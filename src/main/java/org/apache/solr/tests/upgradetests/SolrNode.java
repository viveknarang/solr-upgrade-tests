package org.apache.solr.tests.upgradetests;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;

public class SolrNode {

	final static Logger logger = Logger.getLogger(SolrNode.class);
	public static final String URL_BASE = "http://archive.apache.org/dist/lucene/solr/";

	private String nodeDirectory;
	private String port;
	private String version;
	private static String solrCommand;
	private String zooKeeperIp;
	private String zooKeeperPort;

	static {

		solrCommand = System.getProperty("os.name") != null && System.getProperty("os.name").startsWith("Windows")
				? "bin" + File.separator + "solr.cmd" : "bin" + File.separator + "solr";

	}

	private String gitDirectoryPath = SolrRollingUpgradeTests.TEMP_DIR + "git-repository";

	public SolrNode(String version, String zooKeeperIp, String zooKeeperPort) throws IOException, GitAPIException {
		super();
		this.version = version;
		this.zooKeeperIp = zooKeeperIp;
		this.zooKeeperPort = zooKeeperPort;
		this.install();
	}

	private void install() throws IOException, GitAPIException {

		Util.postMessage("** Installing Solr Node ...", MessageType.ACTION, true);

		this.nodeDirectory = SolrRollingUpgradeTests.BASE_DIR + UUID.randomUUID().toString() + File.separator;
		this.port = String.valueOf(getFreePort());

		try {

			Util.postMessage("** Checking if SOLR node directory exists ...", MessageType.ACTION, true);
			File node = new File(nodeDirectory);

			if (!node.exists()) {

				Util.postMessage("Node directory does not exist, creating it ...", MessageType.ACTION, true);
				node.mkdir();
				Util.postMessage("Directory Created: " + nodeDirectory, MessageType.RESULT_SUCCESS, true);

			}

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		}

		File release = new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + version + ".zip");
		if (!release.exists()) {
			if (Character.isDigit(version.charAt(0)) && version.length() == 5) { // must be a release version, e.g. "6.4.0"
				download(version);
			} else { // assuming this is a git branch/commit
				checkoutCommitAndBuild(version);
			}
		}

