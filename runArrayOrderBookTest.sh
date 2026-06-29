#!/bin/bash

ORDER_COUNT=${1:-10000}

java \
  -server -Xms48g -Xmx48g -XX:+UseParallelGC -XX:ParallelGCThreads=8 -XX:+AlwaysPreTouch -XX:GCTimeRatio=19 -XX:NewRatio=2 -XX:SurvivorRatio=8 -XX:+DisableExplicitGC -XX:+UseNUMA \
  -cp ".:config.properties:target/classes/:target/test-classes/:lib/*" \
  com.solfini.matchengine.ArrayOrderBookTest \
  "$ORDER_COUNT"