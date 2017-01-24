package org.apache.solr.tests.upgradetests;

import java.util.Random;

public class TestStrings {
	public static void main(String args[]) {
		// TODO Auto-generated method stub
		Random r  = new Random();
		System.out.println(Util.getSentence(r, 15000));
	}
}
