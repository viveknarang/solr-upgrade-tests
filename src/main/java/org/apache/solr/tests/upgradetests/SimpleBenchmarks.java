package org.apache.solr.tests.upgradetests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SimpleBenchmarks {
	private static String WORK_DIRECTORY = System.getProperty("user.dir");
	private static String DNAME = "SolrUpdateTests";
	public static String BASE_DIR = WORK_DIRECTORY + File.separator + DNAME + File.separator;
	public static String TEMP_DIR = BASE_DIR + "temp" + File.separator;

	public static void main(String args[]) throws Exception {

		SimpleBenchmarks s = new SimpleBenchmarks();
		s.init();
		s.test(args);

	}

	public void init() {

		try {

			File baseDir = new File(BASE_DIR);
			Util.postMessage("** Checking if base directory exists ...", MessageType.ACTION, true);
			if (!baseDir.exists()) {
				Util.postMessage("Base directory does not exist, creating one ...", MessageType.ACTION, true);
				baseDir.mkdir();
			}

			Util.postMessage("** Checking if temp directory exists ...", MessageType.ACTION, true);
			File tempDir = new File(TEMP_DIR);
			if (!tempDir.exists()) {
				Util.postMessage("Temp directory does not exist Creating Temp directory ...", MessageType.ACTION, true);
				tempDir.mkdir();
			}

		} catch (Exception e) {
			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
		}

	}

	private void cleanup(List<SolrNode> nodes, Zookeeper zookeeper, boolean removeFiles) throws IOException, InterruptedException {
		for (SolrNode cnode : nodes) {
			cnode.stop();
			if (removeFiles) {
				cnode.clean();
			}
		}
		zookeeper.stop();
		Thread.sleep(3000);
		zookeeper.clean();
	}

	public void test(String args[]) throws Exception {

		boolean removeFilesAfterCleanup = false;
		
		Map<String, String> argM = new HashMap<String, String>();

		for (int i = 0; i < args.length; i += 2) {
			argM.put(args[i], args[i + 1]);
		}

		String versionOne = argM.get("-v");
		String numNodes = argM.get("-Nodes");
		String numShards = argM.get("-Shards");
		String numReplicas = argM.get("-Replicas");

		int nodesCount = Integer.parseInt(numNodes);

		int numDocs = Integer.parseInt(argM.get("-numDocs"));
		int iterations = Integer.parseInt(argM.get("-iterations"));
		int updates = Integer.parseInt(argM.get("-updates"));
		int queueSize = Integer.parseInt(argM.get("-queueSize"));
		int thread = Integer.parseInt(argM.get("-threads"));

		Zookeeper zookeeper = new Zookeeper();
		SolrClient client = new SolrClient(1000, zookeeper.getZookeeperIp(), zookeeper.getZookeeperPort(),
				numDocs, iterations, updates, queueSize, thread);
		zookeeper.start();

		List<SolrNode> nodes = new LinkedList<SolrNode>();

		SolrNode node;
		for (int i = 1; i <= nodesCount ; i++) {
			node = new SolrNode(versionOne, zookeeper.getZookeeperIp(), zookeeper.getZookeeperPort());
			node.start();
			Thread.sleep(1000);
			nodes.add(node);
		}

		try {
			int nodeUpCount = client.getLiveNodes();

			if (nodeUpCount != nodesCount) {
				throw new Exception("Current number of nodes that are up: " + nodeUpCount);	
			}

			Util.postMessage(String.valueOf("Current number of nodes that are up: " + nodeUpCount), MessageType.RESULT_SUCCESS, false);

			String collectionName = "simple";
			client.getCloudSolrClient().uploadConfig(Paths.get("gettingstarted/conf"), "benchmarks");
			nodes.get(0).createCollection(collectionName, "benchmarks", numShards, numReplicas);
			/*client.postData(collectionName);
			boolean pass = client.verifyData(collectionName);*/
			
			client.benchmark(collectionName, nodes);
			boolean pass = true;
			
			if (!pass) {
				throw new Exception("Data verification failed");
			} else {
				Util.postMessage("Test passed ...", MessageType.RESULT_SUCCESS, true);
				removeFilesAfterCleanup = true;
			}
		} finally {
			client.getCloudSolrClient().close();
			this.cleanup(nodes, zookeeper, removeFilesAfterCleanup);
		}
	}
}
