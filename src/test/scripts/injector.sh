#!/bin/bash

# This script launches the Injector tool used for creating users and submitting orders for performance testing.

# Run this with -h (or --help) for command line optiopns and usage examples.

# Launch the injector
java -cp lib/match-engine-v2-0.0.1-SNAPSHOT.jar com.solfini.util.benchmark.Injector $*
