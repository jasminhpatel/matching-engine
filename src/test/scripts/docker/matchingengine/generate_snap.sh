#!/bin/bash

# Generate the snapshot from JSON
java -cp /app/config/staging/docker/:/app/lib/* com.solfini.util.snapshot.SnapConverter -j /resources/SnapConverterTest.json -s /snap -m import

# Rename the snapshot
rm -rf /snap/10000001
latestSnap=$(ls -1tr /snap | tail -1)
mv /snap/$latestSnap /snap/10000001
