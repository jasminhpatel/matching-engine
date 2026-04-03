#!/bin/bash
# filepath: /mydrive/crypto-trade/solfini-matching-engine-qt/src/main/scripts/test-bitget-client.sh

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
while [ ! -f "${PROJECT_DIR}/pom.xml" ] && [ "${PROJECT_DIR}" != "/" ]; do
    PROJECT_DIR="$(dirname "${PROJECT_DIR}")"
done
if [ ! -f "${PROJECT_DIR}/pom.xml" ]; then
    echo "ERROR: Could not find project root (pom.xml)"
    exit 1
fi
JAR_FILE="${PROJECT_DIR}/target/solfini-match-engine-qt-v1.5.0-snapshot-fat.jar"
MAIN_CLASS="com.solfini.matchengine.liquidity.direct.aiGenerated.prodtest.BitgetFastClientProdTest"
LOG_DIR="${PROJECT_DIR}/logs"

mkdir -p "${LOG_DIR}"

if [ ! -f "${JAR_FILE}" ]; then
    echo "ERROR: JAR file not found at ${JAR_FILE}"
    exit 1
fi

if [ -z "${BITGET_DEMO_API_KEY}" ] || [ -z "${BITGET_DEMO_API_SECRET}" ] || [ -z "${BITGET_PASSPHRASE}" ]; then
    echo "ERROR: BITGET_DEMO_API_KEY, BITGET_DEMO_API_SECRET, and BITGET_PASSPHRASE environment variables must be set"
    echo "Export them before running this script:"
    echo "  export BITGET_DEMO_API_KEY='your_api_key'"
    echo "  export BITGET_DEMO_API_SECRET='your_api_secret'"
    echo "  export BITGET_PASSPHRASE='your_passphrase'"
    exit 1
fi

if [ $# -lt 1 ]; then
    echo "Usage: $0 <trade_params_string> [OUTBOUND_IP=ip_address]"
    echo ""
    echo "Example:"
    echo "  $0 's=SOLUSDT,p=125,ps=2,q=3,qs=1,side=Buy,t=LIMIT,tf=GTC,i=100' 'OUTBOUND_IP=192.168.1.0'"
    echo ""
    echo "Trade Parameters Format: key1=value1,key2=value2,..."
    echo ""
    echo "Available Trade Parameters:"
    echo "  s    - Symbol (required, e.g., SOLUSDT)"
    echo "  p    - Price (required, e.g., 125)"
    echo "  ps   - Price scale/decimals (required, e.g., 2)"
    echo "  q    - Quantity (required, e.g., 3)"
    echo "  qs   - Quantity scale/decimals (required, e.g., 1)"
    echo "  side - Side: Buy or Sell (required)"
    echo "  t    - Order type: LIMIT or MARKET (required)"
    echo "  tf   - Time in force: GTC or FOK (required)"
    echo "  i    - Number of iterations/orders (optional, default: 1)"
    echo ""
    echo "Order Types:"
    echo "  LIMIT  - Limit order at specified price"
    echo "  MARKET - Market order at best available price"
    echo ""
    echo "Time in Force:"
    echo "  GTC - Good Till Cancel (order stays until filled or cancelled)"
    echo "  FOK - Fill or Kill (entire order must fill immediately or cancel)"
    echo ""
    echo "Optional Second Argument:"
    echo "  OUTBOUND_IP=<ip_address> - Set outbound IP (default: 192.168.1.1)"
    exit 1
fi

TRADE_PARAMS="$1"
OUTBOUND_IP_ARG="${2:-OUTBOUND_IP=192.168.1.1}"

OUTBOUND_IP=$(echo "${OUTBOUND_IP_ARG}" | grep -oP '(?<=OUTBOUND_IP=).*' || echo "192.168.1.1")

LOG_FILE="${LOG_DIR}/BITGET-test-$(date +%Y%m%d_%H%M%S).log"

echo "=========================================="
echo "Starting BitgetFastClientProdTest"
echo "=========================================="
echo "Time: $(date)"
echo "JAR File: ${JAR_FILE}"
echo "Trade Parameters: ${TRADE_PARAMS}"
echo "Outbound IP: ${OUTBOUND_IP}"
echo "Log File: ${LOG_FILE}"
echo "=========================================="
echo ""

java -cp "${JAR_FILE}" \
    -Dcom.google.protobuf.use_unsafe_pre22_gencode=true \
    -DOUTBOUND_IP="${OUTBOUND_IP}" \
    "${MAIN_CLASS}" \
    "${TRADE_PARAMS}" \
    2>&1 | tee "${LOG_FILE}"

EXIT_CODE=$?

echo ""
echo "=========================================="
if [ ${EXIT_CODE} -eq 0 ]; then
    echo "✓ Test completed successfully at $(date)"
else
    echo "✗ Test failed with exit code: ${EXIT_CODE} at $(date)"
fi
echo "Log saved to: ${LOG_FILE}"
echo "=========================================="

exit ${EXIT_CODE}
