package com.solfini.util.snapshot;

import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.matchengine.message.internal.CancelReplaceOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class CancelReplaceOrderJsonDeserializer implements JsonDeserializer<CancelReplaceOrder> {

  @Override
  public CancelReplaceOrder deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();
    String senderCompId = (json.get("senderCompAsString") != null ? json.get("senderCompAsString").getAsString() : "");

    Order order = new Order();
    order.setSecurityId(json.get("securityId").getAsInt());
    order.setOrderId(json.get("orderId").getAsLong());
    order.setClOrdId(json.get("clOrdId").getAsString());
    order.setSecondaryOrderId(json.get("secondaryOrderId").getAsLong());
    order.setPrice(json.get("price2").getAsLong(), json.get("price2_scale").getAsShort());
    order.setQty(json.get("qty2").getAsLong(), json.get("qty2_scale").getAsShort());
    order.setSide(Side.valueOf(json.get("side").getAsString()));
    order.setSenderCompId(senderCompId);
    order.setAccount(json.get("account").getAsInt());
    order.setOrdType(OrdType.valueOf(json.get("ordType").getAsString()));
    order.setType(json.get("type").getAsInt());
    order.setUser(UserCache.get(order.getAccount()));
    order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);

    CancelReplaceOrder cancelReplaceOrder = new CancelReplaceOrder();
    cancelReplaceOrder.setOrder(order);

    cancelReplaceOrder.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    cancelReplaceOrder.setSnapId(json.get("snapId").getAsLong());
    cancelReplaceOrder.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    cancelReplaceOrder.setSecurityId(json.get("securityId").getAsInt());
    cancelReplaceOrder.setPrice(json.get("price").getAsLong(), json.get("price_scale").getAsShort());
    cancelReplaceOrder.setPrice2(new DecimalFloat(json.get("price2").getAsLong(), json.get("price2_scale").getAsInt()).value());
    cancelReplaceOrder.setQty(json.get("qty").getAsLong(), json.get("qty_scale").getAsShort());
    cancelReplaceOrder.setQty2(new DecimalFloat(json.get("qty2").getAsLong(), json.get("qty2_scale").getAsInt()).value());
    cancelReplaceOrder.setSide(Side.valueOf(json.get("side").getAsString()));
    cancelReplaceOrder.setOrigOrderId(json.get("origOrderId").getAsLong());
    cancelReplaceOrder.setNewOrderId(json.get("newOrderId").getAsLong());
    cancelReplaceOrder.setSecondaryOrderId(json.get("secondaryOrderId").getAsLong());
    cancelReplaceOrder.setCancelId(json.get("cancelId").getAsLong());
    cancelReplaceOrder.setCancelPriority(json.get("cancelPriority").getAsLong());
    cancelReplaceOrder.setClOrdId(json.get("clOrdId").getAsString());
    cancelReplaceOrder.setSenderCompId(senderCompId);
    cancelReplaceOrder.setAccount(json.get("account").getAsInt());
    cancelReplaceOrder.setOrdType(OrdType.valueOf(json.get("ordType").getAsString()));
    cancelReplaceOrder.setType(json.get("type").getAsInt());
    cancelReplaceOrder.setQuantityLong(json.get("quantityLong").getAsLong());
    cancelReplaceOrder.setQuantityOrigLong(json.get("quantityOrigLong").getAsLong());
    cancelReplaceOrder.setPriceInt(json.get("priceInt").getAsInt());
    cancelReplaceOrder.setPrice2Int(json.get("price2Int").getAsInt());
    cancelReplaceOrder.setUser(UserCache.get(cancelReplaceOrder.getAccount()));

    return cancelReplaceOrder;
  }
}
