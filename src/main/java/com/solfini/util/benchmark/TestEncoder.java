package com.solfini.util.benchmark;

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AdminAcknowledgementEncoder;
import com.solfini.internal.admin.schema.BalanceAdminMessageEncoder;
import com.solfini.internal.admin.schema.DecimalFloatEncoder;
import com.solfini.internal.admin.schema.FIXUserAdminMessageEncoder;
import com.solfini.internal.admin.schema.FeeAdminMessageEncoder;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageEncoder;
import com.solfini.internal.admin.schema.GlobalStateAdminMessageEncoder;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageEncoder;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageEncoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder;
import com.solfini.matchengine.message.admin.AckAdminMessage;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.message.admin.GlobalStateAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.session.HeartbeatMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.matchengine.message.session.ResendRequestMessage;
import com.solfini.matchengine.message.session.SequenceResetMessage;
import com.solfini.matchengine.publisher.LogonEncoderCache;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.CancelOrderEncoder;
import com.solfini.sbe.encoder.CancelReplaceOrderEncoder;
import com.solfini.sbe.encoder.HeartbeatEncoder;
import com.solfini.sbe.encoder.LogonEncoder;
import com.solfini.sbe.encoder.MassCancelOrderEncoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.ResendRequestEncoder;
import com.solfini.sbe.encoder.SequenceResetEncoder;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;

public class TestEncoder {
  private static final int KAFKA_OFFSET = 17;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  public static final String DEFAULT_SENDER_COMP_ID = "test_encoder_sender";
  private static final short HEADER_LENGTH = 2;

  private static int msgSeqNum = 0;

  private TestEncoder() {
    // hidden default constructor
  }

