package com.solfini.matchengine.model.session;

import java.io.IOException;
import java.util.Properties;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.slf4j.event.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LogonTest extends ModelTest {

  protected static final int USDT = 1;

  final void configure() {
    LogLevel.setLevel(Level.TRACE);

    Properties properties = new Properties();
    PoolSize.minimize(properties);
    properties.setProperty("INITIAL_ORDER_BOOK_SIZE", "4194304");
    properties.setProperty("BALANCE_ADMIN_POOL_QUEUE_CAPACITY", "4028");
    properties.setProperty("BALANCE_ADMIN_POOL_START_CAPACITY", "1024");
    properties.setProperty("POSITION_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_POOL_START_CAPACITY", "32768");
    properties.setProperty("POSITION_REPORT_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("POSITION_REPORT_POOL_START_CAPACITY", "32768");
    properties.setProperty("USER_OPEN_ORDERS_POOL_QUEUE_CAPACITY", "65536");
    properties.setProperty("USER_OPEN_ORDERS_POOL_START_CAPACITY", "32768");
    properties.setProperty("NUM_ENCODER_THREADS", "0");
    properties.setProperty("NUM_DECODER_THREADS", "0");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Before
  public final void before() {
    configure();
    clearQueues();
  }

  @After
  public final void after() {
    clearQueues();
  }

  protected void createInstruments() {
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 4, 3));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    expectMessage("securityId=" + USDT + ", symbol=USDT");
    expectMessage("securityId=" + BTC + ", symbol=BTC");
    expectMessage("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F], updateType=PATCH");

    expectOutput("securityId=" + USDT + ", symbol=USDT");
    expectOutput("securityId=" + BTC + ", symbol=BTC");
    expectOutput("securityId=" + BTC_USDT_F + ", symbol=BTC/USDT[F]");
  }


  @Test
  public void testLogonMessage() {
    createInstruments();

    User user = createUser(18);
    user.addPosition(USDT, 10_000_00, null, 0, TokenType.ERC20);
    user.addPosition(BTC, 10_000_00, null, 0, TokenType.ERC20);
    user.addPosition(BTC_USDT_F, 10_000_00, null, 0, TokenType.ERC20);

    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setConnectionId(0);
    sessionInfo.setSenderCompId("0");

    LogonMessage message = new LogonMessage();
    message.setSessionInfo(sessionInfo);
    message.setUser(user);

    message.onMatcher();

    expectMessage("UserAdminMessage", "userId=18");
    expectMessage("LogonMessage", "heartbeatInterval=0", "user=User [id=18, externalId=0, userType=0, login=user_18");
    assertMessages();

    expectOutput("UserAdminMessage", "userId=18");
    expectOutput("LogonMessage", "heartbeatInterval=0");
    expectOutput("BalanceAdminMessage", "userId=18", "Balance [assetId=1, balance=10000.00,", "Balance [assetId=3, balance=1000.000,");
    assertOutputMessages();
  }
}
