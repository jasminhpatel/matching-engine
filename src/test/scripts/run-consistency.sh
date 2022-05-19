#!/bin/bash


function message() {
	echo -e "\e[1;34m$1\e[0m"
}

function clean_logs(){
    message "XX Cleaning logs"
    rm -rf logs/output.log
    touch logs/output.log

}
function start_match_engine() {
	message "XX Starting Match Engine"
	./scripts/mestart.sh -c $1 --warm-start -d CONTROLLER_TYPE=static
	sleep 5
	while [ 1 ]; do
		ready=`grep "Moving input queue cursor to the end of the queue" logs/output.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
	sleep 5
}

function stop_match_engine() {
    message "XX Stopping Match Engine"
    ./scripts/stop.sh
}

function run_consistency(){
   message "XX Running consistency test"
   ./scripts/consistency-injector.sh -c $1 -s $2 -o $3 -r $4 -sd $5

}

function snapshot() {
	message "XX Writing snapshot"
	./scripts/mectrl.sh -c $1 -i $2 --snapshot
	sleep 5
	while [ 1 ]; do
		ready=`grep "Snapshot completed:" logs/output.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
	snap=`grep "Snapshot completed:" logs/output.log | tail -1 | cut -d'=' -f2 | cut -d',' -f1`
	message "XX Snapshot Id: $snap"
}

function backup_files() {
    message "XX Taking backup"
    epochTime=`date +%s`
    tar -czf feeder/consistency_results_$epochTime.tar.gz pre_test_snap.json post_test_snap.json logs/output.log
    message "XX Backup complete. File Name: consistency_results_$epochTime.tar.gz"
}

function validate() {
    message "XX Verifying"
    ./scripts/snap-to-json.sh -s /efs/snap/$1 -j $2.json --validate
    status=$?
    if [ $status -eq 0 ]; then
        message "XX Verified Successfully"
    else
        message "XX Verification failed"
        backup_files
        stop_match_engine
        error=1;
    fi
}

function run_test_iteration(){
    message "XX Starting Consistency test"
    clean_logs
    start_match_engine $config

    message "\nXX PRE_TEST"
    snapshot $config $instance_id
    validate $snap pre_test_snap
    if [ 0 -eq "$error" ]; then
        run_consistency $config pre_test_snap.json $orders $rate $seed
        message "\nXX POST_TEST"
        snapshot $config $instance_id
        validate $snap post_test_snap
        if [ 0 -eq "$error" ]; then
            stop_match_engine
        fi
    fi
}

error=0;

if [ "$1" == "iterate" ]; then
    if [ 6 -gt "$#" ]; then
        echo "XX Insufficient arguments"
        echo "XX USAGE: iterate <count> <config_file> <instance_id> <orders> <rate> or <config_file> <instance_id> <orders> <rate> <seed>"
        exit 1
    fi
    count=$2
    config=$3
    instance_id=$4
    orders=$5
    rate=$6

    for i in $(seq 1 $count);
        do
            seed=$((1 + RANDOM % 1000000))
            message "\nXX Iteration $i - Seed: $seed"
            run_test_iteration
        done
    exit 0

elif [ 5 -gt "$#" ]; then
    echo "XX Insufficient arguments"
    echo "XX USAGE: iterate <count> <config_file> <instance_id> <orders> <rate> or <config_file> <instance_id> <orders> <rate> <seed>"
    exit 1

elif [ 5 -eq "$#" ]; then
   config=$1
   instance_id=$2
   orders=$3
   rate=$4
   seed=$5

   run_test_iteration
   exit 0
fi

