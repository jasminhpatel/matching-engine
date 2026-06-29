bash#!/bin/bash

ORDER_COUNT=${1:-10000}

java \
  -server -Xms128g -Xmx128g -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:ReservedCodeCacheSize=512m \
  -cp ".:config.properties:target/classes:target/test-classes:lib/*" \
  com.solfini.matchengine.ArrayOrderBookTest "$ORDER_COUNT"