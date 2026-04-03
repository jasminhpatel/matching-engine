package com.solfini.util.blockchain;

import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.evm.EVMWithdrawSender;
import com.solfini.util.blockchain.evm.EVMWithdrawableAmountUpdateSender;
import com.solfini.util.blockchain.evm.EVMPositionUpdateSender;
import com.solfini.util.blockchain.model.WithdrawTransaction;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import com.solfini.util.blockchain.model.PositionUpdateTransaction;

import static com.solfini.common.Constants.*;

public class BlockchainSenderFactory {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BlockchainSenderFactory.class);

  public static BlockchainTransactionSender getSender(final PositionUpdateTransaction positionUpdateTransaction) {
    if (POLYGON.equalsIgnoreCase(positionUpdateTransaction.getChainType()) || POLYGON_AMOY.equalsIgnoreCase(positionUpdateTransaction.getChainType())
        || ETHEREUM.equalsIgnoreCase(positionUpdateTransaction.getChainType()) || SEPOLIA.equalsIgnoreCase(positionUpdateTransaction.getChainType())) {
      LOGGER.info("Sender loading " + positionUpdateTransaction.getChainType());
      return new EVMPositionUpdateSender(positionUpdateTransaction);
    }

    return null;
  }

  public static BlockchainTransactionSender getSender(final WithdrawableAmountUpdateTransaction notionalUpdateTransaction) {
    if (POLYGON.equalsIgnoreCase(notionalUpdateTransaction.getChainType()) || POLYGON_AMOY.equalsIgnoreCase(notionalUpdateTransaction.getChainType())
        || ETHEREUM.equalsIgnoreCase(notionalUpdateTransaction.getChainType()) || SEPOLIA.equalsIgnoreCase(notionalUpdateTransaction.getChainType())) {
      LOGGER.info("Sender loading " + notionalUpdateTransaction.getChainType());
      return new EVMWithdrawableAmountUpdateSender(notionalUpdateTransaction);
    }

    return null;
  }

  public static BlockchainTransactionSender getSender(final WithdrawTransaction withdrawTransaction) {
    if (POLYGON.equalsIgnoreCase(withdrawTransaction.getChainType()) || POLYGON_AMOY.equalsIgnoreCase(withdrawTransaction.getChainType())
        || ETHEREUM.equalsIgnoreCase(withdrawTransaction.getChainType()) || SEPOLIA.equalsIgnoreCase(withdrawTransaction.getChainType())) {
      LOGGER.info("Sender loading " + withdrawTransaction.getChainType());
      return new EVMWithdrawSender(withdrawTransaction);
    }

    return null;
  }
}
