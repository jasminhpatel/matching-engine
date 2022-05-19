#!/bin/bash

function title() {
	echo -e "\e[1;33m>>> TEST: $1 <<<\e[0m"
}

function message() {
	echo -e "\e[1;34m$1\e[0m"
}

function stop_all() {
	message "Stopping all matching engines"
	ps -ef | grep com.solfini.matchengine.MatchEngineStarter | grep -v grep
	for pid in `ps -eo "%p|%a" | grep java | grep com.solfini.matchengine.MatchEngineStarter | grep -v grep | cut -d'|' -f1`; do
		kill -9 $pid
	done

	while [[ ! -z `ps -ef | grep com.solfini.matchengine.MatchEngineStarter | grep -v grep` ]]; do
		sleep 1
	done
	sleep 5
}

function clean_logs() {
	message "Cleaning logs"
	rm -fr logs/me0*.log
}

function clean_cache() {
	message "Clean snapshot cache"
	if [ -d ./snap ]; then
		rm -fr ./snap/*
	else
		mkdir ./snap
	fi
}

function init() {
	snap=0
	uid=1000
	stop_all
	clean_logs
	clean_cache
}

function primary_cold_start() {
	message "Cold starting primary $1"
	./scripts/mestart.sh --profile minimal -c config/multinode/$1/config.properties --cold-start -m primary -d CONTROLLER_TYPE=kafka -d CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY=./snap
	sleep 5
	while [ 1 ]; do
		ready=`grep "Moving input queue cursor to the end of the queue" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function primary_warm_start() {
	message "Warm starting primary $1"
	./scripts/mestart.sh --profile minimal -c config/multinode/$1/config.properties --warm-start -d CONTROLLER_TYPE=kafka -d CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY=./snap
	sleep 5
	while [ 1 ]; do
		ready=`grep "Moving input queue cursor to the end of the queue" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function primary_warm_start_with_replay() {
	message "Warm starting primary $1 with replay"
	./scripts/mestart.sh --profile minimal -c config/multinode/$1/config.properties -d CONTROLLER_TYPE=kafka -d LOAD_FROM_SNAP=$snap -d LOAD_FROM_SNAP_AND_REPLAY=OUTPUT -d CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY=./snap
	sleep 5
	while [ 1 ]; do
		ready=`grep "Moving input queue cursor to the end of the queue" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function secondary_cold_start() {
	message "Cold starting secondary $1"
	./scripts/mestart.sh --profile minimal -c config/multinode/$1/config.properties --cold-start -m secondary -d CONTROLLER_TYPE=kafka -d CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY=./snap
	sleep 5
	while [ 1 ]; do
		ready=`grep "WarmStart: Snapshot loading completed and state recovered" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function secondary_warm_start() {
	message "Warm starting $1 from snapshot $snap"
	./scripts/mestart.sh --profile minimal -c config/multinode/$1/config.properties -m secondary -d CONTROLLER_TYPE=kafka -d LOAD_FROM_SNAP=$snap -d CHRONICLE_ENGINE_SNAP_CACHE_DIRECTORY=./snap
	sleep 5
	while [ 1 ]; do
		ready=`grep "WarmStart: Consuming messages from offset" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function inject() {
	message "Injecting messages"
	./scripts/injector.sh -c config/multinode/me01/config.properties -u 1000 -o 10000 -m 10 -pt 0 -pc 1000000 -r 1000 -uid $uid
	uid=`expr $uid + 1000`
}

function snapshot() {
	message "Writing snapshot from $1"
	./scripts/mectrl.sh -c config/multinode/$1/config.properties -i mn-$1 --snapshot
	sleep 5
	while [ 1 ]; do
		ready=`grep "Snapshot completed:" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
	snap=`grep "Snapshot completed:" logs/$1.log | tail -1 | cut -d'=' -f2 | cut -d',' -f1`
}

function check_state() {
	message "Writing snapshot from all instances"
	./scripts/mectrl.sh -c config/multinode/me01/config.properties -i all --snapshot
	sleep 60

	message "Exporting snapshot from $1"
	while [ 1 ]; do
		ready=`grep "SnapUtil] Snapshot" logs/$1.log | tail -1 | grep "completed"`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
	snap1=`grep "Snapshot completed:" logs/$1.log | tail -1 | cut -d'=' -f2 | cut -d',' -f1`
	./scripts/snap-to-json.sh -s /efs/snap/$snap1 -j $snap1.json --validate
	if [ $? == 0 ]; then
		echo -e "\e[1;32mOK\e[0m"
	else
		echo -e "\e[1;31mFAILED\e[0m"
	fi

	shift

	while [ ! -z $1 ]; do
		message "Exporting snapshot from $1"
		while [ 1 ]; do
			ready=`grep "SnapUtil] Snapshot" logs/$1.log | tail -1 | grep "completed"`
			if [ ! -z "$ready" ]; then
				break
		fi
		sleep 1
		done
		snap2=`grep "Snapshot completed:" logs/$1.log | tail -1 | cut -d'=' -f2 | cut -d',' -f1`
		./scripts/snap-to-json.sh -s /efs/snap/$snap2 -j $snap2.json --validate
		if [ $? == 0 ]; then
			echo -e "\e[1;32mOK\e[0m"
		else
			echo -e "\e[1;31mFAILED\e[0m"
		fi

		message "Comparing snapshots $snap1 and $snap2"
		java -cp lib/match-engine-v2-0.0.1-SNAPSHOT-tests.jar com.solfini.util.snapshot.SnapConverterTest $snap1.json $snap2.json
		if [ $? == 0 ]; then
			echo -e "\e[1;32mOK\e[0m"
		else
			echo -e "\e[1;31mFAILED\e[0m"
		fi
		shift
	done
}

function check_errors() {
	while [ ! -z $1 ]; do
		message "Checking for errors in $1"
		errors=0

		count=`grep SKIP logs/$1.log | grep -v "output message as topic has not been set" | wc -l`
		if [ $count != "0" ]; then
			errors=1
			echo "$count messages skipped"
		fi

		count=`grep "Unexpected sequence number" logs/$1.log | grep -v KafkaInputFixListener | wc -l`
		if [ $count != "0" ]; then
			errors=1
			echo "$count sequence breaks"
		fi

		count=`grep "Exception" logs/$1.log | wc -l`
		if [ $count != "0" ]; then
			errors=1
			echo "$count exceptions"
		fi

		count=`grep "validation error" logs/$1.log | wc -l`
		if [ $count != "0" ]; then
			errors=1
			echo "$count validation errors"
		fi

		shift
		if [ $errors == 0 ]; then
			echo -e "\e[1;32mOK\e[0m"
		else
			echo -e "\e[1;31mFAILED\e[0m"
		fi
	done
}

function compare_snapshots() {
	message "Exporting snapshot $1"
	snap1=$1
	./scripts/snap-to-json.sh -s /efs/snap/$snap1 -j $snap1.json --validate
	if [ $? == 0 ]; then
		echo -e "\e[1;32mOK\e[0m"
	else
		echo -e "\e[1;31mFAILED\e[0m"
	fi
	shift

	while [ ! -z $1 ]; do
		message "Exporting snapshot $1"
		snap2=$1
		./scripts/snap-to-json.sh -s /efs/snap/$snap2 -j $snap2.json --validate
		if [ $? == 0 ]; then
			echo -e "\e[1;32mOK\e[0m"
		else
			echo -e "\e[1;31mFAILED\e[0m"
		fi

		message "Comparing snapshots $snap1 and $snap2"
		java -cp lib/match-engine-v2-0.0.1-SNAPSHOT-tests.jar com.solfini.util.snapshot.SnapConverterTest $snap1.json $snap2.json
		if [ $? == 0 ]; then
			echo -e "\e[1;32mOK\e[0m"
		else
			echo -e "\e[1;31mFAILED\e[0m"
		fi
		shift
	done
}

function shutdown() {
	message "Shutting down $1"
	./scripts/mectrl.sh -c config/multinode/$1/config.properties -i mn-$1 --shutdown
	sleep 5
	while [ 1 ]; do
		ready=`grep "Terminating" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function failover() {
	message "Promoting $1 to primary"
	./scripts/mectrl.sh -c config/multinode/$1/config.properties -i mn-$1 --primary
	sleep 5
	while [ 1 ]; do
		ready=`grep "Control message processed: ModeControlMessage (PreviousMode: SECONDARY, Mode: PRIMARY)" logs/$1.log`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
}

function test_primary_start_cold() {
	title "Start primary in cold start mode"
	init
	primary_cold_start me01
	check_errors me01
}

function test_primary_start_from_snapshot() {
	title "Start primary from snapshot"
	init
	primary_warm_start me01
	snapshot me01
	reference=$snap

	inject
	snapshot me01
	shutdown me01

	primary_warm_start me01
	snapshot me01
	compare_snapshots $reference $snap
	check_errors me01
}

function test_primary_start_from_snapshot_and_replay() {
	title "Start primary from snapshot and replay"
	init
	primary_warm_start me01
	inject
	snapshot me01
	start=$snap
	inject
	snapshot me01
	reference=$snap
	shutdown me01

	snap=$start
	primary_warm_start_with_replay me01
	snapshot me01
	compare_snapshots $reference $snap
	check_errors me01
}

function test_secondary_start_from_primary_snapshot() {
	title "Start secondaries from primary snapshot"
	init
	primary_warm_start me01
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

function test_secondary_start_from_delayed_primary_snapshot() {
	title "Start secondaries from delayed primary snapshot"
	init
	primary_warm_start me01
	inject
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

function test_secondary_start_from_primary_snapshot_and_replay() {
	title "Start secondaries from primary snapshot and replay"
	init
	primary_warm_start me01
	snapshot me01
	inject

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

function test_secondary_start_from_delayed_primary_snapshot_and_replay() {
	title "Start secondaries from delayed primary snapshot and replay"
	init
	primary_warm_start me01
	inject
	snapshot me01
	inject

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

function test_secondary_start_late_join_from_primary_snapshot() {
	title "Late joining of secondaries from primary snapshot"
	init
	primary_warm_start me01
	inject
	snapshot me01

	secondary_warm_start me02
	inject
	secondary_warm_start me03
	inject
	secondary_warm_start me04
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

function test_secondary_start_late_join_from_secondary_snapshot() {
	title "Late joining of secondaries from secondary snapshot"
	init
	primary_warm_start me01
	inject
	snapshot me01

	secondary_warm_start me02
	inject
	snapshot me02

	secondary_warm_start me03
	inject
	secondary_warm_start me04
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

function test_secondary_start_cold() {
	title "Start secondary in cold start mode"
	init
	primary_warm_start me01
	inject

	secondary_cold_start me02
	inject
	check_state me01 me02
	check_errors me01 me02
}

function test_failover() {
	title "Failover"
	init
	primary_warm_start me01
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	inject
	
	shutdown me01
	failover me02
	inject
	check_state me02 me03 me04
}

function test_failover_restart_failed_node_from_old_primary_snapshot() {
	title "Failover with restart of failed node from original primary snapshot"
	init
	primary_warm_start me01
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	inject
	
	shutdown me01
	failover me02
	inject
	secondary_warm_start me01
	inject
	check_state me02 me03 me04 me01
	check_errors me01 me02 me03 me04
}

function test_failover_restart_failed_node_from_new_primary_snapshot() {
	title "Failover with restart of failed node from new primary snapshot"
	init
	primary_warm_start me01
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	inject
	
	shutdown me01
	failover me02
	inject
	snapshot me02
	secondary_warm_start me01
	inject
	check_state me02 me03 me04 me01
	check_errors me01 me02 me03 me04
}

function test_failover_restart_failed_node_from_secondary_snapshot() {
	title "Failover with restart of failed node from secondary snapshot"
	init
	primary_warm_start me01
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	inject
	
	shutdown me01
	failover me02
	inject
	snapshot me03
	secondary_warm_start me01
	inject
	check_state me02 me03 me04 me01
	check_errors me01 me02 me03 me04
}

function test_sequence_primary_restart() {
	title "Sequence consistency across primary restart"
	init
	primary_warm_start me01
	inject
	snapshot me01
	
	secondary_warm_start me02
	inject

	shutdown me01
	primary_warm_start_with_replay me01
	inject
	check_state me01 me02
	check_errors me01 me02
}

function test_sequence_primary_failover() {
	title "Sequence consistency across primary failover"
	init
	primary_warm_start me01
	snapshot me01
	
	secondary_warm_start me02
	secondary_warm_start me03
	inject

	shutdown me01
	failover me02
	inject
	check_state me02 me03
	check_errors me02 me03
}

function test_snapshot_consistency() {
	title "Snapshot consistency"
	init
	primary_warm_start me01
	snapshot me01

	secondary_warm_start me02
	secondary_warm_start me03
	secondary_warm_start me04
	check_state me01 me02 me03 me04

	inject
	check_state me01 me02 me03 me04
	check_errors me01 me02 me03 me04
}

if [ -z $1 ]; then
	echo "$0: specify test categories to run"
	echo "      all                run all tests"
	echo "      primary            run primary startup tests"
	echo "      secondary          run secondary tests"
	echo "      failover           run failover tests"
	echo "      sequence           run sequence verification tests"
	echo "      snapshot           run snapshot verification tests"
	exit
fi

function run_primary_tests() {
	test_primary_start_cold
	test_primary_start_from_snapshot
	test_primary_start_from_snapshot_and_replay
}

function run_secondary_tests() {
	test_secondary_start_from_primary_snapshot
	test_secondary_start_from_delayed_primary_snapshot
	test_secondary_start_from_primary_snapshot_and_replay
	test_secondary_start_from_delayed_primary_snapshot_and_replay
	test_secondary_start_late_join_from_primary_snapshot
	test_secondary_start_late_join_from_secondary_snapshot
	test_secondary_start_cold
}

function run_failover_tests() {
	test_failover
	test_failover_restart_failed_node_from_old_primary_snapshot
	test_failover_restart_failed_node_from_new_primary_snapshot
	test_failover_restart_failed_node_from_secondary_snapshot
}

function run_sequence_tests() {
	test_sequence_primary_restart
	test_sequence_primary_failover
}

function run_snapshot_tests() {
	test_snapshot_consistency
}

while [ ! -z $1 ]; do
	case $1 in
		all)
			run_primary_tests
			run_secondary_tests
			run_failover_tests
			run_sequence_tests
			run_snapshot_tests
			;;
		primary)
			run_primary_tests
			;;
		secondary)
			run_secondary_tests
			;;
		failover)
			run_failover_tests
			;;
		sequence)
			run_sequence_tests
			;;
		snapshot)
			run_snapshot_tests
			;;
	esac
	shift
done
