package org.apache.solr.tests.upgradetests;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class Zookeeper {

	public static String zooCommand;

	static {

		zooCommand = System.getProperty("os.name") != null && System.getProperty("os.name").startsWith("Windows")
				? "bin" + File.separator + "zkServer.cmd " : "bin" + File.separator + "zkServer.sh ";

	}

	public String ZOO_URL_BASE = "http://www.us.apache.org/dist/zookeeper/";
	public String ZOOKEEPER_RELEASE = "3.4.6";
	public String ZOOKEEPER_DIR = SolrRollingUpgradeTests.BASE_DIR + "ZOOKEEPER" + File.separator;
	private String zookeeperIp = "127.0.0.1";
	private String zookeeperPort = "2181";

	Zookeeper() throws IOException {
		super();
		this.install();
	}

	public void install() throws IOException {

		Util.postMessage("** Installing Zookeeper Node ...", MessageType.ACTION, true);

		File base = new File(ZOOKEEPER_DIR);
		if (!base.exists()) {
			base.mkdir();
			base.setExecutable(true);
		}

		File release = new File(SolrRollingUpgradeTests.TEMP_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + ".tar.gz");
		if (!release.exists()) {

			String fileName = null;
			URL link = null;
			InputStream in = null;
			FileOutputStream fos = null;

			try {

				Util.postMessage("** Attempting to download zookeeper release ..." + " : " + ZOOKEEPER_RELEASE,
						MessageType.ACTION, true);
				fileName = "zookeeper-" + ZOOKEEPER_RELEASE + ".tar.gz";
				link = new URL(ZOO_URL_BASE + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + fileName);
				Util.postMessage(ZOO_URL_BASE + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + fileName,
						MessageType.ACTION, true);
				in = new BufferedInputStream(link.openStream());
				fos = new FileOutputStream(SolrRollingUpgradeTests.TEMP_DIR + fileName);
				byte[] buf = new byte[1024 * 1024]; // 1mb blocks
				int n = 0;
				long size = 0;
				while (-1 != (n = in.read(buf))) {
					size += n;
					Util.postMessageOnLine(size + " ");
					fos.write(buf, 0, n);
				}
				fos.close();
				in.close();

			} catch (Exception e) {

				Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

			}
		}

		File urelease = new File(SolrRollingUpgradeTests.TEMP_DIR + "zookeeper-" + ZOOKEEPER_RELEASE);
		if (!urelease.exists()) {

			Util.execute("tar -xf " + SolrRollingUpgradeTests.TEMP_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + ".tar.gz"
					+ " -C " + ZOOKEEPER_DIR);

			Util.execute("mv " + ZOOKEEPER_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + "conf"
					+ File.separator + "zoo_sample.cfg " + ZOOKEEPER_DIR + "zookeeper-" + ZOOKEEPER_RELEASE
					+ File.separator + "conf" + File.separator + "zoo.cfg");

		}
	}

	public int start() {

		Util.postMessage("** Attempting to start zookeeper ...", MessageType.ACTION, true);
		
		new File(ZOOKEEPER_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + zooCommand).setExecutable(true);
		
		return Util.execute(ZOOKEEPER_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + zooCommand + " start");

	}

	public int stop() {

		Util.postMessage("** Attempting to stop zookeeper ...", MessageType.ACTION, true);

		new File(ZOOKEEPER_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + zooCommand).setExecutable(true);

		return Util.execute(ZOOKEEPER_DIR + "zookeeper-" + ZOOKEEPER_RELEASE + File.separator + zooCommand + " stop");

	}

	public int clean() throws IOException, InterruptedException {

		Util.postMessage("Deleting directory for zookeeper data ", MessageType.ACTION, true);

		return Util.execute("rm -r -f /tmp/zookeeper/");	
			
	}

	public String getZookeeperIp() {
		return zookeeperIp;
	}

	public void setZookeeperIp(String zookeeperIp) {
		this.zookeeperIp = zookeeperIp;
	}

	public String getZookeeperPort() {
		return zookeeperPort;
	}

	public void setZookeeperPort(String zookeeperPort) {
		this.zookeeperPort = zookeeperPort;
	}

}