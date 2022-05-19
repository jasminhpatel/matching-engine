#!/bin/bash

instance="me02"
config="secondary"

echo "[match-engine] $instance"
cd /mnt/match-engine

echo "stopping"
./scripts/mectrl.sh -c config/solfini/$config/config.properties -i $instance --shutdown
sleep 10

for pid in `ps -ef | grep java | grep -v grep | grep MatchEngineStarter | awk '{print $2}'`; do
	echo "killing pid $pid"
	kill -9 $pid
done

echo "done"

