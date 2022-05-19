#!/bin/bash


echo "deploying matching engine"
cp /home/ubuntu/match-engine-0.0.1-SNAPSHOT*.jar ./lib
tar xvfz /home/ubuntu/match-engine-0.0.1-SNAPSHOT-deployment.tgz

DIR="$(cd "$(dirname "$0")" && pwd)"
rm output.log
rm -r logs/*
touch output.log
touch logs/output.log
# $DIR/mestart.sh -c $DIR/../config/staging/primary/config.properties -d PUBLISH_MARKET_DATA=false -d LOGGING_DISABLE_ON_LATENCY_HIKE=true -d INSTANCE_ID=$1 -d CONTROLLER_TYPE=zookeeper -d CONTROLLER_ZOOKEEPER_PRIORITY=$2 -d KAFKA.CONSUMER.group.id=$1 $3
$DIR/mestart.sh -c $DIR/../config/staging/$1/config.properties -d PUBLISH_MARKET_DATA=false $*
tail -F logs/output.log -F output.log | grep -e ZooKeeperController -e SnapUtil -e 'pause' -e 'Moving input queue cursor to the end' -e 'Failover' --color 
