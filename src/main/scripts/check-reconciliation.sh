#!/bin/bash
# Read-only reconciliation preview: reads the currently published snapshot + live on-chain
# balances and sends the real HTML summary email. Never pushes anything to the blockchain
# (does not call FundManagerV2SnapUpdater.update() or sendHeartbeatBatch()).
#
# Usage: ./check-reconciliation.sh <NETWORK> <SYMBOL> [config-file]
#   ./check-reconciliation.sh MAINNET USDC
#   ./check-reconciliation.sh MAINNET USDT
#   ./check-reconciliation.sh XDC XUSDC test-config.properties
#
# Defaults to test-config.properties so it doesn't email the real RECONCILIATION_ALERT_EMAILS
# list. Create it once with a test recipient:
#   cp config.properties test-config.properties
#   echo 'RECONCILIATION_ALERT_EMAILS=alerts.mihindu@gmail.com' >> test-config.properties
set -euo pipefail

NETWORK="${1:?Usage: $0 <NETWORK> <SYMBOL> [config-file]}"
SYMBOL="${2:?Usage: $0 <NETWORK> <SYMBOL> [config-file]}"
CONFIG="${3:-test-config.properties}"

LOG="reconcile_check_${NETWORK}_${SYMBOL}_$(date +%Y%m%d%H%M%S).log"

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
  -Xms24G -Xmx24G \
  -DENCRYPTION_KEY=Bar12345Mac12345 \
  -Dlogging.config='./log4j2.properties' \
  -cp .:lib/* com.solfini.reconciliation.FundManagerV2ReconciliationCheckJob \
  -c="./${CONFIG}" \
  -d NETWORK="${NETWORK}" \
  -d SYMBOL="${SYMBOL}" \
  2>&1 | tee "$LOG"
