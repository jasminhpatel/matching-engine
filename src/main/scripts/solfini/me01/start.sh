#!/bin/bash

instance="me01"
config="primary"

echo "[match-engine] $instance"
cd /mnt/match-engine

echo "cleaning logs"
rm -f output.log
rm -f logs/*

echo "starting"
./scripts/mestart.sh -c config/solfini/$config/config.properties $@

echo "done"

