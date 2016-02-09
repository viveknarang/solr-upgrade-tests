package org.apache.solr.tests.upgradetests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamGobbler extends Thread {

	InputStream is;
	String type;

	StreamGobbler(InputStream is, String type) {
			this.is = is;
			this.type = type;
	}

	public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					Util.postMessage("  SubProcess: " + type + " >> " + line, MessageType.PROCESS, true);
	
			} catch (IOException ioe) {
				Util.postMessage(ioe.getMessage(), MessageType.RESULT_ERRROR, true);
			}
	}

}
