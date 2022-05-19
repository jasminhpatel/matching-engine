#!/bin/bash

echo "[shutdown]"
echo "stopping: api-server api01"
ssh td@solfini.com "sudo /mnt/api-server/stop.sh"
echo "stopping: api-server api02"
ssh td@167.86.103.84 "sudo /mnt/api-server/stop.sh"
echo "stopping: match-engine me01"
/mnt/match-engine/stop.sh
echo "stopping: match-engine me02"
ssh td@167.86.103.84 "sudo /mnt/match-engine/stop.sh"
echo "stopping: market-maker"
/mnt/market-maker/stop.sh
echo "[shutdown] done"
echo

if [ $1 == "--clean" ]; then
	echo "[cleanup]"
	echo "cleaning: kafka queues"
	/mnt/kafka/bin/kafka-topics.sh --zookeeper localhost --delete --topic api0001
	/mnt/kafka/bin/kafka-topics.sh --zookeeper localhost --delete --topic me0001
	echo "cleaning: data store"
	psql -h solfini.com -d trader -U trader2 -c "truncate table execution_report;"
	psql -h solfini.com -d trader -U trader2 -c "truncate table position_report;"
	psql -h solfini.com -d trader -U trader2 -c "truncate table position_report_balance;"
	psql -h solfini.com -d trader -U trader2 -c "truncate table user_stat_log;"
	psql -h solfini.com -d trader -U trader2 -c "truncate table user_stat_fee_log;"
	sleep 10
	echo "[cleanup] done"
	echo
fi

echo "[start]"
echo "start: match-engine me01"
/mnt/match-engine/start.sh --warm-start
sleep 30
echo "snapshot: creating new snapshot from me01"
last=`ls -tr /mnt/data/snap/ | tail -1`
/mnt/match-engine/scripts/mectrl.sh -c config/solfini/primary/config.properties -i me01 --snapshot
sleep 10
snapshot=$last
while [ $last == $snapshot ]; do
	sleep 1
	snapshot=`ls -tr /mnt/data/snap/ | tail -1`
done
while [ ! -e /mnt/data/snap/$snapshot/done ]; do
	sleep 1
done
echo "snapshot: pushing new snapshot"
/mnt/data/snap/snap-push.sh $snapshot
echo "start: match-engine me02"
ssh td@167.86.103.84 "sudo /mnt/match-engine/start.sh --snapshot $snapshot"
sleep 10
echo "start: api-serveri api01"
ssh td@solfini.com "sudo /mnt/api-server/start.sh --snapshot $snapshot"
echo "start: api-serveri api02"
ssh td@167.86.103.84 "sudo /mnt/api-server/start.sh --snapshot $snapshot"
sleep 30
echo "start: market-maker"
/mnt/market-maker/start.sh
echo "[start] done"

