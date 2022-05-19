#!/bin/bash

pid=`ps -eo "%p|%a" | grep java | grep com.solfini.matchengine.MatchEngineStarter | cut -d'|' -f1`
if [ ! -z $pid ]; then
    echo "stopping matching engine (pid: $pid)"
    kill -9 $pid
fi
