#!/usr/bin/env bash

# This script launches a snapshot comparison tool that compare 2 snapshot json files.
java -Xms1024m -Xmx88192m -cp lib/match-engine-v2-0.0.1-SNAPSHOT-test.jar:lib/* com.solfini.util.snapshot.SnapConverterTest $@
