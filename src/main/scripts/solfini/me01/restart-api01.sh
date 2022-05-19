#!/bin/bash

instance="api01"
host="solfini.com"

echo "[shutdown] $instance"
echo "stopping: api-server"
ssh td@$host "sudo /mnt/api-server/stop.sh"
echo "[shutdown] done"
echo

echo "[start] $instance"
echo "snapshot: creating new snapshot from me01"
last=`ls -tr /mnt/data/snap/ | tail -1`
/mnt/match-engine/scripts/mectrl.sh -c config/solfini/primary/config.properties -i me01 --snapshot
sleep 30
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
echo "start: api-server"
ssh td@$host "sudo /mnt/api-server/start.sh --snapshot $snapshot"
sleep 30
echo "[start] done"

