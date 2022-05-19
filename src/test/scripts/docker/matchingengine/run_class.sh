#!/bin/bash

echo "Running class $1"
class=$1
shift
java -cp config/staging/docker/:lib/* $class $*