  public static byte[] encodeNewUser(User user) {
    short encodedLength = HEADER_LENGTH;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final UserAdminMessageEncoder userAdminMessageEncoder = new UserAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();

    userAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    userAdminMessageEncoder.updateType(UpdateType.PUT);
    userAdminMessageEncoder.username(user.getLogin());
    userAdminMessageEncoder.userId(user.getId());
    userAdminMessageEncoder.password(user.getPassword());
    userAdminMessageEncoder.feeTier(user.getFeeTier());
    userAdminMessageEncoder.firmId(user.getFirmId());
    userAdminMessageEncoder.lmm((short) (user.isLmm() ? 0 : 1));

    UserAdminMessageEncoder.BalanceGroupEncoder balanceGroupEncoder = userAdminMessageEncoder.balanceGroupCount(3);
    balanceGroupEncoder.next().assetId(0).balance().value(0).scale(2);
    balanceGroupEncoder.next().assetId(1).balance().value(0).scale(2);
    balanceGroupEncoder.next().assetId(2).balance().value(0).scale(2);

    encodedLength += userAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeBalance(final User user, final long quantity, final int quantityScale, final int securityId) {
    short encodedLength = HEADER_LENGTH;
    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    BalanceAdminMessageEncoder balanceAdminMessageEncoder = new BalanceAdminMessageEncoder();
    com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    balanceAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    balanceAdminMessageEncoder.updateType(UpdateType.PUT);
    balanceAdminMessageEncoder.userId(user.getId());
    balanceAdminMessageEncoder.feeTier(user.getFeeTier());
    balanceAdminMessageEncoder.txType(Constants.TX_ADJUSTMENT);
    balanceAdminMessageEncoder.txId(1);

    com.solfini.internal.admin.schema.BalanceAdminMessageEncoder.BalanceGroupEncoder balanceGroupEncoder =
        balanceAdminMessageEncoder.balanceGroupCount(1);
    balanceGroupEncoder.next().assetId(securityId).balance().value(quantity).scale(quantityScale);

    encodedLength += balanceAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeStopLimitOrder(final User user, final InstrumentPair instrument, Side side, long price, long stopPrice,
      long qty, String orderId, TimeInForce timeInForce) {
    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    newOrderSingleEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    newOrderSingleEncoder.userId(user.getId());
    newOrderSingleEncoder.securityId(instrument.getId());
    newOrderSingleEncoder.side(side);
    newOrderSingleEncoder.ordType(OrdType.STOP_LIMIT);
    newOrderSingleEncoder.stopPx(stopPrice);
    newOrderSingleEncoder.stopPxScale((short) 2);
    newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
    newOrderSingleEncoder.price(price);
    newOrderSingleEncoder.priceScale((short) 2);
    newOrderSingleEncoder.qty(qty);
    newOrderSingleEncoder.qtyScale((short) 2);
    newOrderSingleEncoder.clOrdID(orderId);
    newOrderSingleEncoder.expireTime(TimeUtil.getTime());

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeNewOrder(final User user, final InstrumentPair instrument, final Side side, final OrdType orderType,
      final long price, long qty, final String clOrdID, final TimeInForce timeInForce) {
    short encodedLength = HEADER_LENGTH;
    ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
    UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

    NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();
    com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
    newOrderSingleEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

    headerEncoder.senderCompId("test");
    headerEncoder.sendingTime(TimeUtil.getTime());
    headerEncoder.msgSeqNum(msgSeqNum++);
    encodedLength += headerEncoder.encodedLength();

    newOrderSingleEncoder.userId(user.getId());
    newOrderSingleEncoder.securityId(instrument.getId());
    newOrderSingleEncoder.side(side);
    newOrderSingleEncoder.ordType(orderType);
    newOrderSingleEncoder.timeInForce(timeInForce); // TimeInForce.GOOD_TILL_CANCEL
    newOrderSingleEncoder.price(price);
    newOrderSingleEncoder.priceScale((short) 2);
    newOrderSingleEncoder.qty(qty);
    newOrderSingleEncoder.qtyScale((short) 2);
    newOrderSingleEncoder.clOrdID(clOrdID);
    newOrderSingleEncoder.expireTime(TimeUtil.getTime());

    encodedLength += newOrderSingleEncoder.encodedLength();
    buffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeSecurityDefinition(final SecurityDefinitionAdminMessage securityDefinitionAdminMessage) {
    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final SecurityDefinitionAdminMessageEncoder securityDefinitionAdminMessageEncoder = new SecurityDefinitionAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    securityDefinitionAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    securityDefinitionAdminMessageEncoder.updateType(securityDefinitionAdminMessage.getUpdateType());
    securityDefinitionAdminMessageEncoder.securityId(securityDefinitionAdminMessage.getSecurityId());
    securityDefinitionAdminMessageEncoder.assetType(securityDefinitionAdminMessage.getAssetType());
    securityDefinitionAdminMessageEncoder.baseId(securityDefinitionAdminMessage.getBaseId());
    securityDefinitionAdminMessageEncoder.quotedId(securityDefinitionAdminMessage.getQuotedId());
    securityDefinitionAdminMessageEncoder.priceScale((short) securityDefinitionAdminMessage.getPriceScale());
    securityDefinitionAdminMessageEncoder.quantityScale((short) securityDefinitionAdminMessage.getQuantityScale());
    securityDefinitionAdminMessageEncoder.orderBookStrategy((short) securityDefinitionAdminMessage.getOrderBookStrategy());
    securityDefinitionAdminMessageEncoder.preOrderCheckStrategy((short) securityDefinitionAdminMessage.getPreOrderCheckStrategy());
    securityDefinitionAdminMessageEncoder.symbol(securityDefinitionAdminMessage.getSymbol());
    securityDefinitionAdminMessageEncoder.name(securityDefinitionAdminMessage.getName());
    securityDefinitionAdminMessageEncoder.settleType(securityDefinitionAdminMessage.getSettleType());
    securityDefinitionAdminMessageEncoder.maintMarginPercent(securityDefinitionAdminMessage.getMaintMarginBasisPoints());
    securityDefinitionAdminMessageEncoder.requiredMarginPercent(securityDefinitionAdminMessage.getRequiredMarginBasisPoints());
    securityDefinitionAdminMessageEncoder.minQty(securityDefinitionAdminMessage.getMinQty());
    securityDefinitionAdminMessageEncoder.maxQty(securityDefinitionAdminMessage.getMaxQty());
    securityDefinitionAdminMessageEncoder.maxPrice(securityDefinitionAdminMessage.getMaxPrice());
    securityDefinitionAdminMessageEncoder.supportOrderType(securityDefinitionAdminMessage.getSupportOrderType());
    securityDefinitionAdminMessageEncoder.commissionType(securityDefinitionAdminMessage.getCommissionType());
    securityDefinitionAdminMessageEncoder.triggerTimeMillis(securityDefinitionAdminMessage.getTriggerTimeMillis());
    securityDefinitionAdminMessageEncoder.routeToDestination(securityDefinitionAdminMessage.getRouteToDestination());


    final DecimalFloatEncoder indexFeedUsdMarkEncoder = securityDefinitionAdminMessageEncoder.indexFeedUsdMark();
    indexFeedUsdMarkEncoder.value((long) (securityDefinitionAdminMessage.getIndexFeedUsdMark() * 100));
    indexFeedUsdMarkEncoder.scale(2);

    securityDefinitionAdminMessageEncoder.externalId(securityDefinitionAdminMessage.getExternalId());
    securityDefinitionAdminMessageEncoder.sourceSeqNum(securityDefinitionAdminMessage.getSourceSeqNum()); // sourceSeqNum
    securityDefinitionAdminMessageEncoder.kafkaRecordOffset(securityDefinitionAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset

    encodedLength += securityDefinitionAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeFeeAdminMessage(FeeAdminMessage feeAdminMessage) {

    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final FeeAdminMessageEncoder feeAdminMessageEncoder = new FeeAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    feeAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    feeAdminMessageEncoder.updateType(feeAdminMessage.getUpdateType());
    feeAdminMessageEncoder.assetId(feeAdminMessage.getAssetId());
    feeAdminMessageEncoder.tier(feeAdminMessage.getTier());
    feeAdminMessageEncoder.assetId(feeAdminMessage.getAssetId());
    feeAdminMessageEncoder.fee(feeAdminMessage.getFee());
    feeAdminMessageEncoder.feeType(feeAdminMessage.getFeeType());
    feeAdminMessageEncoder.makerTaker(feeAdminMessage.getMakerTaker());

    feeAdminMessageEncoder.externalId(feeAdminMessage.getExternalId());
    feeAdminMessageEncoder.sourceSeqNum(feeAdminMessage.getSourceSeqNum()); // sourceSeqNum
    feeAdminMessageEncoder.kafkaRecordOffset(feeAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset
    feeAdminMessageEncoder.triggerTimeMillis(feeAdminMessage.getTriggerTimeMillis());
    feeAdminMessageEncoder.routeToDestination(feeAdminMessage.getRouteToDestination());

    encodedLength += feeAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeFundingRateCalcAdmin(FundingRateCalcMessage fundingRateCalcMessage, InstrumentPair instrument,
      int markInSettleCoin, int rate) {

    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final FundingRateCalcAdminMessageEncoder fundingRateCalcAdminMessageEncoder = new FundingRateCalcAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    fundingRateCalcAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    fundingRateCalcAdminMessageEncoder.updateType(fundingRateCalcMessage.getUpdateType());
    fundingRateCalcAdminMessageEncoder.assetId(instrument.getId());
    fundingRateCalcAdminMessageEncoder.txId(fundingRateCalcMessage.getTxId());
    fundingRateCalcAdminMessageEncoder.routeToDestination(fundingRateCalcMessage.getRouteToDestination());
    fundingRateCalcAdminMessageEncoder.markInSettleCoin().scale(2);
    fundingRateCalcAdminMessageEncoder.markInSettleCoin().value(markInSettleCoin);
    fundingRateCalcAdminMessageEncoder.rate().scale(2);
    fundingRateCalcAdminMessageEncoder.rate().value(rate);

    fundingRateCalcAdminMessageEncoder.externalId(fundingRateCalcMessage.getExternalId());
    fundingRateCalcAdminMessageEncoder.sourceSeqNum(fundingRateCalcMessage.getSourceSeqNum()); // sourceSeqNum
    fundingRateCalcAdminMessageEncoder.kafkaRecordOffset(fundingRateCalcMessage.getKafkaRecordOffset()); // kafkaRecordOffset
    fundingRateCalcAdminMessageEncoder.triggerTimeMillis(fundingRateCalcMessage.getTriggerTimeMillis());
    fundingRateCalcAdminMessageEncoder.routeToDestination(fundingRateCalcMessage.getRouteToDestination());
    fundingRateCalcAdminMessageEncoder.triggerTimeMillis(fundingRateCalcMessage.getTriggerTimeMillis());

    encodedLength += fundingRateCalcAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeGlobalStateAdmin(GlobalStateAdminMessage message) {

    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final GlobalStateAdminMessageEncoder globalStateAdminMessageEncoder = new GlobalStateAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    globalStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    globalStateAdminMessageEncoder.snapLoaderMode(message.getSnapLoaderMode());
    globalStateAdminMessageEncoder.useInsurance(message.getUseInsurance());
    globalStateAdminMessageEncoder.liquidationMode(message.getLiquidationMode());
    globalStateAdminMessageEncoder.insurancePositonPercentLimit(message.getInsurancePositonPercentLimit());
    globalStateAdminMessageEncoder.insuranceLossPercentLimit(message.getInsuranceLossPercentLimit());
    globalStateAdminMessageEncoder.insuranceAutoCloseMode(message.getInsuranceAutoCloseMode());
    globalStateAdminMessageEncoder.publishSequenceNumber(message.getPublishSequenceNumber());
    globalStateAdminMessageEncoder.orderSequenceNumber(message.getOrderSequenceNumber());
    globalStateAdminMessageEncoder.execSequenceNumber(message.getExecSequenceNumber());
    globalStateAdminMessageEncoder.discountFeesInstrumentId(message.getDiscountFeesInstrumentId());
    globalStateAdminMessageEncoder.discountFeesCoinBasisPts(message.getDiscountFeesCoinBasisPts());

    globalStateAdminMessageEncoder.externalId(message.getExternalId());
    globalStateAdminMessageEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    globalStateAdminMessageEncoder.routeToDestination(message.getRouteToDestination());
    globalStateAdminMessageEncoder.triggerTimeMillis(message.getTriggerTimeMillis());

    encodedLength += globalStateAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }


  public static byte[] encodeAckAdmin(AckAdminMessage message, RequestStatus requestStatus) {

    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final AdminAcknowledgementEncoder adminAcknowledgementEncoder = new AdminAcknowledgementEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    adminAcknowledgementEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    adminAcknowledgementEncoder.requestStatus(requestStatus);
    adminAcknowledgementEncoder.externalId(message.getExternalId());
    adminAcknowledgementEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    adminAcknowledgementEncoder.routeToDestination(message.getRouteToDestination());
    adminAcknowledgementEncoder.triggerTimeMillis(message.getTriggerTimeMillis());
    adminAcknowledgementEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset


    encodedLength += adminAcknowledgementEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeFixUserAdminMessage(User user) {

    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final FIXUserAdminMessageEncoder fixUserAdminMessageEncoder = new FIXUserAdminMessageEncoder();
    final com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    fixUserAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    fixUserAdminMessageEncoder.updateType(UpdateType.PUT);
    fixUserAdminMessageEncoder.username(user.getLogin());
    fixUserAdminMessageEncoder.password(user.getPassword());
    fixUserAdminMessageEncoder.senderCompId("TEST_");
    fixUserAdminMessageEncoder.userType(user.getUserType());

    fixUserAdminMessageEncoder.externalId(user.getExternalId());
    fixUserAdminMessageEncoder.sourceSeqNum(msgSeqNum++); // sourceSeqNum
    fixUserAdminMessageEncoder.routeToDestination("ROUTE");

    encodedLength += fixUserAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }



  public static byte[] encodeSnapResponse(SnapResponseAdminMessage message) {
    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final SnapResponseAdminMessageEncoder snapResponseAdminMessageEncoder = new SnapResponseAdminMessageEncoder();
    com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    snapResponseAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    snapResponseAdminMessageEncoder.externalId(message.getExternalId());
    snapResponseAdminMessageEncoder.inputKafkaRecordOffset(message.getInputKafkaRecordOffset());
    snapResponseAdminMessageEncoder.outputKafkaRecordOffset(message.getOutputKafkaRecordOffset());
    snapResponseAdminMessageEncoder.orderSequenceNumber(message.getOrderSequenceNumber());
    snapResponseAdminMessageEncoder.sequenceNumber(message.getSequenceNumber());
    snapResponseAdminMessageEncoder.snapId(message.getSnapId());
    snapResponseAdminMessageEncoder.sourceSeqNum(message.getSourceSeqNum());
    snapResponseAdminMessageEncoder.triggerTimeMillis(message.getTriggerTimeMillis());
    snapResponseAdminMessageEncoder.routeToDestination(message.getRouteToDestination());
    snapResponseAdminMessageEncoder.orderId(message.getOrderId());
    snapResponseAdminMessageEncoder.execId(message.getExecId());

    encodedLength += snapResponseAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeTradeState(TradeStateAdminMessage tradeStateAdminMessage) {
    short encodedLength = 2;
    final ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(16384);
    final UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    final TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
    com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
        new com.solfini.internal.admin.schema.MessageHeaderEncoder();
    tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
    encodedLength += headerEncoder.encodedLength();

    tradeStateAdminMessageEncoder.marketStatus(tradeStateAdminMessage.getMarketStatus()); // MarketStatus.RESTATE
    tradeStateAdminMessageEncoder.securityId(tradeStateAdminMessage.getSecurityId());
    tradeStateAdminMessageEncoder.triggerTimeMillis(tradeStateAdminMessage.getTriggerTimeMillis());
    tradeStateAdminMessageEncoder.routeToDestination(tradeStateAdminMessage.getRouteToDestination());

    tradeStateAdminMessageEncoder.externalId(tradeStateAdminMessage.getExternalId());
    tradeStateAdminMessageEncoder.sourceSeqNum(tradeStateAdminMessage.getSourceSeqNum()); // sourceSeqNum
    tradeStateAdminMessageEncoder.kafkaRecordOffset(tradeStateAdminMessage.getKafkaRecordOffset()); // kafkaRecordOffset

    encodedLength += tradeStateAdminMessageEncoder.encodedLength();
    adminMessageBuffer.limit(encodedLength);
    adminMessageUnsafeBuffer.putShort(0, encodedLength);

    return StringUtil.bufferToArrayBulk(adminMessageUnsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeLogon(User user) {
    // create logon message
    LogonMessage logonMessage = new LogonMessage();
    logonMessage.setHeartBeatInterval(60);

    final LogonEncoderCache cache = LogonEncoderCache.get();
    final LogonEncoder logonEncoder = cache.getEncoder();
    final ByteBuffer directBuffer = cache.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = cache.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = cache.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    logonEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, logonMessage);
    encodedLength += headerEncoder.encodedLength();

    logonEncoder.heartBtInt(logonMessage.getHeartBeatInterval());
    logonEncoder.username(user.getLogin());

    // convert and publish
    encodedLength += logonEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeResendRequest(ResendRequestMessage message) {

    final ResendRequestEncoder encoder = new ResendRequestEncoder();
    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    short encodedLength = HEADER_LENGTH;
    encoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    encodedLength += headerEncoder.encodedLength();

    encoder.beginSequenceNo(message.getBeginSeqNo());
    encoder.endSequenceNo(message.getEndSeqNo());

    // convert and publish
    encodedLength += encoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeHeartBeat(HeartbeatMessage message) {

    // create buffers
    final HeartbeatEncoder encoder = new HeartbeatEncoder();
    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    short encodedLength = HEADER_LENGTH;
    encoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, message);
    encodedLength += headerEncoder.encodedLength();

    // convert and publish
    encodedLength += encoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  public static byte[] encodeSequenceReset(SequenceResetMessage message) {
    final SequenceResetEncoder encoder = new SequenceResetEncoder();

    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    short length = HEADER_LENGTH;
    encoder.wrapAndApplyHeader(unsafeBuffer, length, headerEncoder);
    populateHeader(headerEncoder, message);
    length += headerEncoder.encodedLength();

    // Set values
    encoder.gapFillFlag(message.isGapFillFlag() ? BooleanType.TRUE : BooleanType.FALSE);
    encoder.newSequenceNo(message.getNewSeqNo());

    // convert and publish
    length += encoder.encodedLength();
    directBuffer.limit(length);
    unsafeBuffer.putShort(0, length);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), length, KAFKA_OFFSET);
  }

  public static byte[] encodeOrderMassCancel(String orderId, User user, InstrumentPair instrument, Side side) {

    final MassCancelOrder massCancelOrder = new MassCancelOrder();
    final MassCancelOrderEncoder encoder = new MassCancelOrderEncoder();

    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    short length = HEADER_LENGTH;
    encoder.wrapAndApplyHeader(unsafeBuffer, length, headerEncoder);
    populateHeader(headerEncoder, massCancelOrder);
    length += headerEncoder.encodedLength();

    encoder.submitterId(0);
    encoder.securityId(instrument.getId());
    encoder.cancelId(instrument.getId());
    encoder.clOrdID(String.valueOf(user.getId() + 1_000_000_000));
    encoder.userId(user.getId());

    // convert and publish
    length += encoder.encodedLength();
    directBuffer.limit(length);
    unsafeBuffer.putShort(0, length);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), length, KAFKA_OFFSET);
  }

  public static byte[] encodeCancelReplace(String orderId, String originalOrderId, User user, InstrumentPair instrument, Side side,
      long price, long price2, long qty, long qty2) {

    final CancelReplaceOrder cancelReplaceOrder = new CancelReplaceOrder();
    final CancelReplaceOrderEncoder encoder = new CancelReplaceOrderEncoder();

    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    short length = HEADER_LENGTH;
    encoder.wrapAndApplyHeader(unsafeBuffer, length, headerEncoder);
    populateHeader(headerEncoder, cancelReplaceOrder);
    length += headerEncoder.encodedLength();

    // Set values
    encoder.originalOrderId(Long.parseLong(originalOrderId));
    encoder.securityId(instrument.getId());
    encoder.cancelId(Long.parseLong(orderId));
    encoder.qty2((int) qty2);
    encoder.qtyScale((short) 2);
    encoder.side(side);
    encoder.qty(qty);
    encoder.qtyScale((short) 2);
    encoder.price(price);
    encoder.priceScale((short) 2);
    encoder.price2(price2);
    encoder.price2Scale((short) 2);
    encoder.userId(user.getId());
    encoder.clOrdID(String.valueOf(user.getId() + 1_000_000_000));

    // convert and publish
    length += encoder.encodedLength();
    directBuffer.limit(length);
    unsafeBuffer.putShort(0, length);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), length, KAFKA_OFFSET);
  }

  public static byte[] encodeOrderCancel(String orderId, User user, InstrumentPair instrument, Side side, OrdType orderType, long qty) {
    CancelOrder cancelOrder = new CancelOrder();

    // create buffers
    final CancelOrderEncoder encoder = new CancelOrderEncoder();
    final ByteBuffer directBuffer = ByteBuffer.allocateDirect(4096);
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(directBuffer);
    final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    short encodedLength = HEADER_LENGTH;
    encoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    populateHeader(headerEncoder, cancelOrder);
    encodedLength += headerEncoder.encodedLength();

    // set values
    encoder.userId(user.getId());
    encoder.securityId(instrument.getId());
    encoder.clOrdID(String.valueOf(user.getId() + 1_000_000_000));
    encoder.cancelId(Long.parseLong(orderId));
    encoder.side(side);
    encoder.qty(qty);
    encoder.qtyScale((short) 2);

    // convert and publish
    encodedLength += encoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    return StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
  }

  private static int ME_SEQ_ID = 1;

  private static void populateHeader(final MessageHeaderEncoder headerEncoder, final Message message) {
    final SessionInfo sessionInfo = SessionInfoCache.DEFAULT;

    headerEncoder.msgSeqNum(sessionInfo.incrementAndGetMessageSequenceNumber());
    headerEncoder.sendingTime(System.currentTimeMillis());
    headerEncoder.sourceSeqNum(message.getSourceSeqNum()); // sourceSeqNum
    headerEncoder.kafkaRecordOffset(message.getKafkaRecordOffset()); // kafkaRecordOffset
    headerEncoder.senderCompId(Context.getInstanceId()); // instance id
    headerEncoder.deliverToCompId(ME_SEQ_ID); // matching engine seq num
  }

}
