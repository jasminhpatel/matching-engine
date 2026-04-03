package com.solfini.matchengine.message.admin;

import com.solfini.common.*;
import com.solfini.instrument.*;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.pool.BalanceAdminMessageObjectPool;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.FastArrayList;
import com.solfini.util.StringUtil;

import java.util.List;
import java.util.TreeSet;

import static com.solfini.instrument.Position.assetIdComparator;
import static com.solfini.matchengine.orderbook.GlobalOrderBook.incrementAndGetFilledCountGlobal;

public class StakeInterestCalcMessage extends AdminMessage {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(StakeInterestCalcMessage.class);
  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  private final List<StakeInterestRate> stakeInterestList = new FastArrayList<>();
  private long timestamp;

  public StakeInterestCalcMessage() {
    timestamp = System.currentTimeMillis();
  }

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.STAKE_INTEREST;
  }

  @Override
  public final void onMatcher() {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_2, ">>> calcStakeInterest timestamp=", timestamp);
    }

    applyStakeInterest();

    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(LOG_FMT_4, "<<< calcStakeInterest timestamp=", timestamp, ", now=", System.currentTimeMillis());
    }
  }

  @Override
  public String toJSON() {
    return "";
  }

  @Override
  public StringBuilder appendTo(StringBuilder s) {
    return null;
  }

  private void applyStakeInterest() {
    for (final StakeInterestRate stakeInterestRate : stakeInterestList) {
      final Instrument instrument = InstrumentCache.get(stakeInterestRate.getAssetId());
      if (instrument == null) {
        LOGGER.info(LOG_FMT_2, "Instrument not found. assetId: ", stakeInterestRate.getAssetId());
        continue;
      }
      final InstrumentPair pair = InstrumentCache.getPair(stakeInterestRate.getSecurityId());
      if (pair == null) {
        LOGGER.info(LOG_FMT_2, "InstrumentPair not found. assetId: ", stakeInterestRate.getSecurityId());
        continue;
      }
      final double interestRate = StringUtil.toDouble(stakeInterestRate.getRate());
      if (interestRate == 0)
        continue;

      if (LOGGER.isInfoEnabled()) {
        LOGGER.info(LOG_FMT_8, ">>> applyStakeInterest assetId=", stakeInterestRate.getAssetId(), " symbol=", instrument.getSymbol(),
            ", settle in =", instrument.getSymbol(), ", interestRate=", interestRate);
      }
      // for all users
      for (int userId = 0; userId <= UserCache.getCapacity(); userId++) {
        final User user = UserCache.get(userId);
        if (user == null || !user.isActive() || user.getPositionArr() == null)
          continue;

        final Position position = user.getPositionArr()[instrument.getId()];
        if (position == null || position.getQuantity() == 0)
          continue;

        final long change = (long) (position.getQuantity() * interestRate);

        if (LOGGER.isInfoEnabled()) {
          LOGGER.info(LOG_FMT_12, ">>> applyStakeInterest timestamp=", timestamp, ", userId=", user.getId(), ", symbol=", instrument.getSymbol(),
              QUANTITY_EQ, position.getQuantity(), ", interestRate=", interestRate, ", change=", change);
        }

        final BalanceAdminMessage message = BalanceAdminMessageObjectPool.get();
        message.setUpdateType(UpdateType.PATCH);
        message.setRequestStatus(RequestStatus.SUCCESS);
        message.setChecksum(0);
        message.setPersistTime(timestamp);
        message.setUserId(user.getId());
        message.setUser(user);
        message.setFirmId(user.getFirmId());
        message.setFeeTier(user.getFeeTier());
        message.setTxType(Constants.TX_ADMIN_DEPOSIT);
        message.setTxId(instrument.getId());
        message.setTriggerTimeMillis(triggerTimeMillis);
        message.setMatchTime(triggerTimeMillis);

        final Balance balance = new Balance();
        message.getBalanceList().add(balance);
        balance.setAssetId(instrument.getId());
        balance.setBalanceChange(change, instrument.getQuantityScale());
        balance.setTokenType(TokenType.ERC20);
        TreeSet<long[]> assetIdtreeSet = new TreeSet<>(assetIdComparator);
        long[] arrValue = new long[] {0, 0, 0};
        assetIdtreeSet.add(arrValue);
        balance.setAssetIdtreeSet(assetIdtreeSet);

        UserCache.addBalance(message);

        final User tokenManager = UserCache.getTokenManager();
        final BalanceAdminMessage counterMessage = BalanceAdminMessageObjectPool.get();
        counterMessage.setUpdateType(UpdateType.PATCH);
        counterMessage.setRequestStatus(RequestStatus.SUCCESS);
        counterMessage.setChecksum(0);
        counterMessage.setPersistTime(timestamp);
        counterMessage.setUserId(tokenManager.getId());
        counterMessage.setUser(tokenManager);
        counterMessage.setFirmId(tokenManager.getFirmId());
        counterMessage.setFeeTier(tokenManager.getFeeTier());
        counterMessage.setTxType(Constants.TX_ADMIN_DEPOSIT);
        counterMessage.setTxId(instrument.getId());
        counterMessage.setTriggerTimeMillis(triggerTimeMillis);
        counterMessage.setMatchTime(triggerTimeMillis);

        final Balance counterBalance = new Balance();
        counterMessage.getBalanceList().add(counterBalance);
        counterBalance.setAssetId(instrument.getId());
        counterBalance.setBalanceChange(-change, instrument.getQuantityScale());
        counterBalance.setTokenType(TokenType.ERC20);
        assetIdtreeSet = new TreeSet<>(assetIdComparator);
        arrValue = new long[] {0, 0, 0};
        assetIdtreeSet.add(arrValue);
        counterBalance.setAssetIdtreeSet(assetIdtreeSet);

        UserCache.addBalance(counterMessage);

        final ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createFundingExecutionReport(NewOrderSingleHandler.getNextOrderId(),
            user, pair.getId(), pair.getSymbol(), 1L, (short) 0, change, (short) instrument.getQuantityScale(), incrementAndGetFilledCountGlobal(),
            0, tokenManager.getId(), Constants.INTEREST);
        executionReportMessage.setKafkaRecordOffset(this.kafkaRecordOffset);
        user.copySetPositionArr(executionReportMessage);

        matcherToPublisherQueue.addGuaranteed(executionReportMessage);

        final ExecutionReportMessage counterExecutionReportMessage = ExecutionReportMessage.createFundingExecutionReport(NewOrderSingleHandler.getNextOrderId(),
            tokenManager, pair.getId(), pair.getSymbol(), 1L, (short) 0, -change, (short) instrument.getQuantityScale(),
            incrementAndGetFilledCountGlobal(), 0, user.getId(), Constants.INTEREST);
        counterExecutionReportMessage.setKafkaRecordOffset(this.kafkaRecordOffset);
        user.copySetPositionArr(counterExecutionReportMessage);

        matcherToPublisherQueue.addGuaranteed(counterExecutionReportMessage);

      }

      //Context.getMarketDataBuilderQueue().add(instrumentPair);
    }
  }

  public List<StakeInterestRate> getStakeInterestList() {
    return stakeInterestList;
  }
}
