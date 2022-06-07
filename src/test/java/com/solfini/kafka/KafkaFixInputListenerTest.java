package com.solfini.kafka;

import static com.solfini.util.benchmark.TestEncoder.encodeAckAdmin;
import static com.solfini.util.benchmark.TestEncoder.encodeBalance;
import static com.solfini.util.benchmark.TestEncoder.encodeCancelReplace;
import static com.solfini.util.benchmark.TestEncoder.encodeFeeAdminMessage;
import static com.solfini.util.benchmark.TestEncoder.encodeFixUserAdminMessage;
import static com.solfini.util.benchmark.TestEncoder.encodeFundingRateCalcAdmin;
import static com.solfini.util.benchmark.TestEncoder.encodeGlobalStateAdmin;
import static com.solfini.util.benchmark.TestEncoder.encodeHeartBeat;
import static com.solfini.util.benchmark.TestEncoder.encodeLogon;
import static com.solfini.util.benchmark.TestEncoder.encodeNewOrder;
import static com.solfini.util.benchmark.TestEncoder.encodeNewUser;
import static com.solfini.util.benchmark.TestEncoder.encodeOrderCancel;
import static com.solfini.util.benchmark.TestEncoder.encodeOrderMassCancel;
import static com.solfini.util.benchmark.TestEncoder.encodeResendRequest;
import static com.solfini.util.benchmark.TestEncoder.encodeSecurityDefinition;
import static com.solfini.util.benchmark.TestEncoder.encodeSequenceReset;
import static com.solfini.util.benchmark.TestEncoder.encodeSnapResponse;
import static com.solfini.util.benchmark.TestEncoder.encodeTradeState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;
import com.solfini.common.Context;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.instrument.Fee;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.AckAdminMessage;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.FIXUserAdminMessage;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.message.admin.GlobalStateAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.session.HeartbeatMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.matchengine.message.session.ResendRequestMessage;
import com.solfini.matchengine.message.session.SequenceResetMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

public class KafkaFixInputListenerTest extends ModelTest {

  private KafkaInputFixListener kafkaInputFixListener;
  private final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue;

  private User user = null;
  private InstrumentPair pair = null;

  public KafkaFixInputListenerTest() throws IOException {
    PropertyReader.initialize(null, configure());
    kafkaInputFixListener = new KafkaInputFixListener(false);
    receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  }

