package org.apache.solr.tests.upgradetests;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;

enum MessageType {
	PROCESS, ACTION, RESULT_SUCCESS, RESULT_ERRROR, GENERAL
};

public class Util {

	final static Logger logger = Logger.getLogger(Util.class);

	public static void postMessage(String message, MessageType type, boolean printInLog) {

		String ANSI_RESET = "\u001B[0m";
		String ANSI_RED = "\u001B[31m";
		String ANSI_GREEN = "\u001B[32m";
		String ANSI_YELLOW = "\u001B[33m";
		String ANSI_BLUE = "\u001B[34m";
		String ANSI_WHITE = "\u001B[37m";

		if (type.equals(MessageType.ACTION)) {
			System.out.println(ANSI_WHITE + message + ANSI_RESET);
		} else if (type.equals(MessageType.GENERAL)) {
			System.out.println(ANSI_BLUE + message + ANSI_RESET);
		} else if (type.equals(MessageType.PROCESS)) {
			System.out.println(ANSI_YELLOW + message + ANSI_RESET);
		} else if (type.equals(MessageType.RESULT_ERRROR)) {
			System.out.println(ANSI_RED + message + ANSI_RESET);
		} else if (type.equals(MessageType.RESULT_SUCCESS)) {
			System.out.println(ANSI_GREEN + message + ANSI_RESET);
		}

		if (printInLog) {
			logger.info(message);
		}

	}

	public static void postMessageOnLine(String message) {

		System.out.print(message);

	}

	@SuppressWarnings("finally")
	public static int deleteDirectory(String directory) throws IOException, InterruptedException {

		postMessage("Deleting directory: " + directory, MessageType.ACTION, true);
		Runtime rt = Runtime.getRuntime();
		Process proc = null;
		StreamGobbler errorGobbler = null;
		StreamGobbler outputGobbler = null;

		try {

			proc = rt.exec("rm -r -f " + directory);

			errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
			outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");

			errorGobbler.start();
			outputGobbler.start();
			proc.waitFor();
			return proc.exitValue();

		} catch (Exception e) {

			postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
			return -1;

		} finally {

			proc.destroy();
			return proc.exitValue();

		}

	}

	public static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {

		BufferedOutputStream bos = null;
		try {

			bos = new BufferedOutputStream(new FileOutputStream(filePath));
			byte[] bytesIn = new byte[4096];
			int read = 0;
			while ((read = zipIn.read(bytesIn)) != -1) {
				bos.write(bytesIn, 0, read);
			}
			bos.close();

		} catch (Exception e) {

			Util.postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);

		} finally {

			bos.close();

		}
	}
	
	public static File checkoutAndBuild(String gitUrl, String branch) {
		
		gitUrl = "https://github.com/apache/lucene-solr.git";
		String sourceFolder = SolrRollingUpgradeTests.TEMP_DIR + UUID.randomUUID().toString() + File.separator;
		File checkoutFolder = new File(sourceFolder);
		if (!checkoutFolder.exists()) {
			postMessage("Folder " + sourceFolder + " Does not exist creating it ..", MessageType.RESULT_ERRROR, true);
			boolean folder = checkoutFolder.mkdir();
			if (folder) {
				postMessage("Folder " + sourceFolder + " Created ...", MessageType.RESULT_SUCCESS, true);
			} else {
				postMessage("Folder " + sourceFolder + " Creation Failed ...", MessageType.RESULT_ERRROR, true);
			}
		}
		
		{
		
				postMessage("Getting source to build from: " + gitUrl + " and branch: " + branch, MessageType.ACTION, true);
				Runtime rt = Runtime.getRuntime();
				Process proc = null;
				StreamGobbler errorGobbler = null;
				StreamGobbler outputGobbler = null;
		
				try {
		
					proc = rt.exec("git clone " + gitUrl + " " + sourceFolder);
		
					errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
					outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");
		
					errorGobbler.start();
					outputGobbler.start();
					proc.waitFor();
		
				} catch (Exception e) {
		
					postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
		
				} finally {
		
					proc.destroy();
		
				}
		
		}

		
		{
			
			postMessage("Checking out branch: " + branch, MessageType.ACTION, true);
			Runtime rt = Runtime.getRuntime();
			Process proc = null;
			StreamGobbler errorGobbler = null;
			StreamGobbler outputGobbler = null;
	
			try {
	
				proc = rt.exec("git checkout " + branch, null, new File(sourceFolder));
	
				errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
				outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");
	
				errorGobbler.start();
				outputGobbler.start();
				proc.waitFor();
	
			} catch (Exception e) {
	
				postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
	
			} 	
		
		}

		
		{
			
			postMessage("Building package: " + branch, MessageType.ACTION, true);
			Runtime rt = Runtime.getRuntime();
			Process proc = null;
			StreamGobbler errorGobbler = null;
			StreamGobbler outputGobbler = null;
	
			try {
	
				proc = rt.exec("ant package ", null, new File(sourceFolder + "solr" + File.separator));
	
				errorGobbler = new StreamGobbler(proc.getErrorStream(), "ERROR");
				outputGobbler = new StreamGobbler(proc.getInputStream(), "OUTPUT");
	
				errorGobbler.start();
				outputGobbler.start();
				proc.waitFor();
	
			} catch (Exception e) {
	
				postMessage(e.getMessage(), MessageType.RESULT_ERRROR, true);
	
			} 	
		
		}
		
		File returnFile = Util.listFile(sourceFolder + "solr" + File.separator +"package", ".zip");
		
		String currentName = returnFile.getName();
		System.out.println(currentName);
		postMessage("Using file: " + currentName, MessageType.ACTION, true);
		unZip(sourceFolder + "solr"+ File.separator +"package" + File.separator + currentName, SolrRollingUpgradeTests.BASE_DIR + UUID.randomUUID().toString() + File.separator);
		 
		return null;
	}
	
	
	public static File listFile(String folder, String ext) {

		GenericExtFilter filter = new GenericExtFilter(ext);

		File dir = new File(folder);
		
		String[] list = dir.list(filter);
		for (String file : list) {
			String temp = new StringBuffer(folder).append(File.separator)
					.append(file).toString();
			postMessage("File :" + temp, MessageType.RESULT_SUCCESS, true);
			File returnFile = new File(temp);
			return returnFile;
			
		}
		return null;
	}
	
	public static void unZip(String zipPath, String destinationPath) {
		
		ZipInputStream zipIn = null;

		try {

			Util.postMessage("** Attempting to unzip the downloaded release ...", MessageType.ACTION, true);
			zipIn = new ZipInputStream(
					new FileInputStream(zipPath));
			ZipEntry entry = zipIn.getNextEntry();
			while (entry != null) {
				String filePath = destinationPath + entry.getName();
				if (!entry.isDirectory()) {
					Util.postMessage("\r Unzipping to : " + destinationPath + " : " + entry.getName(),
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

}

class GenericExtFilter implements FilenameFilter {

	private String ext;

	public GenericExtFilter(String ext) {
		this.ext = ext;
	}

	public boolean accept(File dir, String name) {
		return (name.endsWith(ext));
	}
}
