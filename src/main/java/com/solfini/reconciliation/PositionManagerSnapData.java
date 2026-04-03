package com.solfini.reconciliation;

import uk.co.real_logic.artio.fields.DecimalFloat;

import java.util.HashMap;
import java.util.Map;

public class PositionManagerSnapData {
  private String snapshotId;
  private Map<Integer, UserPositions> userPositionsMap = new HashMap<>();

  public String getSnapshotId() {
    return snapshotId;
  }

  public void setSnapshotId(String snapshotId) {
    this.snapshotId = snapshotId;
  }

  public Map<Integer, UserPositions> getUserPositionsMap() {
    return userPositionsMap;
  }

  public static class UserPositions {
    private final Map<Integer, Position> assetPositions = new HashMap<>();

    public Map<Integer, Position> getAssetPositions() {
      return assetPositions;
    }
  }

  public static class Position {
    private int assetId;
    private DecimalFloat balance;

    public int getAssetId() {
      return assetId;
    }

    public void setAssetId(int assetId) {
      this.assetId = assetId;
    }

    public DecimalFloat getBalance() {
      return balance;
    }

    public void setBalance(DecimalFloat balance) {
      this.balance = balance;
    }
  }
}
