package com.solfini.matchengine.drmode.message;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.orderbook.OrderBook;
import com.solfini.sbe.encoder.ExecType;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;

public class DRExecutionReport extends Message {

  protected int securityId;
  private String clOrdId;
  private String symbol;
  private Side side;
  private OrdType ordType;
  private int account;
  private int submitterId;
  private long orderId;
  private long secondaryOrderId;
  private long execId;
  private long secondaryExecId;

  private long feeQty;
  private short feeQtyScale;
  private long feeEstimatedQuantity;
  private long feeAccumulatedQuantity;
  private long availableEstimatedQuantity;
  private long availableAccumulatedQuantity;

  private TimeInForce timeInForce;
  private long expireTime;

  private long qtyInOrderbook;
  private int feeInstrumentId;
  private boolean isPaidToInsurance; // internal use only
  private int targetStrategy;
  private long assetId;
  private int tokenId;
  private long selectId;

  private ExecType execType;
  private OrdStatus ordStatus;

  public DRExecutionReport() {}

  @Override
  public PayloadType getPayloadType() {
    return PayloadType.orderEntry;
  }

  @Override
  public MessageType getMessageType() {
    return MessageType.EXECUTION_REPORT;
  }

  public DRExecutionReport(final ExecutionReportDecoder executionReportDecoder) {
    this.securityId = executionReportDecoder.securityId();

    this.clOrdId = executionReportDecoder.clOrdID();
    this.targetStrategy = executionReportDecoder.targetStrategy();

    this.ordType = executionReportDecoder.ordType();

    final int userId = executionReportDecoder.userId(); // TODO: check that user is set
    this.account = userId;
    this.submitterId = executionReportDecoder.submitterId();

    this.timeInForce = executionReportDecoder.timeInForce();

    this.expireTime = executionReportDecoder.expireTime();
    this.execType = executionReportDecoder.execType();
    this.ordStatus = executionReportDecoder.ordStatus();
    this.execId = executionReportDecoder.execId();
    this.orderId = executionReportDecoder.orderId();

    this.feeEstimatedQuantity = executionReportDecoder.feeEstimatedQuantity();
    this.feeAccumulatedQuantity = executionReportDecoder.feeAccumulatedQuantity();
    this.availableEstimatedQuantity = executionReportDecoder.availableEstimatedQuantity();
    this.availableAccumulatedQuantity = executionReportDecoder.availableAccumulatedQuantity();

    this.assetId = executionReportDecoder.assetId();
    this.tokenId = executionReportDecoder.tokenId();
    this.selectId = executionReportDecoder.selectId();
  }

  public final int getSecurityId() {
    return securityId;
  }

  public final void setSecurityId(final int securityId) {
    this.securityId = securityId;
  }

  public final String getClOrdId() {
    return clOrdId;
  }

