#!/usr/bin/env bash

# This script launches the SnapshotConverter in export mode which will convert snapshot to json file.
java -Xms1024m -Xmx88192m -cp lib/match-engine-v2-0.0.1-SNAPSHOT.jar:lib/* com.solfini.util.snapshot.SnapConverter -m export $@
