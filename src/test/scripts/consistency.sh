#!/bin/bash

class=com.solfini.matchengine.model.orderbook.PositionContractConsistencyTest3
log=logs/consistency.log

if [ ! -z $1 ]; then
    class=$1
fi

echo "RUNNING $class"
for i in {0..65535}; do
    echo "SEED $i"
    if [ -e $log ]; then
        rm $log
    fi
    java -cp lib/match-engine-v2-0.0.1-SNAPSHOT-tests.jar:lib/* $class $i >$log
    errors=`grep -e Exception -e ERROR $log | wc -l`
    if [ $errors == 0 ]; then
        echo "OK"
    else
        echo "FAILED"
        cp $log logs/consistency-$class-$i.log
    fi
done
