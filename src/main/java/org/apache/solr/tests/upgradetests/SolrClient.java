package org.apache.solr.tests.upgradetests;

import java.io.IOException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

public class SolrClient {

	public int testDocumentsCount = 1000;
	private String zookeeperIp;
	private String zookeeperPort;

	public SolrClient(int testDocumentsCount, String zookeeperIp, String zookeeperPort) {
		super();
		this.testDocumentsCount = testDocumentsCount;
		this.zookeeperIp = zookeeperIp;
		this.zookeeperPort = zookeeperPort;
	}

	public void postData(String collectionName) throws IOException, InterruptedException, SolrServerException {

		Util.postMessage("** Posting data to the node ... ", MessageType.ACTION, true);
		CloudSolrClient solr = new CloudSolrClient(zookeeperIp + ":" + zookeeperPort);
		try {
			
			solr.connect();
			solr.setDefaultCollection(collectionName);
			SolrInputDocument document;

			for (int i = 1; i <= testDocumentsCount; i++) {

				document = new SolrInputDocument();
				document.setField("EMP_ID", "EMP_ID@" + i);
				document.setField("TITLE", "TITLE@" + i);
				solr.add(collectionName, document);
				if (i % 10 == 0) {
					Util.postMessageOnLine("|");
				}
			}
			Util.postMessage("", MessageType.GENERAL, false);
			Util.postMessage("Added data into the cluster ...", MessageType.RESULT_SUCCESS, true);
			solr.commit();
			solr.close();

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		} finally {

			solr.close();

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

}
