source config.sh
mvn compile exec:java -Dexec.mainClass=fr.sfeir.beam.WordCountPubsub -Dexec.args="--topic=$topicPath --output=counts" -Pdirect-runner