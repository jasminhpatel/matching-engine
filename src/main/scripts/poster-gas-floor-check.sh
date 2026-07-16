#!/bin/bash
# Daily poster gas floor check: reads each poster's native gas balance (XDC/ETH/POL) and emails a
# status report if any balance is at or below its configured floor. Stateless - no history, no DB.
#
# Usage: ./poster-gas-floor-check.sh [config-file] [DRY_RUN]
#   ./poster-gas-floor-check.sh                                   # real run against config.properties
#   ./poster-gas-floor-check.sh test-config.properties true       # console-only preview, no email
set -euo pipefail

CONFIG="${1:-config.properties}"
DRY_RUN="${2:-false}"

LOG="poster_gas_floor_check_$(date +%Y%m%d%H%M%S).log"

java \
  --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED \
  --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
  --add-opens=jdk.compiler/com.sun.tools.javac=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  -XX:+UseZGC -XX:+ZGenerational -XX:-ZUncommit \
  -Xms2G -Xmx2G \
  -DENCRYPTION_KEY=Bar12345Mac12345 \
  -Dlogging.config='./log4j2.properties' \
  -cp .:lib/* com.solfini.reconciliation.PosterGasFloorCheckJob \
  -c="./${CONFIG}" \
  -d DRY_RUN="${DRY_RUN}" \
  2>&1 | tee "$LOG"
