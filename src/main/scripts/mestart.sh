#!/bin/bash

# This script launches the matching engine with a specified configuration
profile=""
if [ $1 == "--profile" ]; then
  shift
  profile=$1
  shift
fi

found=0
config=""
for arg in $@; do
  if [ $found == 1 ]; then
    config=$arg
    found=0
  elif [ $arg == "-c" -o $arg == "--config" ]; then
    found=1
  fi
done

if [ ! -z $config ]; then
  if [ -f $config ]; then
    config=`dirname $config`
  fi
fi

if [ "$profile" == "throughput" ]; then
  java -server -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xms128g -Xmx128g -cp $config:lib/* com.solfini.matchengine.MatchEngineStarter $* >output.log 2>&1 &
elif [ "$profile" == "minimal" ]; then
  java -server -XX:+UseG1GC -XX:MaxGCPauseMillis=500 -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=50 -XX:InitiatingHeapOccupancyPercent=70 -XX:+ParallelRefProcEnabled -XX:+ExplicitGCInvokesConcurrent -XX:+UseStringDeduplication -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xms16g -Xmx16g -cp $config:lib/* com.solfini.matchengine.MatchEngineStarter $* >output.log 2>&1 &
elif [ "$profile" == "docker" ]; then
  java -server -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+UseStringDeduplication -Xmx128g -javaagent:lib/org.jacoco.agent-0.8.4-runtime.jar=output=tcpserver,address=*,port=6300 -cp $config:lib/* com.solfini.matchengine.MatchEngineStarter $* 
else
  #java -server -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector -XX:+UseG1GC -XX:MaxGCPauseMillis=500 -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=50 -XX:InitiatingHeapOccupancyPercent=70 -XX:+ParallelRefProcEnabled -XX:+ExplicitGCInvokesConcurrent -XX:+UseStringDeduplication -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xms256g -Xmx256g -cp $config:lib/* com.solfini.matchengine.MatchEngineStarter $* >output.log 2>&1 &
  java -server -DLog4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector -XX:+UseG1GC -XX:MaxGCPauseMillis=500 -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+ExplicitGCInvokesConcurrent -XX:+UseStringDeduplication -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xms256g -Xmx256g -cp $config:lib/* com.solfini.matchengine.MatchEngineStarter $* >output.log 2>&1 &
fi
