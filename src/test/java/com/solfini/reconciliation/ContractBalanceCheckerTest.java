package com.solfini.reconciliation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ContractBalanceCheckerTest {

  private static final double TOLERANCE = 0.05;

  @Test
  public void check_exactMatch_isMatched() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", 1500.0, 1500.0, TOLERANCE);

    assertTrue(result.matched());
    assertFalse(result.skipped());
  }

  @Test
  public void check_withinTolerance_isMatched() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", 1000.0, 970.0, TOLERANCE);

    assertTrue(result.matched());
  }

  @Test
  public void check_underCollateralized_beyondTolerance_isNotMatched() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", 1500.0, 1.0, TOLERANCE);

    assertFalse(result.matched());
    assertFalse(result.skipped());
  }

  @Test
  public void check_overCollateralized_beyondTolerance_isNotMatched() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", 1000.0, 2000.0, TOLERANCE);

    assertFalse(result.matched());
  }

  @Test
  public void check_zeroRequiredBacking_isSkipped() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDT", 0.0, 5.0, TOLERANCE);

    assertTrue(result.skipped());
    assertTrue(result.matched());
  }

  @Test
  public void check_negativeRequiredBacking_stillFlagsLargeMismatch() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", -1000.0, 1000.0, TOLERANCE);

    assertFalse(result.matched());
    assertFalse(result.skipped());
  }

  @Test
  public void formatTableHeader_containsColumnNames() {
    String header = ContractBalanceChecker.formatTableHeader();

    assertTrue(header.contains("Asset"));
    assertTrue(header.contains("User Notional"));
    assertTrue(header.contains("MM Notional"));
    assertTrue(header.contains("Required"));
    assertTrue(header.contains("Contract Bal"));
    assertTrue(header.contains("Diff"));
    assertTrue(header.contains("Status"));
  }

  @Test
  public void formatTableRow_matched_showsOkStatus() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", 1500.0, 1500.0, TOLERANCE);

    String row = ContractBalanceChecker.formatTableRow(1450.0, 50.0, result);

    assertTrue(row.contains("USDC"));
    assertTrue(row.contains("OK"));
    assertFalse(row.contains("[ALERT]"));
  }

  @Test
  public void formatTableRow_failed_showsAlertStatus() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDC", 1500.0, 1.0, TOLERANCE);

    String row = ContractBalanceChecker.formatTableRow(1450.0, 50.0, result);

    assertTrue(row.contains("[ALERT] FAILED"));
  }

  @Test
  public void formatTableRow_skipped_showsSkippedStatus() {
    AssetBalanceCheckResult result = ContractBalanceChecker.check("USDT", 0.0, 5.0, TOLERANCE);

    String row = ContractBalanceChecker.formatTableRow(0.0, 0.0, result);

    assertTrue(row.contains("SKIPPED"));
  }
}