		File uzrelease = new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + version);
		if (!uzrelease.exists()) {
			Util.extract(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + version + ".zip", SolrRollingUpgradeTests.TEMP_DIR + "solr-"+version);
			
			for (File file: new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-"+version+"/solr-7.0.0-SNAPSHOT").listFiles()) {
				String src = file.getAbsolutePath();
				String dest = SolrRollingUpgradeTests.TEMP_DIR + "solr-"+version;
				System.out.println("Moving "+src+ " to "+dest);
				//Files.move(Paths.get(src), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);
				if (file.isDirectory()) {
					FileUtils.moveDirectoryToDirectory(file, new File(dest), true);
				} else {
					FileUtils.moveFileToDirectory(file, new File(dest), true);
				}
			}
		}

		File node = new File(nodeDirectory + "solr-" + version);
		node.mkdir();
		FileUtils.copyDirectory(new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + version), node);

	}

	void download(String version) {
		String fileName = null;
		URL link = null;
		InputStream in = null;
		FileOutputStream fos = null;

		try {

			fileName = "solr-" + version + ".zip";
			String url = URL_BASE + version + File.separator + fileName;
			Util.postMessage("** Attempting to download release ..." + " " + version + " from " + url,
					MessageType.ACTION, true);
			link = new URL(url);

			in = new BufferedInputStream(link.openStream());
			fos = new FileOutputStream(SolrRollingUpgradeTests.TEMP_DIR + fileName);
			byte[] buf = new byte[1024 * 1024]; // 1mb blocks
			int n = 0;
			long size = 0;
			while (-1 != (n = in.read(buf))) {
				size += n;
				Util.postMessageOnLine("\r" + size + " ");
				fos.write(buf, 0, n);
			}
			fos.close();
			in.close();

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		}
	}

	void checkoutCommitAndBuild (String commit) throws IOException, GitAPIException {
		Util.postMessage("** Checking out Solr: "+commit+" ...", MessageType.ACTION, true);

		File gitDirectory = new File(gitDirectoryPath);

		Git repository;

		if (gitDirectory.exists()) {
			repository = Git.open(gitDirectory);

			repository.checkout()
			.setName(commit)
			.call();

		} else {
			repository = Git.cloneRepository()
					.setURI("https://github.com/apache/lucene-solr")
					.setDirectory(gitDirectory)
					.call();
			repository.checkout()
			.setName(commit)
			.call();
		}

		String packageFilename = gitDirectoryPath + "/solr/package/solr-7.0.0-SNAPSHOT.zip";
		String tarballLocation = SolrRollingUpgradeTests.TEMP_DIR+"solr-"+commit+".zip";

		if (new File(tarballLocation).exists() == false) {
			if (new File(packageFilename).exists() == false) {
				Util.postMessage("** There were new changes, need to rebuild ...", MessageType.ACTION, true);
				Util.execute("ant ivy-bootstrap", gitDirectoryPath);
				//Util.execute("ant compile", gitDirectoryPath);
				Util.execute("ant package", gitDirectoryPath + File.separator + "solr");
			}

			if (new File(packageFilename).exists()) {
				System.out.println("Trying to copy: "+packageFilename + " to "+tarballLocation);
				Files.copy(Paths.get(packageFilename), Paths.get(tarballLocation));
				System.out.println("File copied!");
			} else {
				throw new IOException("Couldn't build the package"); // nocommit fix, better exception
			}
		}

		Util.postMessage("** Do we have packageFilename? "+(new File(tarballLocation).exists()? "yes": "no")+" ...", MessageType.ACTION, true);
	}

	@SuppressWarnings("finally")
	public int start() {

		Util.postMessage("** Starting Solr Node ...", MessageType.ACTION, true);

		Runtime rt = Runtime.getRuntime();
		Process proc = null;
		StreamGobbler errorGobbler = null;
		StreamGobbler outputGobbler = null;

		try {

			new File(nodeDirectory + "solr-" + version + File.separator + solrCommand).setExecutable(true);
			proc = rt.exec(nodeDirectory + "solr-" + version + File.separator + solrCommand + " start "
					+ "-p " + port
					+ " -m 4g"
					+ " -z " + zooKeeperIp + ":" + zooKeeperPort);

			errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
			outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");

			errorGobbler.start();
			outputGobbler.start();
			proc.waitFor();
			return proc.exitValue();

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
			return -1;

		} finally {

			return proc.exitValue();

		}

	}

	void checkoutBranchAndBuild (String branch) throws IOException, GitAPIException {
		Util.postMessage("** Checking out Solr ...", MessageType.ACTION, true);

		File gitDirectory = new File(gitDirectoryPath);

		Git repository;

		boolean newCode = false;
		if (gitDirectory.exists()) {
			repository = Git.open(gitDirectory);

			boolean branchExists = false;

			if (repository.getRepository().getBranch().equals(branch)) {
				branchExists = true;
			} else {
				for (Ref ref: repository.branchList().call()) {
					if (ref.getName().equals("refs/heads/" + branch)) {
						branchExists = true;
						break;
					}
				}
				newCode = true; // we need to switch branches
			}

			if (branchExists) {
				Util.postMessage("** git branch already exists, switching to it ...", MessageType.ACTION, true);
				repository.checkout()
				.setName(branch)
				.call();

				Util.postMessage("** git pull ...", MessageType.ACTION, true);
				PullResult pullResult = repository.pull().call();
				if (pullResult.getFetchResult().getTrackingRefUpdates().isEmpty() == false) {
					newCode = true;
				}
			} else {
				newCode = true; // branch didn't exist before
				Util.postMessage("** git branch didn't exist before, checking out ...", MessageType.ACTION, true);
				repository.checkout()
				.setCreateBranch(true)
				.setName(branch)
				.setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
				.setStartPoint("origin/" + branch)
				.call();

				Util.postMessage("** git pull ...", MessageType.ACTION, true);
				PullResult pullResult = repository.pull().call();

			}
		} else {
			repository = Git.cloneRepository()
					.setURI("https://github.com/apache/lucene-solr")
					.setDirectory(gitDirectory)
					.setBranch(branch)
					.call();
			newCode = true;
		}

		String packageFilename = gitDirectoryPath + "/solr/package/solr-7.0.0-SNAPSHOT.tgz";
		if (newCode || new File(packageFilename).exists() == false) {
			Util.postMessage("** There were new changes, need to rebuild ...", MessageType.ACTION, true);
			Util.execute("ant ivy-bootstrap", gitDirectoryPath);
			//Util.execute("ant compile", gitDirectoryPath);
			Util.execute("ant package", gitDirectoryPath + File.separator + "solr");
		}

		Util.postMessage("** Do we have packageFilename? "+(new File(packageFilename).exists()? "yes": "no")+" ...", MessageType.ACTION, true);
	}

	@SuppressWarnings("finally")
	public int stop() {

		Util.postMessage("** Stopping Solr Node ...", MessageType.ACTION, true);
		return Util.execute(solrCommand + " stop -p " + port
				+ " -z " + zooKeeperIp + ":" + zooKeeperPort, nodeDirectory + "solr-" + version);
	}

	public int createCollection(String collectionName, String shards, String replicationFactor) throws IOException, InterruptedException {
		return createCollection(collectionName, null, shards, replicationFactor);
	}

	public int createCollection(String collectionName, String configName, String shards, String replicationFactor)
			throws IOException, InterruptedException {

		Util.postMessage("** Creating collection, configuring shards and replication factor ... ", MessageType.ACTION,
				true);
		
		if (configName != null) {
			return Util.execute(solrCommand + " create_collection "
					+ "-c " + collectionName
					+ " -shards " + shards
					+ " -n " + configName 
					+ " -replicationFactor " + replicationFactor, nodeDirectory + "solr-" + version);
		} else {
			return Util.execute(solrCommand + " create_collection -c "
					+ collectionName + " -shards " + shards + " -replicationFactor " + replicationFactor, nodeDirectory + "solr-" + version);
		}

	}

	public void upgrade(String toVersion) throws IOException, InterruptedException {


		File release = new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + toVersion + ".zip");
		if (!release.exists()) {

			String fileName = null;
			URL link = null;
			InputStream in = null;
			FileOutputStream fos = null;

			try {

				fileName = "solr-" + toVersion + ".zip";
				String url = URL_BASE + toVersion + File.separator + fileName;
				Util.postMessage("** Attempting to download release ..." + " " + toVersion + " from " + url,
						MessageType.ACTION, true);
				link = new URL(url);

				in = new BufferedInputStream(link.openStream());
				fos = new FileOutputStream(SolrRollingUpgradeTests.TEMP_DIR + fileName);
				byte[] buf = new byte[1024 * 1024]; // 1mb blocks
				int n = 0;
				long size = 0;
				while (-1 != (n = in.read(buf))) {
					size += n;
					Util.postMessageOnLine("\r" + size + " ");
					fos.write(buf, 0, n);
				}
				fos.close();
				in.close();

			} catch (Exception e) {

				Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

			}
		}

		File uzrelease = new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + toVersion);
		if (!uzrelease.exists()) {

			ZipInputStream zipIn = null;

			try {

				Util.postMessage("** Attempting to unzip the downloaded release ...", MessageType.ACTION, true);
				zipIn = new ZipInputStream(
						new FileInputStream(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + toVersion + ".zip"));
				ZipEntry entry = zipIn.getNextEntry();
				while (entry != null) {
					String filePath = SolrRollingUpgradeTests.TEMP_DIR + File.separator + entry.getName();
					if (!entry.isDirectory()) {
						Util.postMessage("Unzipping to : " + SolrRollingUpgradeTests.TEMP_DIR + " : " + entry.getName(),
								MessageType.ACTION, true);
						Util.extractFile(zipIn, filePath);
					} else {
						File dirx = new File(filePath);
						dirx.mkdir();
					}
					zipIn.closeEntry();
					entry = zipIn.getNextEntry();
				}
				zipIn.close();

			} catch (Exception e) {

				Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

			}

		} 

		Thread.sleep(1000);

		this.stop();

		Util.postMessage("** Attempting upgrade on the node by replacing lib folder ..." + "From: " + version + " To: "
				+ toVersion, MessageType.ACTION, true);
		try {
			String upgradeLocations[] = {
					File.separator + "server" + File.separator + "lib",
					File.separator + "server" + File.separator + "solr-webapp" + File.separator + "webapp"
							+ File.separator + "WEB-INF" + File.separator + "lib"};
			for (String localPath: upgradeLocations) {
				File src = new File(SolrRollingUpgradeTests.TEMP_DIR + "solr-" + toVersion + localPath);
				File dest = new File(nodeDirectory + "solr-" + version + localPath);

				FileUtils.cleanDirectory(dest);
				FileUtils.copyDirectory(src, dest);
			}
			Util.postMessage("Upgrade process complete ... ", MessageType.RESULT_SUCCESS, true);
		} catch (Exception e) {
			Util.postMessage("Upgrade failed due to some reason ...", MessageType.RESULT_ERRROR, true);
			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		}

		this.start();

	}

	private static int getFreePort() {

		int port = ThreadLocalRandom.current().nextInt(10000, 60000);
		Util.postMessage("Looking for a free port ... Checking availability of port number: " + port,
				MessageType.ACTION, true);
		ServerSocket serverSocket = null;
		DatagramSocket datagramSocket = null;
		try {
			serverSocket = new ServerSocket(port);
			serverSocket.setReuseAddress(true);
			datagramSocket = new DatagramSocket(port);
			datagramSocket.setReuseAddress(true);
			Util.postMessage("Port " + port + " is free to use. Using this port !!", MessageType.RESULT_SUCCESS, true);
			return port;
		} catch (IOException e) {
		} finally {
			if (datagramSocket != null) {
				datagramSocket.close();
			}

			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
				}
			}
		}

		Util.postMessage("Port " + port + " looks occupied trying another port number ... ", MessageType.RESULT_ERRROR,
				true);
		return getFreePort();
	}

	@SuppressWarnings("finally")
	public int clean() {

		Util.postMessage("Deleting node ... ", MessageType.ACTION, true);
		Runtime rt = Runtime.getRuntime();
		Process proc = null;
		StreamGobbler errorGobbler = null;
		StreamGobbler outputGobbler = null;

		try {

			proc = rt.exec("rm -r -f " + nodeDirectory);

			errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
			outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");

			errorGobbler.start();
			outputGobbler.start();
			proc.waitFor();
			return proc.exitValue();

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		} finally {

			return proc.exitValue();

		}

	}

	public String getNodeDirectory() {
		return nodeDirectory;
	}

	public String getBaseUrl() {
		return "http://localhost:"+port+"/solr";
	}
}
