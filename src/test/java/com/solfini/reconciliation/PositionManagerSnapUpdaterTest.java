package com.solfini.reconciliation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PositionManagerSnapUpdaterTest {

  @Test
  public void missedSyncCycle_matchesExpectedPrevious_isNotStale() {
    assertFalse(PositionManagerSnapUpdater.missedSyncCycle(100L, 100L));
  }

  @Test
  public void missedSyncCycle_olderThanExpectedPrevious_isStale() {
    assertTrue(PositionManagerSnapUpdater.missedSyncCycle(80L, 100L));
  }

  @Test
  public void missedSyncCycle_neverSynced_isNotFlaggedAsStale() {
    // snapshotId 0 means this row has never been synced under this tracking (brand new position),
    // not a recovery from a missed cycle.
    assertFalse(PositionManagerSnapUpdater.missedSyncCycle(0L, 100L));
  }

  @Test
  public void missedSyncCycle_noPreviousFolderAvailable_isNotFlaggedAsStale() {
    // expectedPrevSnapshotId 0 means we couldn't determine a previous folder (e.g. the very first
    // run) - nothing to compare against, so don't flag.
    assertFalse(PositionManagerSnapUpdater.missedSyncCycle(100L, 0L));
  }
}
