FROM openjdk:8

RUN mkdir /app && curl -SL https://downloads.apache.org/kafka/2.5.0/kafka_2.12-2.5.0.tgz | tar -xzC /app
WORKDIR /app/kafka_2.12-2.5.0

# CMD [ "bin/kafka-server-start.sh", "config/server.properties" ]
# CMD [ "bin/zookeeper-server-start.sh", "config/zookeeper.properties" ]
