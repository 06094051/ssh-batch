package com.github.stuxuhai.sshbatch.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.stuxuhai.sshbatch.SSHInfo;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class ConfigUtil {

	private static final Pattern SSH_INFO_PATTERN = Pattern.compile("(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)");
	private static final Pattern VARS_PATTERN = Pattern.compile("\\$\\w+");

	public static Map<String, SSHInfo> parseSSHInfo(File file) throws IOException {
		Map<String, SSHInfo> map = new HashMap<String, SSHInfo>();
		BufferedReader br = Files.newReader(file, Charsets.UTF_8);
		String line;
		int lineNum = 0;
		while ((line = br.readLine()) != null) {
			lineNum++;
			if (!line.trim().equals("") && !line.startsWith("#")) {
				Matcher m = SSH_INFO_PATTERN.matcher(line);
				if (m.find()) {
					String host = m.group(1);
					int port = Integer.parseInt(m.group(2));
					String username = m.group(3);
					String password = m.group(4);
					SSHInfo sshInfo = new SSHInfo(host, port, username, password);
					map.put(host, sshInfo);
				} else {
					br.close();
					throw new IllegalArgumentException(String.format(
							"line %d: Can not match SSH Config. Each line format is 'host[TAB]port[TAB]username[TAB]password': %s", lineNum, line));
				}
			}
		}

		br.close();
		return map;
	}

	public static List<String> readCommands(File file) throws IOException {
		Map<String, String> vars = new HashMap<String, String>();
		List<String> commands = new ArrayList<String>();
		BufferedReader br = Files.newReader(file, Charsets.UTF_8);
		String line;

		SortedSet<String> set = new TreeSet<String>(new Comparator<String>() {

			@Override
			public int compare(String a, String b) {
				return b.compareTo(a);
			}

		});

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (!line.isEmpty() && !line.startsWith("#")) {
				if (line.startsWith("export ")) {
					String[] tokens = line.replaceFirst("export\\s*", "").split("=");
					vars.put(tokens[0], tokens[1]);
				} else if (line.toUpperCase().startsWith("[PUT]") || line.toUpperCase().startsWith("[GET]")) {
					Matcher m = VARS_PATTERN.matcher(line);
					while (m.find()) {
						String key = m.group().replaceFirst("\\$", "");
						set.add(key);
					}

					for (String key : set) {
						if (vars.containsKey(key)) {
							line = line.replace("$" + key, vars.get(key));
						}
					}
				}
				commands.add(line);
			}
		}

		br.close();
		return commands;
	}
}
