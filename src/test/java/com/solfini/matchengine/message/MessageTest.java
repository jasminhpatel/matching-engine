package com.solfini.matchengine.message;

import java.text.NumberFormat;
import java.util.Random;

import com.solfini.common.AdminMessage;
import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.schema.PayloadType;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.pool.OrderObjectPool;
import com.solfini.user.User;
import org.junit.Assert;
import org.junit.Test;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class MessageTest {

  static {
    NumberFormat.getInstance().setGroupingUsed(true);
  }

  protected static String format(final long value) {
    return NumberFormat.getInstance().format(value);
  }

  protected static String format(final double value) {
    return NumberFormat.getInstance().format(value);
  }

  protected static void createInstruments() {
    final Instrument base = new Instrument(1, "BTC", "BTC", (short) 6, (short) 6, 3500, 1000, 0,false, 1);
    final Instrument quoted = new Instrument(2, "USD", "USD", (short) 6, (short) 6, 1, 1000, 0,false, 2);
    InstrumentCache.addInstrument(base);
    InstrumentCache.addInstrument(quoted);

    final InstrumentPair instrument =
        new InstrumentPair(3, "BTC/USD", "BTC/USD", base, quoted, (short) 6, (short) 6, 0, AssetType.PAIR, 10, 20, 3500, 0);
    InstrumentCache.addPair(instrument);
  }

  protected Order makeOrder(final User user, final InstrumentPair instrument) {
    final Random random = new Random();
    final int account = 18;
    long orderId = 1;
    try {
      final int orderType = random.nextInt(2);
      final int side = orderType == Constants.BUY_LIMIT ? 1 : 2;
      final long quantity = 1 + random.nextInt(100_000);

      long price = 0;
      if (orderType == Constants.BUY_LIMIT) {
        price = 1 + random.nextInt(1_050_000);
      } else {
        price = 1_000_000 + random.nextInt(1_000_000);
      }

      final Order order = OrderObjectPool.get();
      order.setSenderCompId("test");
      order.setAccount(account);
      order.setUser(user);
      order.setOrderId(orderId);
      order.setOrderPriority(1);
      order.setType(orderType);
      order.setPriceInt((int) price);
      order.setQuantityLong(quantity);
      order.setQuantityOrigLong(quantity);
      order.setSecurityId(instrument.getId());
      order.setClOrdId("clientOrder0");

      final OrdType ordType = OrdType.LIMIT;
      final Side sideType = side == 1 ? Side.BUY : Side.SELL;
      order.setPrice(price, instrument.getPriceScale());
      order.setQty(quantity, instrument.getQuantityScale());
      order.setOrdType(ordType);
      order.setSide(sideType);

      return order;
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }

    return null;
  }

  class TestMessage extends Message {

    @Override
    public PayloadType getPayloadType() {
      return null;
    }

    @Override
    public MessageType getMessageType() {
      return null;
    }

    @Override
    public String toJSON() {
      return null;
    }

    @Override
    public StringBuilder appendTo(StringBuilder s) {
      // TODO Auto-generated method stub
      return null;
    }
  }

  @Test
  public void messageFieldsAndMethods() {
    Message message = new TestMessage();
    Assert.assertNull(message.getError());
    Assert.assertEquals(0, message.getMatchTime());
    Assert.assertEquals(0, message.getPersistTime());

    message.setError("Error message");
    message.setMatchTime(1000);
    message.setPersistTime(2000);

    Assert.assertEquals("Error message", message.getError());
    Assert.assertEquals(1000, message.getMatchTime());
    Assert.assertEquals(2000, message.getPersistTime());

    message.onEncodeBufferedPublish();
    message.onPublish();
  }

  class TestAdminMessage extends AdminMessage {

    @Override
    public PayloadType getPayloadType() {
      return null;
    }

    @Override
    public MessageType getMessageType() {
      return null;
    }

    @Override
    public String toJSON() {
      return null;
    }

    @Override
    public StringBuilder appendTo(StringBuilder s) {
      // TODO Auto-generated method stub
      return null;
    }
  }

  @Test
  public void adminMessageFields() {
    AdminMessage message = new TestAdminMessage();
    Assert.assertEquals(0, message.getConnectionId());

    message.setConnectionId(100);
    Assert.assertEquals(100, message.getConnectionId());
  }
}
