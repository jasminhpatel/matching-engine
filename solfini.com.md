# Deployment and Operation Guide for solfini.com

## Primary Matching Engine

- Server: 207.180.237.240
- Deployment location: /mnt/match-engine
- Instance ID: me01
- Configuration: /mnt/match-engine/config/solfini/primary/config.properties

## Secondary Matching Engine

- Server: 167.86.103.84
- Deployment location: /mnt/match-engine
- Instance ID: me02
- Configuration: /mnt/match-engine/config/solfini/secondary/config.properties

## Stopping

Run the following command from `/mnt/match-engine` directory of the instance.

    ./stop.sh

You can check if the instance is running or not by doing a `ps -ef | grep java`.

## Starting

Run the following command from `/mnt/match-engine` directory of the instance.

    ./start.sh

## Deployment

From the secondary matching engine machine:
- Shutdown the secondary matching engine

From the primary matching engine machine:
- Shutdown the primary matching engine
- Copy the new matching engine and changed dependency JAR files to `/mnt/match-engine/lib` directory
- Start the primary matching engine
- Request the primary to do a snapshot by running `./scripts/mectrl.sh -c config/solfini/primary/config.properties -i me01 --snapshot`
- Note the new snapshot id

From the secondary matching engine machine:
- Pull the snapshot that the primary created to the secondary machine by running `/mnt/data/snap/snap-pull.sh <snap-id>`
- Start the secondary matching engine by running `./start.sh --snapshot <snap-id>` from the `/mnt/match-engine` directory
