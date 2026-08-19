package com.solfini.util.blockchain;

import com.solfini.common.CustomLogger;
import com.solfini.util.blockchain.evm.EVMMembershipExecuteSender;
import com.solfini.util.blockchain.evm.EVMUserRegistrationTransactionSender;
import com.solfini.util.blockchain.evm.EVMWithdrawSender;
import com.solfini.util.blockchain.evm.EVMWithdrawableAmountUpdateSender;
import com.solfini.util.blockchain.evm.EVMPositionUpdateSender;
import com.solfini.util.blockchain.model.MembershipExecuteTransaction;
import com.solfini.util.blockchain.model.UserRegistrationTransaction;
import com.solfini.util.blockchain.model.WithdrawTransaction;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;
import com.solfini.util.blockchain.model.PositionUpdateTransaction;

import static com.solfini.common.Constants.*;

public class BlockchainSenderFactory {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BlockchainSenderFactory.class);

  public static BlockchainTransactionSender getSender(final PositionUpdateTransaction positionUpdateTransaction) {
    if (evmCompatible(positionUpdateTransaction.getChainType())) {
      LOGGER.info("Sender loading " + positionUpdateTransaction.getChainType());
      return new EVMPositionUpdateSender(positionUpdateTransaction);
    }

    return null;
  }

  public static BlockchainTransactionSender getSender(final WithdrawableAmountUpdateTransaction notionalUpdateTransaction) {
    if (evmCompatible(notionalUpdateTransaction.getChainType())) {
      LOGGER.info("Sender loading " + notionalUpdateTransaction.getChainType());
      return new EVMWithdrawableAmountUpdateSender(notionalUpdateTransaction);
    }

    return null;
  }

  public static BlockchainTransactionSender getSender(final WithdrawTransaction withdrawTransaction) {
    if (evmCompatible(withdrawTransaction.getChainType())) {
      LOGGER.info("Sender loading " + withdrawTransaction.getChainType());
      return new EVMWithdrawSender(withdrawTransaction);
    }

    return null;
  }

  public static BlockchainTransactionSender getSender(final MembershipExecuteTransaction membershipExecuteTransaction) {
    if (evmCompatible(membershipExecuteTransaction.getChainType())) {
      LOGGER.info("Sender loading " + membershipExecuteTransaction.getChainType());
      return new EVMMembershipExecuteSender(membershipExecuteTransaction);
    }

    return null;
  }

  public static BlockchainTransactionSender getSender(final UserRegistrationTransaction userRegistrationTransaction) {
    if (evmCompatible(userRegistrationTransaction.getChainType())) {
      LOGGER.info("Sender loading " + userRegistrationTransaction.getChainType());
      return new EVMUserRegistrationTransactionSender(userRegistrationTransaction);
    }

    return null;
  }

  private static boolean evmCompatible(final String chainType) {
    return (POLYGON.equalsIgnoreCase(chainType) || POLYGON_AMOY.equalsIgnoreCase(chainType)
        || MAINNET.equalsIgnoreCase(chainType) || ETHEREUM.equalsIgnoreCase(chainType) || SEPOLIA.equalsIgnoreCase(chainType)
        || XDC.equalsIgnoreCase(chainType) || XDC_APOTHEM.equalsIgnoreCase(chainType)
    );
  }
}
