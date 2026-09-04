#!/bin/bash
# On-demand composite reconciliation (TSYS-176). Runs ONLY the reconcile check for one
# network+compositeId - never publishes/updates anything on-chain (same read-only guarantee as
# check-reconciliation.sh, just composite-keyed and callable non-interactively).
#
# Intended caller: solfini-blockchain-listener's CompositeBalanceReconciliationThread
# (TSYS-100/TSYS-177), shelling out to this script with the exact network + compositeId the
# real CompositeBalanceUpdated event carried, once per event.
#
# cd's into its own directory first (deploy location, e.g. /opt/blockchain-reconcile) so
# ./config.properties and ./log4j2.properties resolve the same way regardless of the caller's own
# working directory - deploy this script alongside config.properties/log4j2.properties/lib/, same
# layout as reconcile_fund_manager_v2.sh.
#
# -c=./config.properties is passed through unconditionally (not user-configurable here) - see
# FundManagerV2ReconcileTriggerJob's header comment for why it's required despite
# FundManagerV2Reconciliation ignoring it directly for its own config load.
#
# Usage: ./composite-reconcile-trigger.sh <NETWORK> <COMPOSITE_ID>
#   ./composite-reconcile-trigger.sh MAINNET 0xabc...
#   ./composite-reconcile-trigger.sh XDC 0xdef...
set -euo pipefail

NETWORK="${1:?Usage: $0 <NETWORK> <COMPOSITE_ID>}"
COMPOSITE_ID="${2:?Usage: $0 <NETWORK> <COMPOSITE_ID>}"

cd "$(dirname "$0")"

LOG="logs/composite_reconcile_${NETWORK}_$(date +%Y%m%d%H%M%S).log"

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
  -Xms2G -Xmx4G \
  -DENCRYPTION_KEY=Bar12345Mac12345 \
  -Dlogging.config='./log4j2.properties' \
  -cp .:lib/* com.solfini.reconciliation.FundManagerV2ReconcileTriggerJob \
  -c=./config.properties \
  -d NETWORK="${NETWORK}" \
  -d COMPOSITE_ID="${COMPOSITE_ID}" \
  2>&1 | tee "$LOG"
