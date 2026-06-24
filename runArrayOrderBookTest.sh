#!/bin/bash

ORDER_COUNT=${1:-10000}

java \
  -Xms48g \
  -Xmx48g \
  -cp ".:config.properties:target/classes/:target/test-classes/:lib/*" \
  com.solfini.matchengine.ArrayOrderBookTest \
  "$ORDER_COUNT"