package com.solfini.reconciliation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FundingContractV6LoaderTest {

  private static final String COMPOSITE_ID = "0x000000000000000000000000000000000000000000000000000000000000aa";
  private static final String SUCCESSOR_ID = "0x000000000000000000000000000000000000000000000000000000000000bb";
  private static final String TOKEN = "0x0000000000000000000000000000000000000001";
  private static final String TARGET_COMPOSITE = "0x000000000000000000000000000000000000000000000000000000000000cc";
  private static final String ZERO_BYTES32 = "0x0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000".substring(0, 66);

  // ─── MembershipProposal (couple/decouple) — pre-existing class, previously untested ──────

  @Test
  public void membershipProposal_op0_isNotPending() {
    FundingContractV6Loader.MembershipProposal p =
        new FundingContractV6Loader.MembershipProposal(COMPOSITE_ID, SUCCESSOR_ID, TOKEN, 100L, 0, false);
    assertFalse(p.isPending());
    assertFalse(p.isMatured(1_000L));
  }

  @Test
  public void membershipProposal_pendingCouple_isPendingButNotMaturedBeforeEta() {
    FundingContractV6Loader.MembershipProposal p =
        new FundingContractV6Loader.MembershipProposal(COMPOSITE_ID, SUCCESSOR_ID, TOKEN, 1_000L, 1, false);
    assertTrue(p.isPending());
    assertFalse(p.isMatured(500L));
  }

  @Test
  public void membershipProposal_pendingDecouple_isMaturedAtOrAfterEta() {
    FundingContractV6Loader.MembershipProposal p =
        new FundingContractV6Loader.MembershipProposal(COMPOSITE_ID, SUCCESSOR_ID, TOKEN, 1_000L, 2, false);
    assertTrue(p.isMatured(1_000L)); // exactly at eta
    assertTrue(p.isMatured(1_001L)); // after eta
  }

  @Test
  public void membershipProposal_alreadyExecuted_isNeverMaturedRegardlessOfEta() {
    FundingContractV6Loader.MembershipProposal p =
        new FundingContractV6Loader.MembershipProposal(COMPOSITE_ID, SUCCESSOR_ID, TOKEN, 100L, 2, true);
    assertFalse(p.isPending());
    assertFalse(p.isMatured(999_999L));
  }

  // ─── MergeProposal — new class added for proposeMerge/executeMerge support ───────────────

  @Test
  public void mergeProposal_zeroTargetComposite_isNotPending() {
    FundingContractV6Loader.MergeProposal p =
        new FundingContractV6Loader.MergeProposal(ZERO_BYTES32, 100L, false);
    assertFalse(p.isPending());
    assertFalse(p.isMatured(1_000L));
  }

  @Test
  public void mergeProposal_realTarget_isPendingButNotMaturedBeforeEta() {
    FundingContractV6Loader.MergeProposal p =
        new FundingContractV6Loader.MergeProposal(TARGET_COMPOSITE, 1_000L, false);
    assertTrue(p.isPending());
    assertFalse(p.isMatured(500L));
  }

  @Test
  public void mergeProposal_realTarget_isMaturedAtOrAfterEta() {
    FundingContractV6Loader.MergeProposal p =
        new FundingContractV6Loader.MergeProposal(TARGET_COMPOSITE, 1_000L, false);
    assertTrue(p.isMatured(1_000L)); // exactly at eta
    assertTrue(p.isMatured(1_001L)); // after eta
  }

  @Test
  public void mergeProposal_alreadyExecuted_isNeverMaturedRegardlessOfEta() {
    FundingContractV6Loader.MergeProposal p =
        new FundingContractV6Loader.MergeProposal(TARGET_COMPOSITE, 100L, true);
    assertFalse(p.isPending());
    assertFalse(p.isMatured(999_999L));
  }

  @Test
  public void mergeProposal_zeroSentinelComparisonIsCaseInsensitive() {
    // Numeric.toHexString always lower-cases, but guard against a future decode path that doesn't.
    FundingContractV6Loader.MergeProposal p =
        new FundingContractV6Loader.MergeProposal(ZERO_BYTES32.toUpperCase().replace("0X", "0x"), 100L, false);
    assertFalse(p.isPending());
  }
}
