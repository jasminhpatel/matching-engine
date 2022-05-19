# Solfini Matching Engine

This repository contains the Solfini matching engine.

## Building

Build the matching engine and package it in to a JAR without dependencies as follows. Note that the resulting JAR would search for dependencies in the same directory.

    mvn clean package

Build the matching engine and package it in to a JAR with all dependencies as follows.

    mvn clean package assembly:single

## Installing

On a run environment with a deployment root `$DEPLOYMENT_ROOT` (say `/mnt/solfini-match-engine`), have the following setup.

- Copy the matching engine JAR and any other dependencies to `$DEPLOYMENT_ROOT/lib`
- Copy all configuration files (from the source repository's `config` directory) to `$DEPLOYMENT_ROOT/config`
- Copy all script files (from the source repository's `scripts` directory) to `$DEPLOYMENT_ROOT/scripts`

## Starting

The matching engine can be started using the `mestart.sh` scripts. This script requires a configuration file. Take a look at the configuration files in the `config` directory for examples.

Start the matching engine as follows (from within the `$DEPLOYMENT_ROOT` directory). Note that this would run the matching engine based on the configuration specified in the `config/prod/primary/config.properties` configuration file.

    ./scripts/mestart.sh -c config/prod/primary/config.properties

The `-c` command line argument is used for specifying the configuration file to use. The argument to `-c` can either be the path to a configuration properties file, or a directory that contains a file named `config.properties`. Configuration for logging can be specified in a file named `log4j2.properties` by placing it in the same directory as the main configuration properties file.

Start the matching engine in cold start mode (without any state being loaded from a snapshot or any state recovery from the output queue) as follows. Note that using the `--cold-start` option overrides the configuration properties to be equivalent to `LOAD_FROM_SNAP=` and `LOAD_FROM_SNAP_AND_REPLAY=NONE`.

    ./scripts/mestart.sh -c config/prod/primary/config.properties --cold-start

Start the matching engine in warm start mode (with state being loaded from a snapshot but no state recovery from the output queue) as follows. Note that using the `--warm-start` option overrides the configuration properties to be equivalent `LOAD_FROM_SNAP_AND_REPLAY=NONE`.

    ./scripts/mestart.sh -c config/prod/primary/config.properties --warm-start

If warm start is to use a specific snapshot (other than the one specified in the configuration file under `LOAD_FROM_SNAP`), use the `--snapshot` option.

    ./scripts/mestart.sh -c config/prod/primary/config.properties --warm-start --snapshot 1555426740161922560

Use the `--mode primary` option to start the matching engine in primary mode. Note that using the `--mode` option overrides the `CONTROLLER_MODE` configuration property.

        ./scripts/mestart.sh -c config/prod/primary/config.properties --mode primary

 Use the `--mode secondary` option to start the matching engine in secondary (DR) mode.

        ./scripts/mestart.sh -c config/prod/secondary/config.properties --mode secondary

By default, logs produced by the matching engine is configured using the `log4j2.properties` file. In addition, the main log level can be specified using the `LOG_LEVEL` configuration parameter in the main configuration file. Valid values are `ERROR`, `WARN`, `INFO` and `DEBUG`, with `INFO` being the default. Under this default configuration using the `--debug` argument in the `mestart.sh` script would enable debug level logging.

Run the following for more startup options.

    ./scripts/mestart.sh -h

## Shutdown

A matching engine instance can be gracefully shutdown using the following command.

    ./scripts/mectrl.sh -c config/prod/primary/config.properties -i me01 --shutdown

## Status reporting

A matching engine instance can be requested to report its current status (including mode of operation) using the following command. Status details would appear in the instance log.

    ./scripts/mectrl.sh -c config/prod/primary/config.properties -i me01 --status

## Failover

Matching engine instances start in either `primary` or `secondary` mode based on its configuration. At any given time, there should be at most one primary, and any number of secondaries. Each matching engine instance is assigned a unique identifier via the configuration property `INSTANCE_ID`. Note that this name case sensitive.

Use the following command to promote the secondary matching engine `me02` to primary, when it is configured for manual failover (using `CONTROLLER_TYPE=kafka`). Note that the configuration file specified with the `-c` argument can be the configuration property file for any of the matching engines, as it is only used for properties related to Kafka configuration.

    ./scripts/mectrl.sh -c config/prod/secondary/config.properties -i me02 --primary

## Snapshots

Snapshots are used by the matching engine to persist state at a point in time. This can later be used for restoring state during start-up.

Use the following command to get matching engine `me01` to write a new snapshot.

    ./scripts/mectrl.sh -c config/prod/secondary/config.properties -i me01 --snapshot

Use the following command to get the current primary matching engine to write a new snapshot.

    ./scripts/mectrl.sh -c config/prod/secondary/config.properties -i primary --snapshot

Use the following command to get the current secondary matching engines to write new snapshots. The aliases `secondaries` and `all-secondaries` can be used in place of `secondary`.

    ./scripts/mectrl.sh -c config/prod/secondary/config.properties -i secondary --snapshot

Use the following command to get all matching engines to write new snapshots.

    ./scripts/mectrl.sh -c config/prod/secondary/config.properties -i all --snapshot

The snapshot converter tool can be used for exporting binary snapshots in to a JSON like human readable/editable format, and vice versa. This can be used for inspecting the contents of a snapshot or to build one with the required content.

Export a snapshot to a JSON file as follows.

    ./scripts/snap-to-json.sh -s <snapshot-path> -j <json-file>

Import a snapshot from a JSON file as follows.

    ./scripts/json-to-snap.sh -j <json-file> -s <snapshot-path>
