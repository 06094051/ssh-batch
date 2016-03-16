package com.github.stuxuhai.sshbatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.stuxuhai.sshbatch.util.ConfigUtil;

public class CliDriver {

	private static final Logger LOGGER = LogManager.getLogger();

	public static void main(String[] args) throws IOException {
		Options opt = new Options();
		opt.addOption("s", true, "servers config file path");
		opt.addOption("f", true, "shell command file path");
		opt.addOption("o", true, "ouptut log dir");

		HelpFormatter formatter = new HelpFormatter();
		CommandLineParser parser = new PosixParser();
		CommandLine cl = null;
		try {
			cl = parser.parse(opt, args);
		} catch (ParseException e) {
			formatter.printHelp("java -jar xx.jar -s xx -f xx -l xx", "", opt, "");
			System.exit(-1);
		}

		if (cl.hasOption("s") && cl.hasOption("f") && cl.hasOption("o")) {
			File sshInfoFile = new File(cl.getOptionValue("s"));
			File commandsFile = new File(cl.getOptionValue("f"));
			File logDir = new File(cl.getOptionValue("o"));

			Map<String, SSHInfo> sshInfoMap = ConfigUtil.parseSSHInfo(sshInfoFile);
			List<String> cmdList = ConfigUtil.readCommands(commandsFile);
			if (logDir.exists() || logDir.mkdirs()) {
				int serversNum = sshInfoMap.size();
				LOGGER.info("Total: " + serversNum);

				int poolSize = Math.min(serversNum, 100);
				ExecutorService pool = Executors.newFixedThreadPool(poolSize);
				for (Entry<String, SSHInfo> entry : sshInfoMap.entrySet()) {
					OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(logDir.getAbsolutePath() + "/" + entry.getKey() + ".log"),
							"UTF-8");
					SSHClient sshClient = new SSHClient(entry.getValue(), cmdList, out);
					LOGGER.info("SSH => " + entry.getKey());
					pool.execute(sshClient);
				}

				pool.shutdown();
			}
		} else {
			formatter.printHelp("java -jar xx.jar -s xx -f xx -l xx", "", opt, "");
			System.exit(-1);
		}
	}
}
