#!/bin/bash

function clean() {
    if [ -d $1 ]; then
        echo "removing: $1/*"
        rm -fr $1/*
    else
        echo "creating: $1"
        mkdir -p $1
    fi
}

function remove() {
    if [ -e $1 ]; then
        echo "removing: $1"
        rm -f $1
    fi
}

clean /opt/wildfly/standalone/log/
clean /mnt/solfini-match-engine/logs/

remove /mnt/solfini-match-engine/output.log
remove /mnt/solfini-market-maker/output.log
remove /mnt/nohup.log

clean /mnt/logs/
clean /mnt/mm/execution-report/
clean /mnt/mm/order/
clean /mnt/mm/order-book-index/
clean /mnt/mm/user-order-index/
clean /mnt/chronicle/engine-balance/
clean /mnt/chronicle/engine-output/
clean /mnt/chronicle/pricing-output/
clean /mnt/chronicle/api-to-engine/
clean /mnt/chronicle/api-admin-to-engine/

echo "deploying matching engine"
cd /mnt/solfini-match-engine
cp /home/ubuntu/match-engine-v2-0.0.1-SNAPSHOT*.jar ./lib
tar xvfz /home/ubuntu/match-engine-v2-0.0.1-SNAPSHOT-deployment.tgz
