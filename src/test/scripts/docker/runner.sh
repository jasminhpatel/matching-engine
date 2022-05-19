#!/bin/bash

set -e 
# set -x 

build_kafka=0
build_me=0
timeout=120
COMMANDS=()

function show_help()
{
    echo "Following options can be specified multiple times in any order."
    echo "unit              Run unit tests"
    echo "integration       Run integration tests"
    echo "e2e               Run end to end tests"
    echo "--build           Builds all images"            
    echo "--build_kafka     Builds Kafka image"            
    echo "--build_me        Builds Matching engine image"            
    echo "--timeout sec     Timeout in seconds for each test."            
}

# Formatter functions
function print_title()
{
	echo -e "\033[1;34m=== $1 ===\033[0m"
}

function print_message()
{
	echo "    $1"
}

function print_error()
{
	echo -e "\033[0;31mERROR: $1\033[0m"
}

function print_warning()
{
	echo -e "\033[0;33mWARNING: $1\033[0m"
}

function generate_snapshot()
{
    print_title "Creating docker volume"
    docker volume rm snap || true
    docker volume create snap

    print_title "generating snapshot"
    docker run --rm -v snap:/snap  --name snap solfini-matching-engine scripts/generate_snap.sh
}

function build_images()
{
    # Generate matching engine image
    if [ $build_me -eq 1 ];then
        print_title "Builing matching engine image"
        
        # Find jcoco.jar location
        JCOCO_JAR=`mvn  jacoco:prepare-agent | grep -e '-javaagent.*$' -o | grep -P '(?<=-javaagent:).*\.jar' -o`
        if [ -z "$JCOCO_JAR" ]; then
            print_error "jcoco agent lib not found."
        else 
            print_title "Using $JCOCO_JAR"
            cp $JCOCO_JAR lib/$(basename $JCOCO_JAR)
        fi

        # Build docker image
        docker build --no-cache -t solfini-matching-engine -f src/test/scripts/docker/matchingengine/Dockerfile .

        # Create snapshot
        generate_snapshot

    else
        print_warning "Skipping building matching engine"
    fi

    # Generate Kafka image
    if [ $build_kafka -eq 1 ];then
        print_title "Building Kafka image"
        docker build --no-cache -t solfini-kafka -f src/test/scripts/docker/kafka/Dockerfile src/test/scripts/docker/kafka
    else
        print_warning "Skipping building kafka"
    fi
}

function integration_test() 
{
    export ME1_PARAMS="--cold-start"
    export ME2_PARAMS="--cold-start"

    # start instances
    print_title "Running integration tests"
    cd src/test/scripts/docker
    docker-compose down
    docker-compose up -d zookeeper kafka me1 me2

    # Wating for me to start
    print_message "Waiting for ME to start"
    sleep 10
    
    # Run test
    print_message "Running"

    set +e
    docker run --rm -v ~/.m2:/root/.m2 -v $ROOT_DIR:/src -v snap:/snap --network=container:zookeeper --name maven maven:3-jdk-8  bash -c 'cd /src && mvn test -Dskip-tests=false -Dtest=*.integration.*' 

    # Revert back the owenership of the directory that might have changed due to doocker run as root.
    sudo chown -R $USER:$USER $ROOT_DIR

    # dump coverage data
    cd $ROOT_DIR
    mvn jacoco:dump -Djacoco.port=6301
    mvn jacoco:dump -Djacoco.port=6302
    cd src/test/scripts/docker

    sleep 10
    # Shutdown
    docker-compose down --timeout 10
    set -e
}