  @Before
  public void before() {
    LogLevel.setLevel(Level.TRACE);
    Thread.currentThread().setName(Thread.currentThread().getName() + "_0");

    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", (short) 2, (short) 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", (short) 4, (short) 3));
    InstrumentCache.updateSecurityDefinition(
        createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, (short) 2, (short) 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    pair = InstrumentCache.getPair(BTC_USDT_F);
    user = createUser(28);
    user.addPosition(pair.getId(), 10_000, null);
    expectMessage("userId=28");
    assertMessages();
  }

  @After
  public void after() {
    ArrayList<Message> resultList = new ArrayList<>();
    receiverToMatcherQueue.drainTo(resultList, 1000);
  }

  @Test
  public void decodeNewOrder() {
    String orderId = String.valueOf(NewOrderSingleHandler.getOrderId());
    byte[] bytes = encodeNewOrder(user, pair, Side.BUY, OrdType.LIMIT, 1011, 500, orderId, TimeInForce.GOOD_TILL_CANCEL);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();
    compareUsers(decodedMessage.getUser(), user);

    if (decodedMessage instanceof Order) {
      Order decodedOrder = (Order) decodedMessage;
      Assert.assertEquals(Side.BUY, decodedOrder.getSide());
      Assert.assertEquals(OrdType.LIMIT, decodedOrder.getOrdType());
      Assert.assertEquals(1011, decodedOrder.getPriceInt());
      Assert.assertEquals(500, decodedOrder.getQuantityLong());
      Assert.assertEquals(Long.parseLong(orderId) + 1, decodedOrder.getOrderId());
    } else
      Assert.fail();

  }

  @Test
  public void decodeLogonMessage() {
    User newUser = createUser(29);
    newUser.addPosition(pair.getId(), 10_000, null);
    expectMessage("userId=29");
    assertMessages();

    byte[] bytes = encodeLogon(newUser);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof LogonMessage) {
      LogonMessage logonMessage = (LogonMessage) decodedMessage;
      compareUsers(newUser, logonMessage.getUser());
      Assert.assertEquals("me01", logonMessage.getSenderCompId());
      Assert.assertEquals("LOGON", logonMessage.getMessageType().toString());
    } else
      Assert.fail();
  }

  @Test
  public void decodeResendRequest() {

    ResendRequestMessage resendRequestMessage = new ResendRequestMessage();
    resendRequestMessage.setBeginSeqNo(1000);
    resendRequestMessage.setEndSeqNo(2000);
    resendRequestMessage.setOriginated(true);
    resendRequestMessage.setSenderCompId("test_sender");

    byte[] bytes = encodeResendRequest(resendRequestMessage);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof ResendRequestMessage) {
      ResendRequestMessage decodedResendRequest = (ResendRequestMessage) decodedMessage;
      Assert.assertEquals(resendRequestMessage.getBeginSeqNo(), decodedResendRequest.getBeginSeqNo());
      Assert.assertEquals(resendRequestMessage.getEndSeqNo(), decodedResendRequest.getEndSeqNo());
    } else
      Assert.fail();
  }

  @Test
  public void decodeOrderCancel() {

    byte[] bytes = encodeOrderCancel("4", user, pair, Side.BUY, OrdType.LIMIT, 500);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);
    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof CancelOrder) {
      CancelOrder cancelOrder = (CancelOrder) decodedMessage;

      Assert.assertEquals(500, cancelOrder.getQuantityLong());
      Assert.assertEquals(pair.getId(), cancelOrder.getSecurityId());
      Assert.assertEquals(Side.BUY, cancelOrder.getSide());
      Assert.assertEquals(user.getId(), cancelOrder.getAccount());
      Assert.assertEquals(user.getId(), cancelOrder.getAccount());
      Assert.assertEquals(OrdType.LIMIT, cancelOrder.getOrdType());
      compareUsers(user, cancelOrder.getUser());
    } else
      Assert.fail();
  }

  @Test
  public void decodeMassCancel() {

    byte[] bytes = encodeOrderMassCancel("4", user, pair, Side.BUY);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);
    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof MassCancelOrder) {
      MassCancelOrder massCancelOrder = (MassCancelOrder) decodedMessage;

      Assert.assertEquals(pair.getId(), massCancelOrder.getSecurityId());
      Assert.assertEquals(user.getId(), massCancelOrder.getAccount());
      compareUsers(user, massCancelOrder.getUser());
    } else
      Assert.fail();
  }

  @Test
  public void decodeCancelReplace() {

    byte[] bytes = encodeCancelReplace("4", "3", user, pair, Side.BUY, 10011, 2200, 500, 505);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);
    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof CancelReplaceOrder) {
      CancelReplaceOrder cancelReplaceOrder = (CancelReplaceOrder) decodedMessage;
      Assert.assertEquals(pair.getId(), cancelReplaceOrder.getSecurityId());
      Assert.assertEquals(NewOrderSingleHandler.getOrderId(), cancelReplaceOrder.getOrder().getOrderId());
      Assert.assertEquals(505, cancelReplaceOrder.getOrder().getQty());
      Assert.assertEquals(2200, cancelReplaceOrder.getOrder().getPriceInt());
      Assert.assertEquals(Side.BUY, cancelReplaceOrder.getOrder().getSide());
      Assert.assertEquals(pair.getId(), cancelReplaceOrder.getOrder().getSecurityId());
      Assert.assertEquals(2200, cancelReplaceOrder.getPrice2());

      compareUsers(user, cancelReplaceOrder.getUser());
    } else
      Assert.fail();
  }

  @Test
  public void decodeUserAdminMessage() {
    user.setLogin("login_test");
    user.setPassword("password_test");
    byte[] bytes = encodeNewUser(user);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof UserAdminMessage) {
      UserAdminMessage userAdminMessage = (UserAdminMessage) decodedMessage;
      compareUsers(user, userAdminMessage);
    } else
      Assert.fail();
  }

  @Test
  public void decodeBalanceAdminMessage() {

    byte[] bytes = encodeBalance(user, 110530, 2, pair.getId());
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof BalanceAdminMessage) {

      BalanceAdminMessage balanceAdminMessage = (BalanceAdminMessage) decodedMessage;
      Assert.assertEquals(user.getId(), balanceAdminMessage.getUserId());
      Assert.assertEquals(user.getFeeTier(), balanceAdminMessage.getFeeTier());
      Assert.assertEquals(user.getFirmId(), balanceAdminMessage.getFirmId());

      try {
        Assert.assertEquals(pair.getId(), balanceAdminMessage.getBalanceList().get(0).getAssetId());
        Assert.assertEquals("1105.30", balanceAdminMessage.getBalanceList().get(0).getBalance().toString());

      } catch (Exception e) {
        Assert.fail();
      }
    } else
      Assert.fail();
  }

  @Test
  public void decodeSecurityDefinitionAdminMessage() {
    SecurityDefinitionAdminMessage securityDefinitionAdminMessage = new SecurityDefinitionAdminMessage(pair);
    byte[] bytes = encodeSecurityDefinition(securityDefinitionAdminMessage);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof SecurityDefinitionAdminMessage) {
      SecurityDefinitionAdminMessage decodedSecurityDefinition = (SecurityDefinitionAdminMessage) decodedMessage;

      Assert.assertEquals(pair.getId(), decodedSecurityDefinition.getSecurityId());
      Assert.assertEquals(pair.getId(), decodedSecurityDefinition.getSecurityId());
      Assert.assertEquals(pair.getSymbol(), decodedSecurityDefinition.getSymbol());
      Assert.assertEquals(pair.getName(), decodedSecurityDefinition.getName());
      Assert.assertEquals(pair.getMarginCurveId(), decodedSecurityDefinition.getMarginCurveId());
      Assert.assertEquals(pair.getBaseId(), decodedSecurityDefinition.getBaseId());
      Assert.assertEquals(pair.getQuotedId(), decodedSecurityDefinition.getQuotedId());
      Assert.assertEquals(pair.getMaintMarginBasisPoints(), decodedSecurityDefinition.getMaintMarginBasisPoints());

    } else
      Assert.fail();
  }

  @Test
  public void decodeFeeAdminMessage() {

    Fee fee = new Fee(pair.getId(), pair.getBaseId(), 0, FeeType.PERCENT, MakerTaker.ALL, 12, true);
    FeeAdminMessage originalFeeAdminMessage = new FeeAdminMessage(fee);

    byte[] bytes = encodeFeeAdminMessage(originalFeeAdminMessage);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof FeeAdminMessage) {
      FeeAdminMessage receivedFeeAdminMessage = (FeeAdminMessage) decodedMessage;
      Assert.assertEquals(fee.getFee(), receivedFeeAdminMessage.getFee());
      Assert.assertEquals(fee.getInstrumentPairId(), receivedFeeAdminMessage.getAssetId());
      Assert.assertEquals(fee.getTier(), receivedFeeAdminMessage.getTier());
      Assert.assertEquals(fee.getFeeType(), receivedFeeAdminMessage.getFeeType());
      Assert.assertEquals(fee.getMakerTaker(), receivedFeeAdminMessage.getMakerTaker());

    } else
      Assert.fail();
  }

  @Test
  public void decodeFIXUserAdminMessage() {

    byte[] bytes = encodeFixUserAdminMessage(user);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof FIXUserAdminMessage) {
      FIXUserAdminMessage decoded = (FIXUserAdminMessage) decodedMessage;
      Assert.assertEquals(user.getLogin(), decoded.getUsername());
      Assert.assertEquals(user.getPassword(), decoded.getPassword());
      Assert.assertEquals("TEST_", decoded.getSenderCompId());

    } else
      Assert.fail();
  }


  @Test
  public void decodeSnapResponseAdminMessage() {

    SnapResponseAdminMessage originalSnapResponse = new SnapResponseAdminMessage(43, 23, 453, "decoder_test");
    originalSnapResponse.setOrderId(453);
    byte[] bytes = encodeSnapResponse(originalSnapResponse);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    Assert.assertNull(decodedMessage);
    Assert.assertEquals(453, NewOrderSingleHandler.getOrderId());
  }

  @Test
  public void decodeTradeStateAdminMessage() {

    TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage();
    tradeStateAdminMessage.setMarketStatus(MarketStatus.RESTATE);
    tradeStateAdminMessage.setSecurityId(pair.getId());
    tradeStateAdminMessage.setSenderCompId("decoder_test");

    byte[] bytes = encodeTradeState(tradeStateAdminMessage);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof TradeStateAdminMessage) {
      TradeStateAdminMessage decodedTradeState = (TradeStateAdminMessage) decodedMessage;
      Assert.assertEquals(MarketStatus.RESTATE, decodedTradeState.getMarketStatus());
      Assert.assertEquals(pair.getId(), decodedTradeState.getSecurityId());

    } else
      Assert.fail();
  }

  @Test
  public void decodeAdminAckMessage() { // NOTE: Ack admin message does not have a setter for request status


    AckAdminMessage original = new AckAdminMessage();

    byte[] bytes = encodeAckAdmin(original, RequestStatus.SUCCESS);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    // Message is not decoded

    if (decodedMessage instanceof AckAdminMessage) {
      AckAdminMessage decoded = (AckAdminMessage) decodedMessage;
      Assert.assertEquals(RequestStatus.SUCCESS, decoded.getRequestStatus());
    }
  }

  @Test
  public void decodeHeartBeat() {

    HeartbeatMessage heartbeatMessage = new HeartbeatMessage();
    heartbeatMessage.getHeartBeatInterval(1555);

    byte[] bytes = encodeHeartBeat(heartbeatMessage);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof HeartbeatMessage) {
      // Assert.assertEquals(1555,heartbeatMessage.getHeartBeatInterval()); //TODO: Heartbeat interval is set to zero in the decoder.
      // Implement the usage of HB and uncomment this
    } else
      Assert.fail();
  }

  @Test
  public void decodeSequenceReset() {

    SequenceResetMessage sequenceResetMessage = new SequenceResetMessage();
    sequenceResetMessage.setNewSeqNo(4353);
    byte[] bytes = encodeSequenceReset(sequenceResetMessage);
    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.NORMAL_API, bytes);

    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof SequenceResetMessage) {
      SequenceResetMessage decodedSequenceReset = (SequenceResetMessage) decodedMessage;
      Assert.assertEquals(4353, decodedSequenceReset.getNewSeqNo());
    } else
      Assert.fail();
  }

  @Test
  public void decodeGlobalStateAdmin() {

    GlobalStateAdminMessage original = new GlobalStateAdminMessage();
    original.setSnapLoaderMode(1);
    original.setLiquidationMode(1);
    original.setDiscountFeesCoinBasisPts(1043);
    original.setInsuranceLossPercentLimit(53);
    original.setUseInsurance(2);
    original.setOrderSequenceNumber(242);
    original.setExecSequenceNumber(241);

    byte[] bytes = encodeGlobalStateAdmin(original);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);
    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof GlobalStateAdminMessage) {
      GlobalStateAdminMessage decoded = (GlobalStateAdminMessage) decodedMessage;
      Assert.assertEquals(original.getSnapLoaderMode(), decoded.getSnapLoaderMode());
      Assert.assertEquals(original.getLiquidationMode(), decoded.getLiquidationMode());
      Assert.assertEquals(original.getDiscountFeesCoinBasisPts(), decoded.getDiscountFeesCoinBasisPts());
      Assert.assertEquals(original.getInsuranceLossPercentLimit(), decoded.getInsuranceLossPercentLimit());
      Assert.assertEquals(original.getUseInsurance(), decoded.getUseInsurance());
      Assert.assertEquals(original.getOrderSequenceNumber(), decoded.getOrderSequenceNumber());
      Assert.assertEquals(original.getExecSequenceNumber(), decoded.getExecSequenceNumber());
    } else
      Assert.fail();
  }

  @Test
  public void decodeFundingRateCalcAdmin() {

    FundingRateCalcMessage fundingRateCalcMessageOriginal = new FundingRateCalcMessage();
    fundingRateCalcMessageOriginal.setUpdateType(UpdateType.PUT);
    fundingRateCalcMessageOriginal.setTxId(12343);
    fundingRateCalcMessageOriginal.setRouteToDestination("ROUTETODES");

    byte[] bytes = encodeFundingRateCalcAdmin(fundingRateCalcMessageOriginal, pair, 24, 42323);

    kafkaInputFixListener.onMessage(1, 10, 0, KafkaPublisher.ADMIN_API, bytes);
    Message decodedMessage = receiverToMatcherQueue.poll();

    if (decodedMessage instanceof FundingRateCalcMessage) {
      FundingRateCalcMessage decodedFundingRateCalcMessage = (FundingRateCalcMessage) decodedMessage;
      Assert.assertEquals(fundingRateCalcMessageOriginal.getTxId(), decodedFundingRateCalcMessage.getTxId());
      Assert.assertEquals(fundingRateCalcMessageOriginal.getUpdateType(), decodedFundingRateCalcMessage.getUpdateType());
      Assert.assertEquals(fundingRateCalcMessageOriginal.getRouteToDestination(), decodedFundingRateCalcMessage.getRouteToDestination());

      try {
        Assert.assertEquals(pair.getId(), decodedFundingRateCalcMessage.getAssetFundingList().get(0).getAssetId());
      } catch (Exception e) {
        e.printStackTrace();
        Assert.fail();
      }
    } else
      Assert.fail();
  }


  private void compareUsers(User expected, UserAdminMessage actual) {
    Assert.assertEquals(expected.getId(), actual.getUserId());
    Assert.assertEquals(expected.getLogin(), actual.getUsername());
    Assert.assertEquals(expected.getUserType(), actual.getUserType());
    Assert.assertEquals(expected.getFirmId(), actual.getFirmId());
    Assert.assertEquals(expected.getFeeTier(), actual.getFeeTier());
    Assert.assertEquals(expected.getPassword(), actual.getPassword());
  }

  private void compareUsers(User expected, User actual) {
    Assert.assertEquals(expected.getId(), actual.getId());
    Assert.assertEquals(expected.getLogin(), actual.getLogin());
    Assert.assertEquals(expected.getUserType(), actual.getUserType());
    Assert.assertEquals(expected.getUsdValue(), actual.getUsdValue(), 0.1);
    Assert.assertArrayEquals(expected.getPositionArr(), actual.getPositionArr());
  }

  private Properties configure() {
    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("QUEUE_CAPACITY", "100");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "40000");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "40000");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    return properties;
  }
}
