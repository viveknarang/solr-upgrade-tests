# Solr Cloud Rolling Upgrade Tests
Introduction
------------

The purpose of this program is to test rolling upgrades for Solr Cloud. This is a standalone system where the program takes care of zookeeper and solr releases and manages the process end-to-end.

Current Test Results
--------------------

                    Upgrade from:   5.3.0   to  5.4.1           FAILED
                    Upgrade from:   5.4.0   to  5.4.1           PASSED
                    Upgrade from:   5.3.0   to  5.3.1           PASSED
                    Upgrade from:   5.3.0   to  5.4.0           FAILED
                    Upgrade from:   5.3.1   to  5.4.1           FAILED
                    Upgrade from:   5.3.1   to  5.4.0           FAILED
                    Upgrade from:   5.2.1   to  5.3.0           PASSED
                    Upgrade from:   5.2.1   to  5.3.1           PASSED
                    Upgrade from:   5.3.1   to  5.3.2           PASSED
                    Upgrade from:   5.3.2   to  5.4.0           FAILED
                    Upgrade from:   5.3.2   to  5.4.1           FAILED


To Run
------
    
Use the following command to run this program on server

                    mvn clean compile assembly:single
                    java -jar target/org.apache.solr.tests.upgradetests-0.0.1-SNAPSHOT-jar-with-dependencies.jar -v1 5.3.0 -v2 5.4.1 -Nodes 3 -Shards 2 -Replicas 3


Program parameters
------------------

                -v1           {Version One}                   Example 5.4.0
                -v2           {Version Two}                   Example 5.4.1
                -Nodes        {Number of nodes}               Example 3
                -Shards       {Number of shards}              Example 2
                -Replicas     {Number of Replicas}            Example 3

Steps
-----

Following is the summary of the steps that the program follows to test the rolling upgrade
    
                Initial steps include, creating local directories, locating and registering ports to use.
                Looking locally for zookeeper, if not present dowload, install and start zookeeper on port 2181
                Looking locally for Solr releases, if not present download it and copy the release on each node folder.
                Start Solr nodes 
                Create a Test collection
                Insert a set of 1000 documents
                Stop each node one by one, upgrade each node by replacing lib folder on server and then start each node
                upon start of the node check if all the documents are present and that the documents are intact. 
                When all the documents are present and the nodes are normal the program identifies the test as successful
                Upon failure of any node or documents count or state abnormal the program declares the test as failed.
                Final steps include shutting down the nodes and cleaning zookeeper data.


Todo
----

                Running against unreleased (master/branch_5x)
                Making it easier to add more tests to this framework
                Cleaning of current code.


Contributing to this project
----------------------------

Please checkout the code from the repository

                https://github.com/viveknarang/org.apache.solr.tests.upgradetests/
    
Import the project on eclise and do the following 

                mvn eclipse:eclipse
                mvn clean compile assembly:single


Contributions
-------------

                Vivek Narang
                Ishan Chattopadhyaya
                
    

  
