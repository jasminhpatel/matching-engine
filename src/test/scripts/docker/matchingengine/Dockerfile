FROM openjdk:8

COPY src/main/scripts /app/scripts
COPY src/test/scripts /app/scripts
COPY config /app/config
COPY lib /app/lib
COPY target/match-engine-*.jar /app/lib/
COPY src/test/scripts/docker/matchingengine/run_class.sh /app/scripts/
COPY src/test/scripts/docker/matchingengine/generate_snap.sh /app/scripts/
COPY src/test/resources/SnapConverterTest.json /resources/SnapConverterTest.json
RUN mkdir -p /tmp/snap_cache

WORKDIR /app
