package org.apache.solr.tests.upgradetests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

import com.google.common.collect.ImmutableMap;

public class SolrClient {

	public int testDocumentsCount = 1000;
	private String zookeeperIp;
	private String zookeeperPort;
	private final CloudSolrClient cloudSolrClient;


	public SolrClient(int testDocumentsCount, String zookeeperIp, String zookeeperPort) {
		super();
		this.testDocumentsCount = testDocumentsCount;
		this.zookeeperIp = zookeeperIp;
		this.zookeeperPort = zookeeperPort;
		
		this.cloudSolrClient = new CloudSolrClient(zookeeperIp + ":" + zookeeperPort);
	}

	public void postData(String collectionName) throws IOException, InterruptedException, SolrServerException {

		Util.postMessage("** Posting data to the node ... ", MessageType.ACTION, true);
		try {
			
			cloudSolrClient.connect();
			cloudSolrClient.setDefaultCollection(collectionName);
			SolrInputDocument document;

			for (int i = 1; i <= testDocumentsCount; i++) {
				document = new SolrInputDocument();
				document.addField("id", i);
				document.setField("EMP_ID", "EMP_ID@" + i);
				document.setField("TITLE", "TITLE@" + i);
				cloudSolrClient.add(collectionName, document);
				if (i % 10 == 0) {
					Util.postMessageOnLine("|");
				}
			}
			Util.postMessage("", MessageType.GENERAL, false);
			Util.postMessage("Added data into the cluster ...", MessageType.RESULT_SUCCESS, true);
			cloudSolrClient.commit();
			cloudSolrClient.close();
		} finally {
			cloudSolrClient.close();
		}

	}

	public void benchmark(String collectionName) throws SolrServerException, IOException, InterruptedException {
		Random r = new Random(0); // fixed seed, so that benchmarks are reproducible easily
		cloudSolrClient.setDefaultCollection(collectionName);

		long start = System.nanoTime();
		// Index 20k docs
		for (int i=1; i<=20000; i++) {
			SolrInputDocument doc = new SolrInputDocument();
			doc.addField("id", i);
			doc.addField("stored_l", r.nextLong());
			doc.addField("inplace_dvo_l", r.nextLong());
			cloudSolrClient.add(doc);
			if (i % 1000 == 0) {
				System.out.println(i + ": "+doc);
			}
		}
		cloudSolrClient.commit();
		long end = System.nanoTime();
		Util.postMessage("Time for adding 20K documents: " + (end-start)/1000000000 + " secs", MessageType.RESULT_SUCCESS, true);

		Map<String, Long> times = new HashMap<String, Long>();
		times.put("stored_l", 0l);
		times.put("inplace_dvo_l", 0l);
		for (int iter=0; iter<50; iter++) {
			for (String field: Arrays.asList("stored_l", "inplace_dvo_l")) {
				start = System.nanoTime();
				for (int i=1; i<=50000; i++) {
					SolrInputDocument doc = new SolrInputDocument();
					int docid = 1 + r.nextInt(20000);
					doc.addField("id", docid);
					doc.addField(field, ImmutableMap.of("set", r.nextInt()));
					cloudSolrClient.add(doc);

					if (i % 5000 == 0) {
						System.out.println(iter+" ("+field+"), "+docid + ": "+doc);
					}
				}
				cloudSolrClient.commit();
				end = System.nanoTime();
				
				times.put(field, times.get(field) + (end-start));
				System.out.println("Iteration "+iter+", field "+field+", took "+ (end-start)/1000000000+ "secs");
				Thread.sleep(2000);
			}
		}
		
		for (String field: Arrays.asList("stored_l", "inplace_dvo_l")) {
			Util.postMessage("Time for "+field+": " + (times.get(field))/1000000000 + " secs", MessageType.RESULT_SUCCESS, true);
		}
	}

	public boolean verifyData(String collectionName) throws IOException, InterruptedException, SolrServerException {

		Util.postMessage("** Getting the data from nodes ... ", MessageType.RESULT_SUCCESS, true);
		CloudSolrClient solr = new CloudSolrClient(zookeeperIp + ":" + zookeeperPort);
		try {

			solr.connect();
			solr.setDefaultCollection(collectionName);
			SolrQuery query = new SolrQuery("*:*");
			query.setRows(10000);
			SolrDocumentList docList = solr.query(query).getResults();

			int count = 0;
			for (SolrDocument document : docList) {
				if (!(document.getFieldValue("TITLE").toString().split("@", 2)[1]
						.equals(document.getFieldValue("EMP_ID").toString().split("@", 2)[1]))) {
					solr.close();
					Util.postMessage("%%%% DATA CORRUPTED, returning false  %%%%", MessageType.RESULT_ERRROR, true);
					return false;
				}
				count++;
				if (count % 10 == 0) {
					Util.postMessageOnLine("|");
				}
			}
			Util.postMessage("", MessageType.GENERAL, false);

			if (count != testDocumentsCount) {
				Util.postMessage("%%%% DATA COUNT MISMATCH, returning false  %%%%", MessageType.RESULT_ERRROR, true);
				solr.close();
				return false;
			}

			solr.close();
			return true;

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
			return false;

		} finally {

			solr.close();

		}

	}

	public void deleteData(String collectionName) throws IOException, InterruptedException, SolrServerException {

		Util.postMessage("** Deleting data from the nodes ... ", MessageType.ACTION, true);
		CloudSolrClient solr = new CloudSolrClient(zookeeperIp + ":" + zookeeperPort);
		try {
			solr.connect();
			solr.setDefaultCollection(collectionName);
			solr.deleteByQuery("*:*");
			solr.close();

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		} finally {

			solr.close();

		}
	}

	public int getLiveNodes() throws IOException {

		Util.postMessage("** Attempting to get live nodes on the cluster ... ", MessageType.ACTION, true);
		CloudSolrClient solr = new CloudSolrClient(zookeeperIp + ":" + zookeeperPort);
		try {
			solr.connect();
			int liveNodes = solr.getZkStateReader().getClusterState().getLiveNodes().size();
			solr.close();
			return liveNodes;

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
			return -1;

		} finally {

			solr.close();

		}
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

	public CloudSolrClient getCloudSolrClient() {
		return cloudSolrClient;
	}
}