function e2e()
{
    export ME1_PARAMS="--warm-start"
    export ME2_PARAMS="--cold-start"

    # start instances
    print_title "Running e2e tests"
    cd src/test/scripts/docker
    docker-compose down
    docker-compose up -d zookeeper kafka

    print_title "Starting primary"
    docker-compose up me1 &

    print_title "Waiting for primary to start"
    sleep 60

    print_title "Starting secondary"
    docker-compose up me2 &

    print_title "Waiting for secondary to start"
    sleep 60

    set +e
    
    # Run test
    print_title "Injecting orders"
    docker run --rm --network=container:zookeeper --name e2e -v snap:/snap solfini-matching-engine scripts/run_class.sh com.solfini.util.benchmark.Injector -c config/staging/docker/config.properties -o 1000 -u 100 -r 500 -pc 1 -pt 1

    print_title "Waiting for ME to process orders"
    sleep 60

    print_title "Sending create snapshot command"
    docker run --rm --network=container:zookeeper --name control -v snap:/snap solfini-matching-engine scripts/mectrl.sh -c config/staging/docker/config.properties -i all --snapshot

    print_title "Waiting for snapshot to complete"
    sleep 60

    # dump coverage data
    cd $ROOT_DIR
    mvn jacoco:dump -Djacoco.port=6301
    mvn jacoco:dump -Djacoco.port=6302
    cd src/test/scripts/docker

    # Shutdown
    docker-compose down --timeout 10
    set -e
}

function e2e2()
{
    export ME1_PARAMS="--warm-start"

    # start instances
    print_title "Running e2e test 2"
    cd src/test/scripts/docker
    docker-compose down
    docker-compose up -d zookeeper kafka

    print_title "Starting primary"
    docker-compose up me1 &

    print_title "Waiting for primary to start"
    sleep 60

    print_title "Create a snapshot"
    docker run --rm --network=container:zookeeper --name control -v snap:/snap solfini-matching-engine scripts/mectrl.sh -c config/staging/docker/config.properties -i primary --snapshot

    set +e
    print_title "Waiting for primary to create snapshot"
	while [ 1 ]; do
		ready=`docker logs me | grep "Snapshot completed:"`
		if [ ! -z "$ready" ]; then
			break
		fi
		sleep 1
	done
	SNAP_ID=`docker logs me | grep "Snapshot completed:" | tail -1 | cut -d'=' -f2 | cut -d',' -f1`
    set -e

    print_title "Injecting more orders"
    docker run --rm --network=container:zookeeper --name e2e -v snap:/snap solfini-matching-engine scripts/run_class.sh com.solfini.util.benchmark.Injector -c config/staging/docker/config.properties -o 1000 -u 100 -r 500 -pc 1 -pt 1

    print_title "Waiting for ME to process orders"
    sleep 60

    print_title "Starting secondary"
    export ME2_PARAMS="-d LOAD_FROM_SNAP=$SNAP_ID -d LOAD_FROM_SNAP_AND_REPLAY=BOTH"
    docker-compose up me2 &

    print_title "Waiting for secondary to start"
    sleep 120

    print_title "Sending create snapshot command"
    docker run --rm --network=container:zookeeper --name control -v snap:/snap solfini-matching-engine scripts/mectrl.sh -c config/staging/docker/config.properties -i all --snapshot

    print_title "Waiting for snapshot to complete"
    sleep 60

    set +e
    print_title "Dump coverage data"
    cd $ROOT_DIR
    mvn jacoco:dump -Djacoco.port=6301
    mvn jacoco:dump -Djacoco.port=6302
    cd src/test/scripts/docker

    print_title "Stop primary"
    docker-compose down me

    print_title "Waiting for zookeeper to update"
    sleep 30

    print_title "Injecting more orders"
    docker run --rm --network=container:zookeeper --name e2e -v snap:/snap solfini-matching-engine scripts/run_class.sh com.solfini.util.benchmark.Injector -c config/staging/docker/config.properties -o 1000 -u 100 -r 500 -pc 1 -pt 1 -uid 200

    print_title "Waiting"
    sleep 30

    print_title "Promote secondary to primary"
    docker run --rm --network=container:zookeeper --name control -v snap:/snap solfini-matching-engine scripts/mectrl.sh -c config/staging/docker/config.properties -i me02 --primary

    print_title "Waiting for me02 to become primary"
    sleep 60

    print_title "Dump me2 coverage data"
    cd $ROOT_DIR
    mvn jacoco:dump -Djacoco.port=6302
    cd src/test/scripts/docker

    # Shutdown
    docker-compose down --timeout 10
    set -e
}

