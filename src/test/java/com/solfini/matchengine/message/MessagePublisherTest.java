package com.solfini.matchengine.message;

import com.solfini.common.Context;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.message.admin.TradeStateAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.MarketDataSnapMessage;
import com.solfini.matchengine.message.session.*;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.publisher.SessionInfoCache;
import com.solfini.matchengine.session.SessionInfo;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import java.util.Properties;

import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class MessagePublisherTest extends OrderBookTest {

  @Override
  protected void onConfigure(Properties properties) {
    properties.setProperty("QUEUE_CAPACITY", "100");
    properties.setProperty("ORDER_POOL_QUEUE_CAPACITY", "40000");
    properties.setProperty("ORDER_POOL_START_CAPACITY", "40000");
    properties.setProperty("KAFKA.PRODUCER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.PRODUCER.key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.setProperty("KAFKA.PRODUCER.value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
  }

  @Test
  public void executionReportPublisher() {
    Order order = createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY);
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    order.onMatcher();

    expectOutput("BalanceAdminMessage", "userId=18, Balance [assetId=1, Balance [assetId=12");
    expectOutput("Order", "Order [securityId=12, orderId=1, price=1011, price_scale=2, quantityLong=500");

    assertMessages();
    assertOutputMessages();
  }

  @Test
  public void businessRejectPublisher() {
    Order order = createOrder(1, user, pair.getId(), -1, -1, Side.BUY, DAY);
    order.onMatcher();

    expectMessage(
      "businessRejectReason=PRICE_IS_MISSING, text=Price is missing, refMsgType=ORDER_SINGLE, businessRejectRefID=1, orderId=1");
    assertMessages();

    expectOutput("BusinessRejectMessage",
      "businessRejectReason=PRICE_IS_MISSING, text=Price is missing, refMsgType=null");
  }

  @Test
  public void logonPublisher() {
    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setConnectionId(0);
    sessionInfo.setSenderCompId("0");

    LogonMessage message = new LogonMessage();
    message.setSessionInfo(sessionInfo);
    message.setUser(user);
    message.onMatcher();

    expectMessage("LogonMessage", "heartbeatInterval=0", "user=User [id=18, externalId=0, userType=0, login=user_18");
    assertMessages();

    expectOutput("LogonMessage", "heartbeatInterval=0");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=1");
    assertOutputMessages();
  }

  @Test
  public void logonPublisherTradeApi() {
    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setConnectionId(0);
    sessionInfo.setSenderCompId("1000000009");

    LogonMessage message = new LogonMessage();
    message.setSessionInfo(sessionInfo);
    message.setUser(user);
    message.onMatcher();

    expectMessage("LogonMessage", "heartbeatInterval=0", "user=User [id=18, externalId=0, userType=0, login=user_18");
    assertMessages();

    expectOutput("LogonMessage", "heartbeatInterval=0");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=1");
    assertOutputMessages();

    Assert.assertNotNull(SessionInfoCache.getTradeApiArr());
    Assert.assertTrue(SessionInfoCache.getTradeApiArr().length > 0);
    Assert.assertNotNull(SessionInfoCache.getTradeApiArr()[0]);
    Assert.assertEquals("1000000009", SessionInfoCache.getTradeApiArr()[0].getSenderCompId());
  }

  @Test
  public void resendRequestPublisher() {
    ResendRequestMessage resendRequestMessage = new ResendRequestMessage();
    resendRequestMessage.setBeginSeqNo(1000);
    resendRequestMessage.setEndSeqNo(2000);
    resendRequestMessage.setOriginated(true);
    resendRequestMessage.setSenderCompId("test_sender");
    resendRequestMessage.onMatcher();

    expectMessage("ResendRequestMessage", "beginSeqNo=1000, endSeqNo=2000, originated=true");
    assertMessages();

    expectOutput("ResendRequestMessage", "beginSeqNo=1000, endSeqNo=2000, originated=true");
    assertOutputMessages();
  }

  @Test
  public void tradeStateAdminMessagePublisher() {
    TradeStateAdminMessage tradeStateAdminMessage = new TradeStateAdminMessage();
    tradeStateAdminMessage.setMarketStatus(MarketStatus.RESTATE);
    tradeStateAdminMessage.setSecurityId(pair.getId());
    tradeStateAdminMessage.setSenderCompId("decoder_test");
    tradeStateAdminMessage.onPublish();

    clearExpectedOutputMessages();

    expectOutput("TradeStateAdminMessage", "marketStatus=RESTATE, securityId=12, routeToDestination=ALL");
    assertOutputMessages(false);
  }

  @Test
  public void networkStatusAdminMessagePublisher() {
    NetworkStatusMessage networkStatusMessage = new NetworkStatusMessage(45, 46, 47, "EXEC_TEST");
    networkStatusMessage.onMatcher();

    expectMessage("NetworkStatusMessage", "requestId=45, responseId=46, orderSequenceNumber=47");
    assertMessages();
    expectOutput("NetworkStatusMessage", "requestId=45, responseId=46, orderSequenceNumber=47");
    assertOutputMessages();
  }

  @Test
  public void heartBeatMessagePublisher() {
    HeartbeatMessage heartbeatMessage = new HeartbeatMessage();
    heartbeatMessage.getHeartBeatInterval(23453);

    Context.getMessagePublisher().publish(heartbeatMessage);
    clearExpectedOutputMessages();
    expectOutput("HeartbeatMessage", "senderCompId=me01");
    assertOutputMessages(false);
  }

  @Test
  public void marketDataSnapAdminMessagePublisher() {
    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setConnectionId(0);
    sessionInfo.setSenderCompId("PUBLISHER_TEST");

    MarketDataSnapMessage marketDataSnapMessage = MarketDataSnapMessage.create(pair, sessionInfo);
    marketDataSnapMessage.onPublish();

    clearExpectedOutputMessages();
    expectOutput("MarketDataSnapMessage", "instrumentPair=" + BTC_USDT_F);
    assertOutputMessages(false);
  }

  @Test
  public void sequenceResetAdminMessagePublisher() {
    SequenceResetMessage sequenceResetMessage = new SequenceResetMessage();
    sequenceResetMessage.setNewSeqNo(4343453);
    sequenceResetMessage.setGapFillFlag(true);
    sequenceResetMessage.onMatcher();

    expectMessage("SequenceResetMessage", "newSeqNo=4343453, gapFillFlag=true");
    assertMessages();

    // TODO: Verify newSeqNo
    // expectOutput("SequenceResetMessage", "newSeqNo=4343453, gapFillFlag=true");
    expectOutput("SequenceResetMessage", "gapFillFlag=true");

    assertOutputMessages();
  }
}
