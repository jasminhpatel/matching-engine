#!/bin/bash

# This script launches the MatchingEngineController tool used for sending control messages to a matching engine
# running with the KafkaController for setting the mode of operation (i.e. function as primary or secondary).
# It can also submit requests for triggering snapshots.

# Run this with -h (or --help) for command line options and usage examples.

# Launch the controller
java -cp lib/match-engine-v2-0.0.1-SNAPSHOT.jar com.solfini.util.controller.MatchingEngineController $*
