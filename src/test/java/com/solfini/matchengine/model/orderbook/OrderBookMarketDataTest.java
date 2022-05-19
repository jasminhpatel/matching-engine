package com.solfini.matchengine.model.orderbook;

import static com.solfini.sbe.encoder.TimeInForce.DAY;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import com.solfini.matchengine.message.outbound.MarketDataSnapMessage;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.Side;

public class OrderBookMarketDataTest extends OrderBookTest {

  // Add buy order, assert
  @Test
  public void addBuyOrderMarketDataSnapMessage(){
    orderBook.addOrder(createOrder(1, user, pair.getId(), 1011, 500, Side.BUY, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=BUY, price=1011, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1012, 500, Side.SELL, DAY));
    expectMessage("orderId=1, ordType=LIMIT, side=SELL, price=1012, orderQty=500, leavesQty=500, ordStatus=NEW");
    assertMessages();


    MarketDataSnapMessage marketDataSnapMessage = MarketDataSnapMessage.create(pair, new SessionInfo());
    marketDataSnapMessage.build();
    assertTrue(marketDataSnapMessage.toString().length() > 0);
    assertTrue(marketDataSnapMessage.toJSON().length() > 0);

    System.out.println("marketDataSnapMessage=" + marketDataSnapMessage);
  }

}