  public final void setClOrdId(final String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final void setSymbol(final String symbol) {
    this.symbol = symbol;
  }

  public final Side getSide() {
    return side;
  }

  public final void setSide(final Side side) {
    this.side = side;
  }

  public final OrdType getOrdType() {
    return ordType;
  }

  public final void setOrdType(final OrdType ordType) {
    this.ordType = ordType;
  }

  public final int getAccount() {
    return account;
  }

  public final void setAccount(final int account) {
    this.account = account;
  }

  public final int getSubmitterId() {
    return submitterId;
  }

  public final void setSubmitterId(final int id) {
    this.submitterId = id;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getExecId() {
    return execId;
  }

  public final void setExecId(final long execId) {
    this.execId = execId;
  }

  public final TimeInForce getTimeInForce() {
    return timeInForce;
  }

  public final void setTimeInForce(final TimeInForce timeInForce) {
    this.timeInForce = timeInForce;
  }

  public final ExecType getExecType() {
    return execType;
  }

  public final void setExecType(final ExecType execType) {
    this.execType = execType;
  }

  public final OrdStatus getOrdStatus() {
    return ordStatus;
  }

  public final void setOrdStatus(final OrdStatus ordStatus) {
    this.ordStatus = ordStatus;
  }

  public final long getSecondaryOrderId() {
    return secondaryOrderId;
  }

  public final void setSecondaryOrderId(final long secondaryOrderId) {
    this.secondaryOrderId = secondaryOrderId;
  }

  public final long getSecondaryExecId() {
    return secondaryExecId;
  }

  public final void setSecondaryExecId(final long secondaryExecId) {
    this.secondaryExecId = secondaryExecId;
  }

  public final long getQtyInOrderbook() {
    return qtyInOrderbook;
  }

  public final void setQtyInOrderbook(final long qtyInOrderbook) {
    this.qtyInOrderbook = qtyInOrderbook;
  }

  public final long getFeeQty() {
    return feeQty;
  }

  public final short getFeeQtyScale() {
    return feeQtyScale;
  }

  public final void setFeeQty(final long feeQty, final short feeQtyScale) {
    this.feeQty = feeQty;
    this.feeQtyScale = feeQtyScale;
  }

  public final int getFeeInstrumentId() {
    return feeInstrumentId;
  }

  public final void setFeeInstrumentId(final int feeInstrumentId) {
    this.feeInstrumentId = feeInstrumentId;
  }

  public final boolean isPaidToInsurance() {
    return isPaidToInsurance;
  }

  public final void setPaidToInsurance(final boolean isPaidToInsurance) {
    this.isPaidToInsurance = isPaidToInsurance;
  }

  public final int getTargetStrategy() {
    return targetStrategy;
  }

  public final void setTargetStrategy(final int targetStrategy) {
    this.targetStrategy = targetStrategy;
  }

  public final long getFeeEstimatedQuantity() {
    return feeEstimatedQuantity;
  }

  public final void setFeeEstimatedQuantity(final long feeEstimatedQuantity) {
    this.feeEstimatedQuantity = feeEstimatedQuantity;
  }

  public final long getFeeAccumulatedQuantity() {
    return feeAccumulatedQuantity;
  }

  public final void setFeeAccumulatedQuantity(final long feeAccumulatedQuantity) {
    this.feeAccumulatedQuantity = feeAccumulatedQuantity;
  }

  public final long getAvailableEstimatedQuantity() {
    return availableEstimatedQuantity;
  }

  public final void setAvailableEstimatedQuantity(final long availableEstimatedQuantity) {
    this.availableEstimatedQuantity = availableEstimatedQuantity;
  }

  public final long getAvailableAccumulatedQuantity() {
    return availableAccumulatedQuantity;
  }

  public final void setAvailableAccumulatedQuantity(final long availableAccumulatedQuantity) {
    this.availableAccumulatedQuantity = availableAccumulatedQuantity;
  }

  public final long getAssetId() {
    return assetId;
  }

  public final void setAssetId(final long assetId) {
    this.assetId = assetId;
  }

  public final int getTokenId() {
    return tokenId;
  }

  public final void setTokenId(final int tokenId) {
    this.tokenId = tokenId;
  }

  public final long getSelectId() {
    return selectId;
  }

  public final void setSelectId(final long selectId) {
    this.selectId = selectId;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  public StringBuilder appendTo(final StringBuilder s) {
    return s.append(DREXECUTIONREPORT_SECURITYID_EQ).append(securityId).append(CLORID_EQ).append(clOrdId).append(SYMBOL_EQ).append(symbol)
        .append(SIDE_EQ).append(side).append(ORDTYPE_EQ).append(ordType).append(ACCOUNT_EQ).append(account).append(SUBMITTERID_EQ)
        .append(submitterId).append(ORDERID_EQ).append(orderId).append(SECONDARYORDERID_EQ).append(secondaryOrderId).append(EXECID_EQ)
        .append(execId).append(SECONDARYEXECID_EQ).append(secondaryExecId).append(FEEQTY_EQ).append(feeQty).append(TIMEINFORCE_EQ)
        .append(timeInForce).append(EXPIRETIME_EQ).append(expireTime).append(QTYINORDERBOOK_EQ).append(qtyInOrderbook)
        .append(FEEINSTRUMENTID_EQ).append(feeInstrumentId).append(ISPAIDTPINSURANCE_EQ).append(isPaidToInsurance).append(EXECTYPE_EQ)
        .append(execType).append(ORDSTATUS_EQ).append(ordStatus).append(SOURCESEQNUM_EQ).append(sourceSeqNum).append(SOURCESENDTIME_EQ)
        .append(sourceSendTime).append(TARGETSTRATEGY_EQ).append(FEEESTIMATEDQUANTITY_EQ).append(feeEstimatedQuantity)
        .append(FEEACCUMULATEDQUANTITY_EQ).append(feeAccumulatedQuantity).append(targetStrategy).append(AVAILABLEESTIMATEDQUANTITY_EQ)
        .append(availableEstimatedQuantity).append(AVAILABLEACCUMULATEDQUANTITY_EQ).append(availableAccumulatedQuantity).append(']');
  }

  @Override
  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"ExecutionReport\"").append(",\"sequenceNumber\":").append(sequenceNumber).append(",\"persistTime\":")
        .append(persistTime).append(",\"sourceSeqNum\":").append(sourceSeqNum).append(",\"sourceSendTime\":").append(sourceSendTime)
        .append(",\"qtyInOrderbook\":").append(qtyInOrderbook).append(",\"snapId\":").append(snapId).append(",\"kafkaRecordOffset\":")
        .append(kafkaRecordOffset);
    sb.append(",\"securityId\":").append(securityId).append(",\"side\":").append("\"").append(side).append("\"")
        .append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"clOrdId\":").append("\"").append(clOrdId).append("\"")
        .append("\"").append(",\"account\":").append("\"").append(account).append("\"").append(",\"submitterId\":").append(submitterId)
        .append(",\"userId\":").append(user.getId()).append(",\"ordType\":").append("\"").append(ordType).append("\"")
        .append(",\"symbol\":").append("\"").append(symbol).append("\"").append(",\"orderId\":").append(orderId)
        .append(",\"secondaryOrderId\":").append(secondaryOrderId).append(",\"execId\":").append(execId).append(",\"secondaryExecId\":")
        .append(secondaryExecId).append(",\"feeInstrumentId\":").append(feeInstrumentId).append(",\"feeEstimatedQuantity\":")
        .append(feeEstimatedQuantity).append(",\"feeAccumulatedQuantity\":").append(feeAccumulatedQuantity)
        .append(",\"availableEstimatedQuantity\":").append(availableEstimatedQuantity).append(",\"availableAccumulatedQuantity\":")
        .append(availableAccumulatedQuantity);

    if (feeQty != 0) {
      sb.append(",\"feeQty\":[").append(feeQty).append(",").append(feeQty).append("]");
    }
    sb.append(",\"timeInForce\":").append("\"").append(timeInForce).append("\"");
    sb.append(",\"expireTime\":").append(expireTime);
    sb.append(",\"execType\":").append("\"").append(execType).append("\"");
    sb.append(",\"ordStatus\":").append("\"").append(ordStatus).append("\"");
    sb.append(",\"targetStrategy\":").append("\"").append(targetStrategy).append("\"");
    sb.append(",\"assetId\":").append(assetId).append(",\"tokenId\":").append(tokenId).append(",\"selectId\":").append(selectId);
    sb.append("}");
    return sb.toString();
  }


  @Override
  public void onMatcher() {
    final InstrumentPair instrument = InstrumentCache.getPair(securityId);
    if (null != instrument) {
      final OrderBook orderbook = instrument.getOrderBook();
      orderbook.execReportDR(this);
    }
  }
}
