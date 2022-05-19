package com.solfini.binance.orderbook;

import org.junit.Assert;
import org.junit.Test;

import com.solfini.instrument.Fee;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;

public class BinanceFeeCalculationTest extends BinanceOrderBookTest {

  private void beforePercentFeeCalculation() {
    pair.setFee(new Fee(BTC_USDT_F, USDT, 0, FeeType.PERCENT, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 100, FeeType.PERCENT, MakerTaker.ALL, 1, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 200, FeeType.PERCENT, MakerTaker.ALL, 2, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 300, FeeType.PERCENT, MakerTaker.ALL, 3, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 400, FeeType.PERCENT, MakerTaker.ALL, 4, true));

    user.setPosition(BTC_USDT_F, 0);
    user.setFeeTier(1);
  }

  private void beforeAbsoluteFeeCalculation() {
    pair.setFee(new Fee(BTC_USDT_F, USDT, 0, FeeType.ABSOLUTE, MakerTaker.ALL, 0, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 100, FeeType.ABSOLUTE, MakerTaker.ALL, 1, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 200, FeeType.ABSOLUTE, MakerTaker.ALL, 2, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 300, FeeType.ABSOLUTE, MakerTaker.ALL, 3, true));
    pair.setFee(new Fee(BTC_USDT_F, USDT, 400, FeeType.ABSOLUTE, MakerTaker.ALL, 4, true));

    user.setPosition(BTC_USDT_F, 0);
    user.setFeeTier(1);
  }

  @Test
  public void exchangeUserPositionUpdate() {

    // create exchange user and assign to global constant
    User exchangeUser = createUser(80);
    UserCache.setExchangeUser(exchangeUser);
    expectMessage("userId=80");

    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505");

    assertMessages();

    Position position = exchangeUser.getPositionArr()[USDT]; // 3 is the fee instrument id corresponding to the calculated fee

    Assert.assertEquals(1010998, position.getQuantity());
  }

  // Buy order submission should have no fee
  @Test
  public void buyOrderSubmission() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));


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

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    assertMessages();
  }

  @Test
  public void percentFeeExactMatchSellOrderToBuyOrderOnBook() {
    beforePercentFeeCalculation();

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

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

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 1000, Side.SELL, DAY));

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

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

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
    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 1000, Side.SELL, DAY));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));

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
    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));


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

    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

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
    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

    // change user fee tier
    user.setFeeTier(2);

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

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
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");
    assertMessages();
  }

  @Test
  public void userFeeTierChangeAfterSellOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill sell order
    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 1000, Side.SELL, DAY));

    // change User tier
    user.setFeeTier(2);

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));

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
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");

    assertMessages();
  }

  @Test
  public void tierPriceChangeAfterBuyOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill buy order
    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 1000, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

    // change tier price
    pair.setFee(new Fee(BTC_USDT_F, USDT, 200, FeeType.PERCENT, MakerTaker.ALL, 1, true));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

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
        "orderId=3, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");
    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");
    assertMessages();
  }

  @Test
  public void tierPriceChangeAfterSellOrderPartialFill() {
    beforePercentFeeCalculation();

    // partially fill sell order
    orderBook.addOrder(createOrder(1, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, user, BTC_USDT_F, 1011, 1000, Side.SELL, DAY));

    // change tier price
    pair.setFee(new Fee(BTC_USDT_F, USDT, 200, FeeType.PERCENT, MakerTaker.ALL, 1, true));

    // fill the partially filled order
    orderBook.addOrder(createOrder(3, user, BTC_USDT_F, 1011, 500, Side.BUY, DAY));

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
        "orderId=3, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=1000, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-1010999");

    assertMessages();
  }

  @Test
  public void percentFeeOnSellerWithPositivePosition() {
    beforePercentFeeCalculation();

    User buyer = createUser(31);
    buyer.addPosition(BTC_USDT_F, 10000000_000000L);
    expectMessage("userId=31");
    buyer.setFeeTier(1);

    User seller = createUser(32);
    seller.addPosition(BTC_USDT_F, 10000000_000000L);
    expectMessage("userId=32");
    seller.setFeeTier(1);

    beforePercentFeeCalculation();
    orderBook.addOrder(createOrder(1, buyer, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, seller, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");
    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW, feePositionQuantityChange=0");

    expectMessage(
        "orderId=2, ordType=LIMIT, side=SELL, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505499");

    expectMessage(
        "orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=0, execType=TRADE, ordStatus=FILLED, feePositionQuantityChange=-505499");

    assertMessages();
  }

  @Test
  public void percentFeeBuyerWithNegativePosition() {
    beforePercentFeeCalculation();

    User buyer = createUser(25);
    buyer.addPosition(USDT, 10000000_000000L);
    buyer.addPosition(BTC_USDT_F, -1250);
    expectMessage("userId=25");
    buyer.setFeeTier(1);

    User seller = createUser(26);
    seller.addPosition(USDT, 10000_000000L);
    expectMessage("userId=26");
    seller.setFeeTier(1);

    beforePercentFeeCalculation();
    orderBook.addOrder(createOrder(1, buyer, BTC_USDT_F, 1011, 500, Side.BUY, DAY));
    orderBook.addOrder(createOrder(2, seller, BTC_USDT_F, 1011, 500, Side.SELL, DAY));

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
}
