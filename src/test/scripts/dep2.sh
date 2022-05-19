#!/bin/bash


DIR="$(cd "$(dirname "$0")" && pwd)"
rm output.log
rm logs/output.log
touch output.log
touch logs/output.log

cp /home/ubuntu/match-engine-0.0.1-SNAPSHOT*.jar ./lib
tar xvfz /home/ubuntu/match-engine-0.0.1-SNAPSHOT-deployment.tgz

$DIR/mestart.sh -c $DIR/../config/staging/primary/config.properties --warm-start -d PUBLISH_MARKET_DATA=false --debug -d INSTANCE_ID=$1 -d CONTROLLER_ZOOKEEPER_PRIORITY=$2 -d KAFKA.CONSUMER.group.id=$1
tail -F logs/output.log -F output.log | grep -e ZooKeeperController -e SnapUtil -e 'BENCHMARK KafkaPublisher' -e 'BENCHMARK KafkaListener' -e 'BENCHMARK MatchingThread' -e 'pause' -e 'Moving input queue cursor to the end' -e 'BENCHMARK Latency: average' -e 'Failover' -e failover -e 'Invalid sender comp id' -e Controller  -e ERROR --color