function zookeeper_failover()
{
    export ME1_PARAMS="--warm-start -d CONTROLLER_TYPE=zookeeper -d CONTROLLER_ZOOKEEPER_PRIORITY=100"
    export ME2_PARAMS="--cold-start -d CONTROLLER_TYPE=zookeeper -d CONTROLLER_ZOOKEEPER_PRIORITY=5"
    export ME3_PARAMS="--cold-start -d CONTROLLER_TYPE=zookeeper -d CONTROLLER_ZOOKEEPER_PRIORITY=1"

    # start instances
    print_title "Starting up zookeeper and kafka"
    cd src/test/scripts/docker
    docker-compose down
    docker-compose up -d zookeeper kafka

    print_title "Starting primary"
    docker-compose up me1 &

    print_title "Waiting for primary to start"
    sleep 60

    print_title "Starting secondaries"
    docker-compose up me2 me3 &

    print_title "Waiting for secondaries to start"
    sleep 90

    
    print_title "Injecting orders"
    docker run --rm --network=container:zookeeper --name e2e -v snap:/snap solfini-matching-engine scripts/run_class.sh com.solfini.util.benchmark.Injector -c config/staging/docker/config.properties -o 1000 -u 100 -r 500 -pc 1 -pt 1

    print_title "Waiting for ME to process orders"
    sleep 60

    # dump coverage data
    print_title "Dump code coverage"
    set +e
    cd $ROOT_DIR
    mvn jacoco:dump -Djacoco.port=6301
    cd src/test/scripts/docker

    # Failover
    print_title "Stopping ME1"
    docker-compose stop me1
    set -e

    print_title "Waiting for ME2 to take over as primary"
    sleep 60

    print_title "Sending few messages as exited primary. These should be ignored."
    docker run --rm --network=container:zookeeper --name ConsistencyCheck -v snap:/snap solfini-matching-engine scripts/run_class.sh com.solfini.matchengine.controller.ZookeeperFailoverConsistencyTest -c config/staging/docker/config.properties -i me01

    print_title "Waiting untill messages are processed"
    sleep 30

    # dump coverage data
    print_title "Dump code coverage"
    set +e
    cd $ROOT_DIR
    mvn jacoco:dump -Djacoco.port=6302
    mvn jacoco:dump -Djacoco.port=6303
    cd src/test/scripts/docker

    # Shutdown
    docker-compose down
    set +e
}

function unit_test()
{
    grep -rl ./src/test/java/ -e '^[[:space:]]*@Test[[:space:]]*' | grep -e '\.java$' | grep -v 'integration' | sort | uniq | sed 's/\.\/src\/test\/java\///g' | sed 's/\//./g' | sed 's/\.java$//g' | while read class; do
        print_title "Running class : $class"
        timeout $timeout mvn -q test -Dskip-tests=false -Dtest=$class && print_title "Done Running $class" || print_error "Timeout while running $class" 
    done
}

while [ ! -z $1 ]; do
    case "$1" in 
        -h|--help)
            show_help
            exit 0
        ;;

        --build_kafka)
            build_kafka=1
        ;;

        --build_me)
            build_me=1
        ;;

        --timeout)
            shift 
            timeout=$1
        ;;

        --build)
            build_kafka=1
            build_me=1
        ;;

        integration)
            COMMANDS+=('integration_test')
        ;;

        unit)
            COMMANDS+=('unit_test')
        ;;

        e2e)
            COMMANDS+=('e2e')
        ;;

        e2e2)
            COMMANDS+=('e2e2')
        ;;

        zookeeper_failover)
            COMMANDS+=('zookeeper_failover')
        ;;

        *)
        print_warning "Invalid argument: $1"
        show_help
        exit 1        
    esac
    shift
done


# Directory of the build script
DIR="$(cd "$(dirname "$0")" && pwd)"

# switch to root dir
cd $DIR/../../../../
ROOT_DIR=$PWD

# Check if the images are present
me_present=$(docker images | grep solfini-matching-engine || true)
if [ -z "$me_present" ];then 
    build_me=1
fi

kafka_present=$(docker images | grep solfini-kafka || true)
if [ -z "$kafka_present" ];then 
    build_kafka=1
fi

# Build images
build_images

# Run commands
for cmd in "${COMMANDS[@]}"; do
    cd $ROOT_DIR
    $cmd
done
