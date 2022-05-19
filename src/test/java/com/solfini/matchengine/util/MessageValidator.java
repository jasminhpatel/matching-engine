package com.solfini.matchengine.util;

import static org.junit.Assert.assertEquals;
import java.util.List;

import com.solfini.instrument.Balance;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder.BalanceGroupDecoder;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.NewOrderSingleDecoder;

/**
 *
 * @author Chris Mack
 *
 */
public class MessageValidator {


  public static void validateOrder(Order order, NewOrderSingleDecoder newOrderSingleDecoder) {

    assertEquals(new String(order.getClOrdId()), newOrderSingleDecoder.clOrdID());
    assertEquals(order.getSecurityId(), newOrderSingleDecoder.securityId());
    // assertEquals() TODO TransactionTime
    assertEquals(order.getSide(), newOrderSingleDecoder.side());
    assertEquals(order.getOrdType(), newOrderSingleDecoder.ordType());
    assertEquals(order.getAccount(), newOrderSingleDecoder.userId());
    assertEquals(order.getPrice(), newOrderSingleDecoder.price());
    assertEquals(order.getPriceScale(), newOrderSingleDecoder.priceScale());
    assertEquals(order.getQty(), newOrderSingleDecoder.qty());
    assertEquals(order.getQtyScale(), newOrderSingleDecoder.qtyScale());
    // assertEquals(new String(order.getSenderCompId()), newOrderSingleDecoder.header().senderCompIDAsString());
  }

  public static void validateUserAdminMessage(UserAdminMessage userAdminMessage, UserAdminMessageDecoder userAdminMessageDecoder) {

    assertEquals(userAdminMessage.getUpdateType(), userAdminMessageDecoder.updateType());
    assertEquals(userAdminMessage.getUserId(), userAdminMessageDecoder.userId());
    assertEquals(userAdminMessage.getUsername(), userAdminMessageDecoder.username());
    assertEquals(userAdminMessage.getPassword(), userAdminMessageDecoder.password());
    assertEquals(userAdminMessage.getFirmId(), userAdminMessageDecoder.firmId());

    List<Balance> balanceList = userAdminMessage.getBalanceList();

    int i = 0;
    for (BalanceGroupDecoder balanceGroupDecoder : userAdminMessageDecoder.balanceGroup()) {

      Balance balance = balanceList.get(i++);
      assertEquals(balance.getAssetId(), balanceGroupDecoder.assetId());
      assertEquals(balance.getBalance().value(), balanceGroupDecoder.balance().value());
      assertEquals(balance.getBalance().scale(), balanceGroupDecoder.balance().scale());

    }


  }

}
