
### Download Kafka

Kafka can be downloaded from the following location. Note that a newer version of Kafka may be available.

```
wget https://www-eu.apache.org/dist/kafka/2.1.1/kafka_2.11-2.1.1.tgz 
tar xvf kafka_2.11-2.1.1.tgz
```

### Start ZooKeeper

Kafka installation includes ZooKeeper. You can start it as follows.

```
cd kafka_2.11-2.1.1
./bin/zookeeper-server-start.sh ./config/zookeeper.properties &
```

This will start ZooKeeper using the default configuration file `config/zookeeper.properties` that comes with the installation.

By default ZooKeeper starts on the default port 2181. To change the default listening port, change the `clientPort` parameter in the `zookeeper.properties` configuration file.

You may also want to change the storage location for ZooKeeper state data using the `dataDir` configuration parameter.

### Start Kafka

You can start Kafka as follows.

```
cd kafka_2.11-2.1.1
./bin/kafka-server-start.sh ./config/server.properties &
```

This will start Kafka using the default configuration file `config/server.properties` that comes with the installation.

Change the following configuration values in `server.properties` as required before starting the Kafka server.

- `broker.id`: The identifier of the kafka broker instance. This has to be a unique integer for each Kafka instance (default is `0`).
- `zookeeper.connect`: IP and port address where ZooKeeper is listening (default is `localhost:2181`).
- `listeners`: IP and port address where Kafka server will listen for client connections. The `server.properties` file in this folder sets this to `INT://:9092,EXT://3.209.193.87:4455`, indicating that Kafka is listening on port 9092 on the internal network and on 3.209.193.87:4455 on the external network. Change this as per the host configuration.
- `log.dirs`: Directory where Kafka data is written.
- `log.retention.hours`: Message retention time on Kafka. Messages after this many hours would be removed from the Kafka storage.

#### Multiple Kafka Instances

Kafka instances can be clustered. This can be done by pointing them at the same ZooKeeper instance and assigning different `broker.id` values for each instance.
