package com.github.stuxuhai.sshbatch;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

import com.google.common.base.Throwables;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import expect4j.Closure;
import expect4j.Expect4j;
import expect4j.matches.EofMatch;
import expect4j.matches.Match;
import expect4j.matches.RegExpMatch;
import expect4j.matches.TimeoutMatch;

public class SSHClient extends Thread {

	private SSHInfo sshInfo;
	private List<String> cmds;
	private OutputStreamWriter outputStreamWriter;
	private Closure closure;
	private Match[] regExpMatchs;
	private Session session;

	private static final int DEFAULT_TIME_OUT = 10000;
	private static final String ENTER_CHARACTER = "\r";
	private static final Pattern PATH_PATTERN = Pattern.compile("['\"]?(.*?)['\"]?\\s+['\"]?(.*?)['\"]?");
	private static final String[] LINUX_PROMPT_REGEX = new String[] { "[\\$%#>:] $" };
	private static final Logger LOGGER = LogManager.getLogger();

	public SSHClient(SSHInfo sshInfo, List<String> cmds, OutputStreamWriter outputStreamWriter) {
		this.sshInfo = sshInfo;
		this.cmds = cmds;
		this.outputStreamWriter = outputStreamWriter;
		this.closure = new LogClosure(outputStreamWriter);

		List<Match> matchList = new ArrayList<Match>();
		for (String regExp : LINUX_PROMPT_REGEX) {
			try {
				RegExpMatch mat = new RegExpMatch(regExp, closure);
				matchList.add(mat);
			} catch (MalformedPatternException e) {
				LOGGER.error(Throwables.getStackTraceAsString(e));
			}
		}
		matchList.add(new EofMatch());
		matchList.add(new TimeoutMatch(null));
		regExpMatchs = matchList.toArray(new Match[matchList.size()]);
	}

	private void execute(Expect4j expect4j, String cmd) {
		if (cmd.toUpperCase().startsWith("[PUT]") || cmd.toUpperCase().startsWith("[GET]")) {
			try {
				Matcher m = PATH_PATTERN.matcher(cmd.substring(5).trim());
				if (m.matches()) {
					String src = m.group(1);
					String dest = m.group(2);
					ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
					channel.connect(DEFAULT_TIME_OUT);
					if (cmd.toUpperCase().startsWith("[PUT]")) {
						channel.put(src, dest);
					} else {
						channel.get(src, dest);
					}
					channel.disconnect();
				} else {
					System.out.println("Command Format error: " + cmd);
				}

			} catch (JSchException e) {
				LOGGER.error(Throwables.getStackTraceAsString(e));
			} catch (SftpException e) {
				LOGGER.error(Throwables.getStackTraceAsString(e));
			}
		} else {
			try {
				expect4j.send(cmd + ENTER_CHARACTER);
				expect4j.expect(regExpMatchs);
			} catch (Exception e) {
				LOGGER.error(Throwables.getStackTraceAsString(e));
			}
		}
	}

	private void execute(Expect4j expect4j, List<String> cmds) {
		for (String cmd : cmds) {
			execute(expect4j, cmd);
		}
	}

	@Override
	public void run() {
		JSch jsch = new JSch();
		try {
			session = jsch.getSession(sshInfo.getUsername(), sshInfo.getHost(), sshInfo.getPort());
			session.setPassword(sshInfo.getPassword());

			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");
			session.setConfig(config);
			session.connect(DEFAULT_TIME_OUT);

			ChannelShell channel = (ChannelShell) session.openChannel("shell");
			channel.setPtyType("vt102");
			Expect4j expect4j = new Expect4j(channel.getInputStream(), channel.getOutputStream());
			expect4j.setDefaultTimeout(Expect4j.TIMEOUT_FOREVER);
			channel.connect(DEFAULT_TIME_OUT);
			try {
				expect4j.expect(regExpMatchs);
			} catch (MalformedPatternException e) {
				LOGGER.error(Throwables.getStackTraceAsString(e));
			} catch (Exception e) {
				LOGGER.error(Throwables.getStackTraceAsString(e));
			}

			execute(expect4j, cmds);
			expect4j.close();
			channel.disconnect();
			session.disconnect();
			outputStreamWriter.close();
		} catch (JSchException e) {
			LOGGER.error(Throwables.getStackTraceAsString(e));
		} catch (IOException e) {
			LOGGER.error(Throwables.getStackTraceAsString(e));
		}
	}

}
