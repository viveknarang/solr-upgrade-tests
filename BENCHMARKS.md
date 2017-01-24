Running the project
-------------------

mvn clean compile assembly:single

java -cp target/org.apache.solr.tests.upgradetests-0.0.1-SNAPSHOT-jar-with-dependencies.jar:. org.apache.solr.tests.upgradetests.SimpleBenchmarks -v fcf71e34f20ea74f99933b80d5bd43cd487751f1 -Nodes 1 -Shards 1 -Replicas 1 -numDocs 20000 -iterations 10 -updates 5000 -queueSize 5000 -threads 8

