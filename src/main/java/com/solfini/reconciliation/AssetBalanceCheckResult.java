package com.solfini.reconciliation;

record AssetBalanceCheckResult(String assetName, double requiredBacking, double actualBalance,
    double changePercentage, boolean matched, boolean skipped, boolean over) {
}
