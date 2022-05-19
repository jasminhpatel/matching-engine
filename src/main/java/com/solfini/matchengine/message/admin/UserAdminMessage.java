package com.solfini.matchengine.message.admin;

import java.util.Arrays;
import java.util.List;

import com.solfini.common.AdminMessage;
import com.solfini.common.Context;
import com.solfini.common.MessageType;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder;
import com.solfini.internal.schema.PayloadType;
import com.solfini.user.UserCache;
import com.solfini.util.FastArrayList;

/**
 *
 * @author Chris Mack
 *
 */
public class UserAdminMessage extends AdminMessage {
  private UpdateType updateType;
  private int userId;
  private String username;
  private String password;
  private int firmId;
  private int feeTier;
  private RequestStatus requestStatus;
  private boolean lmm;
  private Position[] positionArr = new Position[Math.max(InstrumentCache.getPairCapacity(), 128)];
  private int positionsLength = 0;
  private int userType;
  private int status;
  private int accountType;
  private boolean useDiscountFeesCoin;
  private int patchType;
  private int marginCurveIdOverride;

  private final List<Balance> balanceList = new FastArrayList<>();

  public UserAdminMessage() {}

  public UserAdminMessage(final UserAdminMessageDecoder USER_DECODER, final long connectionId) {
    this.connectionId = connectionId;
    this.updateType = USER_DECODER.updateType();
    this.userId = USER_DECODER.userId();
    this.username = USER_DECODER.username();
    this.password = USER_DECODER.password();
    this.firmId = USER_DECODER.firmId();
    this.feeTier = USER_DECODER.feeTier();
    this.lmm = USER_DECODER.lmm() == 1; // boolean
    this.externalId = USER_DECODER.externalId();
    this.userType = USER_DECODER.userType();
    this.status = USER_DECODER.status();
    this.accountType = USER_DECODER.accountType();
    this.triggerTimeMillis = USER_DECODER.triggerTimeMillis();
    this.useDiscountFeesCoin = USER_DECODER.useDiscountFeesCoin() == 1; // boolean
    this.routeToDestination = USER_DECODER.routeToDestination();
    this.senderInstanceId = USER_DECODER.senderInstanceId();
    this.patchType = USER_DECODER.patchType();
    this.marginCurveIdOverride = USER_DECODER.marginCurveIdOverride();
  }

  @Override
  public final PayloadType getPayloadType() {
    return PayloadType.admin;
  }

  public final MessageType getMessageType() {
    return MessageType.USER_ADMIN;
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

  public final String getUsername() {
    return username;
  }

  public final void setUsername(final String username) {
    this.username = username;
  }

  public final String getPassword() {
    return password;
  }

  public final void setPassword(final String password) {
    this.password = password;
  }

  public final int getFirmId() {
    return firmId;
  }

  public final void setFirmId(final int firmId) {
    this.firmId = firmId;
  }

  public final List<Balance> getBalanceList() {
    return balanceList;
  }

  public boolean addBalance(final Balance balance) {
    return balanceList.add(balance);
  }

  public final RequestStatus getRequestStatus() {
    return requestStatus;
  }

  public final void setRequestStatus(final RequestStatus requestStatus) {
    this.requestStatus = requestStatus;
  }

  public final boolean isLmm() {
    return lmm;
  }

  public final void setLmm(final boolean lmm) {
    this.lmm = lmm;
  }

  public final boolean isUseDiscountFeesCoin() {
    return useDiscountFeesCoin;
  }

  public final void setUseDiscountFeesCoin(final boolean useDiscountFeesCoin) {
    this.useDiscountFeesCoin = useDiscountFeesCoin;
  }

  public final int getFeeTier() {
    return feeTier;
  }

  public final void setFeeTier(final int feeTier) {
    this.feeTier = feeTier;
  }

  public final Position[] getPositionArr() {
    return positionArr;
  }

  public final void setPositionArr(final Position[] positionArr) {
    this.positionArr = positionArr;
    this.positionsLength = positionArr.length;
  }

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

  public final int getStatus() {
    return status;
  }

  public final void setStatus(final int status) {
    this.status = status;
  }

  public final int getAccountType() {
    return accountType;
  }

  public final void setAccountType(final int accountType) {
    this.accountType = accountType;
  }

  public final int getPositionsLength() {
    return positionsLength;
  }

  public final void setPositionsLength(final int positionsLength) {
    this.positionsLength = positionsLength;
  }

  public final int getPatchType() {
    return patchType;
  }

  public final void setPatchType(final int patchType) {
    this.patchType = patchType;
  }

  public final int getMarginCurveIdOverride() {
    return marginCurveIdOverride;
  }

  public final void setMarginCurveIdOverride(final int marginCurveIdOverride) {
    this.marginCurveIdOverride = marginCurveIdOverride;
  }

  @Override
  public final void onMatcher() {
    UserCache.add(this);
  }

  @Override
  public final void onPublish() {
    Context.getMessagePublisher().publish(this);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    s.append(USERADMINMESSAGE_SENDERCOMPID_EQ).append(senderCompId).append(CONNECTIONID_EQ).append(connectionId)
        .append(TRIGGERTIMEMILLIS_EQ).append(triggerTimeMillis).append(USERTYPE_EQ).append(userType).append(UPDATETYPE_EQ)
        .append(updateType).append(USERID_EQ).append(userId).append(USERNAME_EQ).append(username).append(FIRMID_EQ).append(firmId)
        .append(FEETIER_EQ).append(feeTier).append(REQUESTSTATUS_EQ).append(requestStatus).append(STATUS_EQ).append(status)
        .append(MARGINCURVEIDOVERRIDE_EQ).append(marginCurveIdOverride).append(ACCOUNTTYPE_EQ).append(accountType).append(LMM_EQ)
        .append(lmm).append(ROUTETODESTINATION_EQ).append(routeToDestination).append(USEDISCOUNTFEESCOIN_EQ).append(useDiscountFeesCoin)
        .append(BALANCELIST_EQ).append(balanceList).append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ)
        .append(sourceSendTime).append(']');
    return s;
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"UserAdminMessage\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":").append(kafkaRecordOffset).append(",\"connectionId\":")
        .append(connectionId).append(",\"triggerTimeMillis\":").append(triggerTimeMillis).append(",\"externalId\":").append(externalId);
    sb.append(",\"routeToDestination\":").append("\"").append(routeToDestination).append("\"");
    sb.append(",\"updateType\":").append(updateType.value()).append(",\"userId\":").append(userId).append(",\"firmId\":").append(firmId)
        .append(",\"feeTier\":").append(feeTier).append(",\"requestStatus\":").append(requestStatus.value()).append(",\"status\":")
        .append(status).append(",\"accountType\":").append(accountType).append(",\"lmm\":").append(lmm)
        .append(",\"marginCurveIdOverride\":").append(marginCurveIdOverride).append(",\"useDiscountFeesCoin\":").append(useDiscountFeesCoin)
        .append(",\"userType\":").append(userType).append(",\"username\":").append("\"").append(username).append("\"");
    sb.append(",\"balanceList\":[");
    boolean comma = false;
    for (Balance balance : balanceList) {
      if (balance != null) {
        if (comma)
          sb.append(",");
        else
          comma = true;
        sb.append(balance.toJSON());
      }
    }
    sb.append("]}");
    return sb.toString();
  }

}
