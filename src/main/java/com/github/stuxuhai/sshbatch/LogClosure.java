package com.github.stuxuhai.sshbatch;

import java.io.OutputStreamWriter;

import expect4j.Closure;
import expect4j.ExpectState;

public class LogClosure implements Closure {

	private OutputStreamWriter osw;

	public LogClosure(OutputStreamWriter osw) {
		this.osw = osw;
	}

	public void run(ExpectState state) throws Exception {
		osw.write(state.getBuffer().replaceAll("\033\\[(\\d+;)?(\\d+;)?(\\d+)?m", "")); // 去除shell背景颜色
		// state.exp_continue();
	}

}
