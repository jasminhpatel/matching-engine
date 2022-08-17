package com.solfini.matchengine.message.admin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.MessageType;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder.BalanceGroupDecoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder.BalanceGroupDecoder.PositionsAssetIdGroupDecoder;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.user.UserCache;
import com.solfini.util.FastArrayList;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;
import static com.solfini.instrument.Position.assetIdComparator;

/**
 *
 * @author Chris Mack
 *
 */
public class BalanceAdminMessage extends AdminMessage {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BalanceAdminMessage.class);

  private UpdateType updateType;
  private int userId;
  private int firmId;
  private int feeTier;
  private RequestStatus requestStatus;
  private long checksum;
  private int txType;
  private int txId;
  private Position[] positionArr = new Position[Math.max(InstrumentCache.getPairCapacity(), 64)];
  private int positionsLength = 0;
  private int userType;
  private int balanceTransferToUserId = 0;

  private final List<Balance> balanceList = new FastArrayList<>();
  private final OneToOneConcurrentArrayQueueCustom<BalanceAdminMessage> pool;


  private Balance[] balanceCacheArr = buildBalanceCacheArr();

  private final Balance[] buildBalanceCacheArr() {
    final Balance[] balanceCacheArrTemp = new Balance[Math.max(InstrumentCache.getPairCapacity(), 64)];
    for (int i = 0; i < balanceCacheArrTemp.length; i++) {
      balanceCacheArrTemp[i] = new Balance();
    }
    return balanceCacheArrTemp;
  }

  private int balanceCacheArrIndex = 0;

  public final Balance getCachedBalance() {

    // Resize as necessary
    if (balanceCacheArr.length <= balanceCacheArrIndex) {
      final int prevLength = balanceCacheArr.length;
      balanceCacheArr = Arrays.copyOf(balanceCacheArr, prevLength + 32);
      for (int i = prevLength; i < balanceCacheArr.length; ++i) {
        balanceCacheArr[i] = new Balance();
      }
    }

    final Balance balance = balanceCacheArr[balanceCacheArrIndex];
    balanceCacheArrIndex++;
    return balance;
  }

  public BalanceAdminMessage() {
    this.pool = null;
  }

  public BalanceAdminMessage(final OneToOneConcurrentArrayQueueCustom<BalanceAdminMessage> pool) {
    this.pool = pool;
  }

  public BalanceAdminMessage(final BalanceAdminMessageDecoder BALANCE_DECODER, final long connectionId) {
    this.pool = null;
    this.connectionId = connectionId;
    this.updateType = BALANCE_DECODER.updateType();
    this.userId = BALANCE_DECODER.userId();
    this.firmId = BALANCE_DECODER.firmId();
    this.feeTier = BALANCE_DECODER.feeTier();
    this.txType = BALANCE_DECODER.txType();
    this.txId = BALANCE_DECODER.txId();
    this.externalId = BALANCE_DECODER.externalId();
    this.userType = BALANCE_DECODER.userType();
    this.triggerTimeMillis = BALANCE_DECODER.triggerTimeMillis();
    this.routeToDestination = BALANCE_DECODER.routeToDestination();
    this.senderInstanceId = BALANCE_DECODER.senderInstanceId();
    this.balanceTransferToUserId = BALANCE_DECODER.balanceTransferToUserId();

    user = UserCache.get(userId);
    if (LOGGER.isTraceEnabled()) {
      LOGGER.debug(LOG_FMT_8, "BalanceAdminMessage userId=", userId, UPDATETYPE_EQ, updateType != null ? updateType.toString() : "",
          TXTYPE_EQ, txType, TXID_EQ, txId, BALANCETRANSFERTOUSERID_EQ, balanceTransferToUserId);
    }
    if (userId == 0) {
      LOGGER.error(LOG_FMT_8, "error BalanceAdminMessage userId=", userId, UPDATETYPE_EQ, updateType != null ? updateType.toString() : "",
          TXTYPE_EQ, txType, TXID_EQ, txId, BALANCETRANSFERTOUSERID_EQ, balanceTransferToUserId);
    }

    for (final BalanceGroupDecoder balanceGroupDecoder : BALANCE_DECODER.balanceGroup()) {
      final int assetId = balanceGroupDecoder.assetId();
      final long value = balanceGroupDecoder.balance().value();
      final int scale = balanceGroupDecoder.balance().scale();
      Set<long[]> assetIdtreeSet = null;
      for (BalanceGroupDecoder.PositionsAssetIdGroupDecoder assetIdGroupDecoder : balanceGroupDecoder.positionsAssetIdGroup()) {
        if (assetIdtreeSet == null)
          assetIdtreeSet = new TreeSet<>(assetIdComparator);

        final long assetId2 = assetIdGroupDecoder.assetId();
        final int tokenId = assetIdGroupDecoder.tokenId();
        final long groupAssetId = assetIdGroupDecoder.groupAssetId();
        final long[] arrvalue = new long[] {assetId2, tokenId, groupAssetId};
        assetIdtreeSet.add(arrvalue);
      }

      if (BALANCE_DECODER.updateType() == UpdateType.PUT) {
        addBalance(new Balance(assetId, value, scale, 0, 0, assetIdtreeSet));
      } else if (BALANCE_DECODER.updateType() == UpdateType.PATCH) {
        addBalance(new Balance(assetId, 0, 0, value, scale, assetIdtreeSet));
      }
    }
  }

  public BalanceAdminMessage(final BalanceAdminMessage source, final OneToOneConcurrentArrayQueueCustom<BalanceAdminMessage> pool) {
    this.pool = pool;
    this.connectionId = source.connectionId;
    this.updateType = source.updateType;
    this.userId = source.userId;
    this.firmId = source.firmId;
    this.feeTier = source.feeTier;
    this.txType = source.txType;
    this.txId = source.txId;
    this.externalId = source.externalId;
    this.userType = source.userType;
    this.triggerTimeMillis = source.triggerTimeMillis;
    this.routeToDestination = source.routeToDestination;
    this.senderInstanceId = source.senderInstanceId;
    this.requestStatus = source.requestStatus;
    this.positionsLength = source.positionsLength;
    this.balanceTransferToUserId = source.balanceTransferToUserId;
    if (source.positionArr != null)
      this.positionArr = Arrays.copyOf(source.positionArr, source.positionArr.length);

    user = UserCache.get(userId);
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_8, "BalanceAdminMessage userId=", userId, UPDATETYPE_EQ, updateType != null ? updateType.toString() : "",
          TXTYPE_EQ, txType, TXID_EQ, txId);
    }
    if (userId == 0) {
      LOGGER.error(LOG_FMT_8, "error2 BalanceAdminMessage userId=", userId, UPDATETYPE_EQ, updateType != null ? updateType.toString() : "",
          TXTYPE_EQ, txType, TXID_EQ, txId, BALANCETRANSFERTOUSERID_EQ, balanceTransferToUserId);
    }

    for (final Balance balance : source.balanceList) {
      if (balance == null)
        continue;

      addBalance(new Balance(balance.getAssetId(), balance.getBalance().value(), balance.getBalance().scale(),
          balance.getBalanceChange().value(), balance.getBalanceChange().scale(), balance.getAssetIdtreeSet()));
    }
  }

  // for transferring from one user to another
  // the first use should be withdrawing
  public void inverseBalancesForTransfer() {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(LOG_FMT_8, "inverseBalancesForTransfer userId=", userId, UPDATETYPE_EQ, updateType != null ? updateType.toString() : "",
          TXTYPE_EQ, txType, TXID_EQ, txId);
    }
    this.user = UserCache.get(userId);
    this.balanceTransferToUserId = 0;

    for (final Balance balance : balanceList) {
      if (balance == null)
        continue;

      final DecimalFloat balanceDecimalFloat = balance.getBalance();
      balanceDecimalFloat.value(-balanceDecimalFloat.value());

      final DecimalFloat balanceChangeDecimalFloat = balance.getBalance();
      balanceChangeDecimalFloat.value(-balanceChangeDecimalFloat.value());
    }
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.BALANCE_ADMIN;
  }

  public final UpdateType getUpdateType() {
    return updateType;
  }

  public final void setUpdateType(final UpdateType updateType) {
    this.updateType = updateType;
  }

  public final int getUserId() {
    return userId;
  }

  public final void setUserId(final int userId) {
    this.userId = userId;
  }

  public final int getFirmId() {
    return firmId;
  }

  public final void setFirmId(final int firmId) {
    this.firmId = firmId;
  }

  public final RequestStatus getRequestStatus() {
    return requestStatus;
  }

  public final void setRequestStatus(final RequestStatus requestStatus) {
    this.requestStatus = requestStatus;
  }

  public final List<Balance> getBalanceList() {
    return balanceList;
  }

  public final boolean addBalance(final Balance balance) {
    return balanceList.add(balance);
  }

  public final int getFeeTier() {
    return feeTier;
  }

  public final void setFeeTier(final int feeTier) {
    this.feeTier = feeTier;
  }

  public final long getChecksum() {
    return checksum;
  }

  public final void setChecksum(final long checksum) {
    this.checksum = checksum;
  }

  public final int getTxType() {
    return txType;
  }

  public final void setTxType(final int txType) {
    this.txType = txType;
  }

  public final int getTxId() {
    return txId;
  }

  public final void setTxId(final int txId) {
    this.txId = txId;
  }

  public final Position[] getPositionArr() {
    return positionArr;
  }

  public final void setPositionArr(final Position[] positionArr) {
    this.positionArr = positionArr;
    this.positionsLength = positionArr.length;
  }

  // Make sure that the position array contains the length size.
  public final Position[] reservePositionArrSize(final int length) {
    if (this.positionArr.length < length) {
      setPositionArr(Arrays.copyOf(this.positionArr, length));
    }

    return positionArr;
  }

  public final int getUserType() {
    return userType;
  }

  public final void setUserType(final int userType) {
    this.userType = userType;
  }

  public final int getBalanceTransferToUserId() {
    return balanceTransferToUserId;
  }

  public final void setBalanceTransferToUserId(final int balanceTransferToUserId) {
    this.balanceTransferToUserId = balanceTransferToUserId;
  }

  public final int getPositionsLength() {
    return positionsLength;
  }

  public final void setPositionsLength(final int positionsLength) {
    this.positionsLength = positionsLength;
  }

  public final OneToOneConcurrentArrayQueueCustom<BalanceAdminMessage> getPool() {
    return pool;
  }



  // called from decoder
  public final void setAccount(final char[] account, final int accountLength) {
    try {
      userId = StringUtil.charArrayToInt(account, accountLength) - 1_000_000_000;
      user = UserCache.get(userId);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public final void setAccount(final int userId) {
    try {
      this.userId = userId;
      user = UserCache.get(userId);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  // called from decoder
  private Balance balanceTemp = null;

  public final void addBalance(final int[] assetIdArr, final DecimalFloat quantity) {
    final int securityId = assetIdArr[0];
    final int fieldIndex = assetIdArr[1];

    if ((securityId > 0) && (InstrumentCache.getPair(securityId) != null) || InstrumentCache.get(securityId) != null) {
      switch (fieldIndex) {
        case 0:
          balanceTemp = getCachedBalance();
          balanceTemp.setBalanceChange(0, 0);

          long quantityLong = quantity.value();
          final InstrumentPair pair = InstrumentCache.getPair(securityId);
          if (pair != null) {
            balanceTemp.setAssetId(pair.getId());
            for (int i = 0; i < pair.getQuantityScale() - quantity.scale(); i++)
              quantityLong *= 10;

            balanceTemp.setBalance(quantityLong, pair.getQuantityScale());
          } else {
            final Instrument instrument = InstrumentCache.get(securityId);
            if (instrument != null) {
              balanceTemp.setAssetId(instrument.getId());
              for (int i = 0; i < instrument.getQuantityScale() - quantity.scale(); i++)
                quantityLong *= 10;

              balanceTemp.setBalance(quantityLong, instrument.getQuantityScale());
            }
          }
          break;
        case 2:
          balanceTemp.setUsdCostBasis(StringUtil.toDouble(quantity));
          break;
        case 3:
          balanceTemp.setUsdAvgCostBasis(StringUtil.toDouble(quantity));
          break;
        case 4:
          balanceTemp.setUsdValue(StringUtil.toDouble(quantity));
          break;
        case 5:
          balanceTemp.setUsdUnrealized(StringUtil.toDouble(quantity));
          break;
        case 6:
          balanceTemp.setUsdRealized(StringUtil.toDouble(quantity));
          break;
        case 7:
          balanceTemp.setQuotedUsdMark(StringUtil.toDouble(quantity));
          break;
        case 8:
          balanceTemp.setSettleCoinUsdMark(StringUtil.toDouble(quantity));
          break;
        case 9:
          balanceTemp.setSettleCoinUnrealized(StringUtil.toDouble(quantity));
          break;
        case 10:
          balanceTemp.setSettleCoinRealized(StringUtil.toDouble(quantity));
          balanceList.add(balanceTemp);
          break;
        default:
      }
    }
  }



  @Override
  public void onMatcher() {
    UserCache.addBalance(this);
  }

  @Override
  public void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public void clear() {
    super.clear();
    balanceCacheArrIndex = 0;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(BALANCEADMINMESSAGE_SENDERCOMPID_EQ).append(senderCompId).append(CONNECTIONID_EQ).append(connectionId)
        .append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis).append(USERTYPE_EQ).append(userType).append(UPDATETYPE_EQ)
        .append(updateType).append(USERID_EQ).append(userId).append(FIRMID_EQ).append(firmId).append(TXTYPE_EQ).append(txType)
        .append(TXID_EQ).append(txId).append(FEETIER_EQ).append(feeTier).append(REQUESTSTATUS_EQ).append(requestStatus)
        .append(ROUTETODESTINATION_EQ).append(routeToDestination).append(BALANCELIST_EQ).append(Arrays.toString(balanceList.toArray()))
        .append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(SOURCESEQNUM_EQ)
        .append(sourceSeqNum).append(SOURCESENDTIME_EQ).append(sourceSendTime).append(BALANCETRANSFERTOUSERID_EQ)
        .append(balanceTransferToUserId).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"BalanceAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId)
        .append(",\"feeTier\":").append(feeTier).append(",\"requestStatus\":")
        .append(requestStatus == null ? "" : String.valueOf(requestStatus.value())).append(",\"updateType\":")
        .append(updateType == null ? "" : String.valueOf(updateType.value())).append(",\"userId\":").append(userId).append(",\"firmId\":")
        .append(firmId).append(",\"checksum\":").append(checksum).append(",\"txType\":").append(txType).append(",\"txId\":").append(txId)
        .append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"").append(",\"userType\":").append(userType)
        .append(",\"positionArr\":[");
    if (positionArr != null) {
      boolean comma = false;
      for (int i = 0; i < positionArr.length; i++) {
        if (positionArr[i] != null) {
          if (comma)
            sb.append(",");
          else
            comma = true;
          sb.append(positionArr[i].toJSON());
        }
      }
    }
    sb.append("]}");
    return sb.toString();
  }
}
