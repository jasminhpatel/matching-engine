#!/bin/bash

set -e

if [ -z "$1" ]; then
    echo "error: package name must be specified"
    exit 1
fi

module="match-engine"
package=$1

path=$HOME/jenkins/package/$package
if [ ! -d $path ]; then
    mkdir -p $path
fi

cp target/*.jar $path/

commit=`git log | head -1 | awk '{print substr($2,0,8)}'`
if [ ! -e $path/version.info ]; then
    touch $path/version.info
fi

cat $path/version.info | sed "/^$module/d" > $path/version.tmp
echo "$module: $commit" >> $path/version.tmp
cat $path/version.tmp | sort > $path/version.info
rm -f $path/version.tmp
