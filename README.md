# Streaming with Beam
Project for handling streaming data source with Apache Beam and any distributed processing back-end.

## Prerequisites
* Having created an account on the [Google Cloud Platform](https://cloud.google.com/).
* Having installed [Google Cloud SDK](https://cloud.google.com/sdk/).

## Use
The following command runs Wordcount (inspired by [this example](https://beam.apache.org/get-started/wordcount-example/)) with an entry from Pubsub messages:
```
mvn compile exec:java -Dexec.mainClass=fr.sfeir.beam.WordCountPubsub \
-Dexec.args="--topic=<topicPath> --output=counts" -Pdirect-runner
```
where `topicPath` has the following structure: `projects/<project_id>/topics/<topic_name>`

The previous command uses the direct runner. See [Apache Beam Wordcount example](https://beam.apache.org/get-started/wordcount-example/) to see how to use other runners.
Note that you can use the `run-direct.sh` script file.

The script file `publish-messages.sh` publishes messages from the chosen topic.
