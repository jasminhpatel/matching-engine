package com.solfini.util.blockchain.model;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

import java.util.List;

public class PositionUpdateTransaction extends BlockchainTransaction implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PositionUpdateTransaction.class);
  private List<BlockchainPosition> userPositions;
  private int batchId;
  private int noOfBatches;

  public List<BlockchainPosition> getUserPositions() {
    return userPositions;
  }

  public void setUserPositions(List<BlockchainPosition> userPositions) {
    this.userPositions = userPositions;
  }

  public int getBatchId() {
    return batchId;
  }

  public void setBatchId(int batchId) {
    this.batchId = batchId;
  }

  public int getNoOfBatches() {
    return noOfBatches;
  }

  public void setNoOfBatches(int noOfBatches) {
    this.noOfBatches = noOfBatches;
  }

  public static class BlockchainPosition {
    private final int userId;
    private final int instrumentId;
    private final long quantity;

    public BlockchainPosition(int userId, int instrumentId, long quantity) {
      this.userId = userId;
      this.instrumentId = instrumentId;
      this.quantity = quantity;
    }

    public int getUserId() {
      return userId;
    }

    public int getInstrumentId() {
      return instrumentId;
    }

    public long getQuantity() {
      return quantity;
    }
  }
}
