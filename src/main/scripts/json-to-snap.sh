#!/usr/bin/env bash

# This script launches the SnapshotConverter in import mode which will convert json file to snapshot.
java -Xms1024m -Xmx88192m -cp lib/match-engine-v2-0.0.1-SNAPSHOT.jar:lib/* com.solfini.util.snapshot.SnapConverter -m import $@
