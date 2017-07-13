source config.sh
for i in {1..50}
do
	gcloud alpha pubsub topics publish $topicPath "Published Message $i"
	sleep 1
done
