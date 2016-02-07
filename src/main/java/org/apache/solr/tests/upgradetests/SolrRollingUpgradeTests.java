package org.apache.solr.tests.upgradetests;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;


public class SolrRollingUpgradeTests {

	private static String WORK_DIRECTORY = System.getProperty("user.dir");
	private static String DNAME = "SOLRUpdateTests";
	public static String BASE_DIR = WORK_DIRECTORY + File.separator + DNAME + File.separator;
	public static String TEMP_DIR = BASE_DIR + "temp" + File.separator;

	public String ARG_VERSION_ONE = "-v1";
	public String ARG_VERSION_TWO = "-v2";
	public String ARG_NUM_SHARDS = "-Shards";
	public String ARG_NUM_REPLICAS = "-Replicas";
	public String ARG_NUM_NODES = "-Nodes";

	public static void main(String args[]) throws IOException, InterruptedException, SolrServerException {

		SolrRollingUpgradeTests s = new SolrRollingUpgradeTests();
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
	
	public void cleanAndExit(int exitValue, List<SolrNode> nodes, Zookeeper zookeeper) throws IOException, InterruptedException {
		
		for (SolrNode cnode : nodes) {

			cnode.stop();
			cnode.clean();
			Thread.sleep(10000);
			
		}

		zookeeper.clean();
		zookeeper.stop();
		System.exit(exitValue);
	}

	public void test(String args[]) throws IOException, InterruptedException, SolrServerException {

		Map<String, String> argM = new HashMap<String, String>();

		for (int i = 0; i < args.length; i += 2) {
			argM.put(args[i], args[i + 1]);
		}

		String versionOne = argM.get(ARG_VERSION_ONE);
		String versionTwo = argM.get(ARG_VERSION_TWO);
		String numNodes = argM.get(ARG_NUM_NODES);
		String numShards = argM.get(ARG_NUM_SHARDS);
		String numReplicas = argM.get(ARG_NUM_REPLICAS);

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
			
			if(i == nodesCount) {
				node.createCollection(collectionName, numShards, numReplicas);
			}

			node = null;		
			
		}
		
		int nodeUpCount = client.getLiveNodes();
		if (nodeUpCount != nodesCount) {
			Util.postMessage(String.valueOf("Current number of nodes that are up: " + nodeUpCount), MessageType.RESULT_ERRROR, false);	
			this.cleanAndExit(-1, nodes, zookeeper);
		} 
		Util.postMessage(String.valueOf("Current number of nodes that are up: " + nodeUpCount), MessageType.GENERAL, false);
		Thread.sleep(30000);
		client.postData(collectionName);
		
		for (SolrNode unode : nodes) {

			unode.stop();
			
			Thread.sleep(30000);

			unode.upgrade(versionTwo);

			Thread.sleep(30000);

			unode.start();
			
			if (!client.verifyData(collectionName)) {
				Util.postMessage("Data Inconsistant ...", MessageType.RESULT_ERRROR, true);
				this.cleanAndExit(-1, nodes, zookeeper);				
			}
			
		}
		
		if (client.getLiveNodes() == nodesCount) {
			Util.postMessage("All nodes are up ...", MessageType.RESULT_SUCCESS, true);
		} else {
			Util.postMessage("All nodes didn't come up ...", MessageType.RESULT_ERRROR, true);
			this.cleanAndExit(-1, nodes, zookeeper);				
		}
		
		if ((client.getLiveNodes() == nodesCount) && (client.verifyData(collectionName))) {
			Util.postMessage("############# TEST PASSED #############", MessageType.RESULT_SUCCESS, true);
		} else {
			Util.postMessage("############# TEST FAILED #############", MessageType.RESULT_ERRROR, true);
		}
		
		this.cleanAndExit(0, nodes, zookeeper);
	}
}