package org.apache.solr.tests.upgradetests;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.eclipse.jgit.api.errors.GitAPIException;

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

			org.apache.solr.tests.upgradetests.Util.postMessage("** Checking if temp directory exists ...", MessageType.ACTION, true);
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
		String collectionName = UUID.randomUUID().toString();

		Zookeeper zookeeper = new Zookeeper();
		SolrClient client = new SolrClient(1000, zookeeper.getZookeeperIp(), zookeeper.getZookeeperPort());
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

			nodes.get(0).createCollection(collectionName, numShards, numReplicas);
			client.postData(collectionName);
			boolean pass = client.verifyData(collectionName);
			if (!pass) {
				throw new Exception("Data verification failed");
			} else {
				Util.postMessage("Test passed ...", MessageType.RESULT_SUCCESS, true);
				removeFilesAfterCleanup = true;
			}
		} finally {
			this.cleanup(nodes, zookeeper, removeFilesAfterCleanup);
		}
	}

}
