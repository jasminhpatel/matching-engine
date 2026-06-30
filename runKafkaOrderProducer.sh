#!/bin/bash

ORDER_COUNT=${1:-5000000}

java \
  -server \
  -cp ".:config.properties:target/classes:target/test-classes:lib/*" \
  com.solfini.performance.KafkaOrderProducer "$ORDER_COUNT"