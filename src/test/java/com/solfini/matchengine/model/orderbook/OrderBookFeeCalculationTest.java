package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.Fee;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.MbxMath;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.Side;
import static com.solfini.sbe.encoder.TimeInForce.DAY;

public class OrderBookFeeCalculationTest extends OrderBookTest {

  private void beforePercentFeeCalculation() {
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 0, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 100, FeeType.PERCENT, MakerTaker.ALL, 1, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 200, FeeType.PERCENT, MakerTaker.ALL, 2, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 300, FeeType.PERCENT, MakerTaker.ALL, 3, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 400, FeeType.PERCENT, MakerTaker.ALL, 4, true));

    user.setPosition(pair.getId(), 0, null);
    user.setFeeTier(1);
  }

  private void beforeAbsoluteFeeCalculation() {
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 0, FeeType.ABSOLUTE, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 100, FeeType.ABSOLUTE, MakerTaker.ALL, 1, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 200, FeeType.ABSOLUTE, MakerTaker.ALL, 2, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 300, FeeType.ABSOLUTE, MakerTaker.ALL, 3, true));
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 400, FeeType.ABSOLUTE, MakerTaker.ALL, 4, true));

    user.setPosition(pair.getId(), 0, null);
    user.setFeeTier(1);
  }

  @Test
  public void exchangeUserPositionUpdate() {

    // create exchange user and assign to global constant
    User exchangeUser = createUser(80);
    UserCache.setExchangeUser(exchangeUser);
    expectMessage("userId=80");

    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    assertMessages();

    Position position = exchangeUser.getPositionArr()[3]; // 3 is the fee instrument id corresponding to the calculated fee

    Assert.assertEquals(1010, position.getQuantity());
  }

  // Buy order submission should have no fee
  @Test
  public void buyOrderSubmission() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.BUY, DAY));


    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    assertMessages();
  }

  // Sell order submission should have no fee
  @Test
  public void sellOrderSubmission() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    assertMessages();
  }

  @Test
  public void percentFeeExactMatchSellOrderToBuyOrderOnBook() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    assertMessages();
  }

  @Test
  public void percentFeeSellOrderPartiallyFilled() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    assertMessages();
  }

  @Test
  public void percentFeeBuyOrderPartiallyFilled() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");
    assertMessages();
  }

  @Test
  public void percentFeePartiallyFilledSellOrderTotalFill() {
    beforePercentFeeCalculation();

    // partially fill sell order
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.SELL, DAY));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    assertMessages();
  }

  @Test
  public void percentFeePartiallyFilledBuyOrderTotalFill() {
    beforePercentFeeCalculation();

    // partially fill buy order
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));


    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    assertMessages();
  }

  @Test
  public void absoluteFeeExactMatchSellOrderToBuyOrderOnBook() {
    beforeAbsoluteFeeCalculation();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-10");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-10");
    assertMessages();
  }

  @Test
  public void userFeeTierChangeAfterBuyOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill buy order
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    // change user fee tier
    user.setFeeTier(2);

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");
    assertMessages();
  }

  @Test
  public void userFeeTierChangeAfterSellOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill sell order
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.SELL, DAY));

    // change User tier
    user.setFeeTier(2);

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");

    assertMessages();
  }

  @Test
  public void tierPriceChangeAfterBuyOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill buy order
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    // change tier price
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 200, FeeType.PERCENT, MakerTaker.ALL, 1, true));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");
    assertMessages();
  }

  @Test
  public void tierPriceChangeAfterSellOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill sell order
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1011, 1000, Side.SELL, DAY));

    // change tier price
    pair.setFee(new Fee(pair.getId(), pair.getBaseId(), 200, FeeType.PERCENT, MakerTaker.ALL, 1, true));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1011, 500, Side.BUY, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=1000, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=500, execType=TRADE, ordStatus=PARTIALLY_FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010");

    assertMessages();
  }

  @Test
  public void PercentFeeOnSellerWithPositivePosition() {
    beforePercentFeeCalculation();

    User buyer = createUser(31);
    buyer.addPosition(pair.getId(), 100000_000, null);
    expectMessage("userId=31");
    buyer.setFeeTier(1);

    User seller = createUser(32);
    seller.addPosition(pair.getId(), 125, null);
    expectMessage("userId=32");
    seller.setFeeTier(1);

    beforePercentFeeCalculation();
    orderBook.addOrder(createOrder(1, buyer, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, seller, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-126");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-379");

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    assertMessages();
  }

  @Test
  public void PercentFeeBuyerWithNegativePosition() {
    beforePercentFeeCalculation();

    User buyer = createUser(25);
    buyer.addPosition(pair.getBaseId(), 100_000_000, null);
    buyer.addPosition(pair.getId(), -125, null);
    expectMessage("userId=25");
    buyer.setFeeTier(1);

    User seller = createUser(26);
    seller.addPosition(pair.getBaseId(), 100_000_000, null);
    expectMessage("userId=26");
    seller.setFeeTier(1);

    beforePercentFeeCalculation();
    orderBook.addOrder(createOrder(1, buyer, pair.getId(), 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, seller, pair.getId(), 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-126");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-379");

    assertMessages();
  }

  private static long getExpectedFeePositionQuantityChange(long price, long quantity, int fee, double indexFeedUsdMark,
      double quotedCoinUsdMask) {
    // priceScale, qtyScale, feeQtyScale = 2

    long notionalUsd = (long) Math.abs(price * 2 * quantity * 2 * quotedCoinUsdMask);
    long usdFee = (long) (notionalUsd * fee * 0.000001);

    return (long) (usdFee / indexFeedUsdMark * 100);
  }

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
  public void feeCalcRoundOffError() {
    final long quantityLong = 120000;
    final int referencePrice = 950327;
    final double quotedCoinUsdMark = 1.0;
    final int fee = 1500;

    final double adjReferenceQuantity = MbxMath.roundToBestPrecision(quantityLong * scaleFactor(6));
    final double notional = MbxMath.roundToBestPrecision(referencePrice * adjReferenceQuantity * scaleFactor(2));
    final double usdNotional = MbxMath.roundToBestPrecision(Math.abs(notional * quotedCoinUsdMark));

    final double usdFee = MbxMath.roundToBestPrecision((usdNotional * fee) * .000001);
    final double feeInFeeInstrument = usdFee / 1.0d;
    final long feeQuantity = (long) (feeInFeeInstrument * scaleMultiplier(6));

    System.out.println("usdNotional = " + usdNotional);
    System.out.println("usdFee = " + usdFee);
    System.out.println("feeInFeeInstrument = " + feeInFeeInstrument);
    System.out.println("feeQuantity = " + feeQuantity);
  }
}
