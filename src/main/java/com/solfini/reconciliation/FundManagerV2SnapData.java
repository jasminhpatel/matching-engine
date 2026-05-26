package com.solfini.reconciliation;

import java.util.HashMap;
import java.util.Map;

public class FundManagerV2SnapData {
  private String snapshotId;
  private Map<Integer, Long> userPositionsMap = new HashMap<>();

  public String getSnapshotId() {
    return snapshotId;
  }

  public void setSnapshotId(String snapshotId) {
    this.snapshotId = snapshotId;
  }

  public Map<Integer, Long> getUserPositionsMap() {
    return userPositionsMap;
  }

}
