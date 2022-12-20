package com.solfini.matchengine.fundingrate;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import com.solfini.internal.admin.schema.TokenType;
import org.slf4j.event.Level;
import org.junit.Before;
import org.junit.Test;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.instrument.AssetFundingRate;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.FundingRateCalcMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.fields.DecimalFloat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Chris Mack
 *
 */
public class FundingRateTest {

  @Before
  public void before() {
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
    properties.setProperty("NUM_DECODER_THREADS", "8");
    properties.setProperty("KAFKA.CONSUMER.bootstrap.servers", "localhost:9092");
    properties.setProperty("KAFKA.CONSUMER.key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("KAFKA.CONSUMER.value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    try {
      PropertyReader.initialize(null, properties);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  protected User createUser(final int userId, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }

  protected static void createInstruments() {
    final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000);
    usdt.setIndexFeedUsdMark(1);
    InstrumentCache.addInstrument(usdt);

    InstrumentPair pair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, usdt, (short) 2, (short) 6, 2, AssetType.PERPETUAL_SWAP, 5_00, 10_00, 260, 0);

    // OrderBook orderBook = OrderBookFactory.create(OrderBookFactory.ARRAY_ORDER_BOOK, OrderBookFactory.MARGIN_PREORDER_CHECK, pair);
    // pair.setOrderBook(orderBook);
    InstrumentCache.addPair(pair);
  }

  @Test
  public void createFundingRateTest() {
    createInstruments();

    final User user = createUser(18);
    user.addPosition(1, 10_000_000, null, 0, TokenType.ERC20);
    user.addPosition(14, 1_000_000, null, 0, TokenType.ERC20);
    user.setActive(true);

    FundingRateCalcMessage message = new FundingRateCalcMessage();
    message.setUpdateType(UpdateType.PATCH);
    message.setConnectionId(1);
    message.setTxId(123);
    message.setTriggerTimeMillis(0);
    message.setRouteToDestination("ALL");
    List<AssetFundingRate> assetFundingList = message.getAssetFundingList();
    assetFundingList.add(new AssetFundingRate(14, new DecimalFloat(2222, 2), new DecimalFloat(1000, 2)));

    message.getMessageType();
    message.getPayloadType();
    message.getTxId();
    message.getUpdateType();
    message.onPublish();
    message.toJSON();
    message.toString();

    message.onMatcher();

    Message userAdminMessage = Context.getMatcherToPublisherQueue().poll();
    assertTrue(userAdminMessage instanceof UserAdminMessage);

    Message balanceMessage = Context.getMatcherToPublisherQueue().poll();
    assertTrue(balanceMessage instanceof BalanceAdminMessage);

    // Message balanceMessage2 = Context.getMatcherToPublisherQueue().poll();
    // assertTrue(balanceMessage2 instanceof BalanceAdminMessage);

    BalanceAdminMessage output = (BalanceAdminMessage) balanceMessage;
    assertEquals(Constants.TX_FUNDING_RATE, output.getTxType());
    Position[] positionArr = output.getPositionArr();
    for (int i = 0; i < positionArr.length; i++) {
      if (positionArr[i] != null)
        System.out.println("i=" + i + ", position=" + positionArr[i]);
    }

    assertNotNull(positionArr[1]);
    assertEquals(1, positionArr[1].getInstrumentId());
    assertEquals(-212200000, positionArr[1].getQuantity());

    assertNotNull(positionArr[2]);
    assertEquals(14, positionArr[2].getInstrumentId());
    assertEquals(1000000, positionArr[2].getQuantity());
  }


  // public static void main(String[] args) {
  // final int positionId = 22;
  // final User user = new User(18);
  // final Position[] positionArr = new Position[32];
  // positionArr[22] = new Position(user, positionId);
  // positionArr[22].setQuantity(1_000_000);
  // positionArr[22].setAvailableQuantity(1_000_000);
  // positionArr[3] = new Position(user, 3);
  // positionArr[3].setQuantity(10_000_000);
  // positionArr[3].setAvailableQuantity(10_000_000);
  // user.setPositionArr(positionArr);


  // final InstrumentPair instrumentPair = new InstrumentPair(positionId, "EWJ", "EWJ", null, null, 2, 6, 1, 10, 20, 51.01, 0);
  // final double interestRate = 0.05;
  // final double usdBtcMark = 3877.83;
  // final double last = 110.53;
  // final double usdMark = 80.78;

  // double premiumAbs = 0;
  // if (usdMark > 0) {
  // premiumAbs = Math.abs(usdMark - last) / usdMark;
  // }
  // // premiumAbs = 0.3682842287694974;

  // double fundingRateAnnual = interestRate + premiumAbs;
  // if (last > usdMark)
  // fundingRateAnnual = -fundingRateAnnual;
  // double fundingRate = fundingRateAnnual / 200;


  // FundingRateCalcMessage message = new FundingRateCalcMessage();
  // message.onMatcher();
  // }

  private static double scaleFactor(final int scale) {
    double factor = 1;
    for (int i = 0; i < scale; i++) {
      factor = factor * 0.1;
    }
    return factor;
  }

  private static int scaleMultiplier(final int scale) {
    int multiplier = 1;
    for (int i = 0; i < scale; i++) {
      multiplier = multiplier * 10;
    }
    return multiplier;
  }

  @Test
  public void fundingRateRoundingError() {
    final double fundingRate = -StringUtil.toDouble(new DecimalFloat(4000, 6));
    final double markInSettleCoin = StringUtil.toDouble(new DecimalFloat(11278790000L, 6));
    final long quantity = 1423000;
    final double usdSettlementMark = 1.0;

    final double notional = markInSettleCoin * scaleFactor(6) * quantity;
    final double adjustment = notional * fundingRate;
    final double adjSettleCoin = adjustment / usdSettlementMark;
    final long change = (long) (adjSettleCoin * scaleMultiplier(6));

    System.out.println(change);
    System.out.println(1660224435835L + change);
  }

}
