package com.solfini.util.snapshot;

import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;

public class OrderJsonDeserializer implements JsonDeserializer<Order> {

  @Override
  public Order deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    Order order = new Order();
    order.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    order.setSecurityId(json.get("securityId").getAsInt());
    order.setPrice(json.get("price").getAsLong(), json.get("price_scale").getAsShort());
    order.setPrice2(json.get("price2").getAsLong(),
        json.get("price2_scale") != null ? json.get("price2_scale").getAsShort() : json.get("price_scale").getAsShort());
    order.setQty(json.get("qty").getAsLong(), json.get("qty_scale").getAsShort());
    order.setSide(Side.valueOf(json.get("side").getAsString()));
    order.setOrderId(json.get("orderId").getAsLong());
    order.setSecondaryOrderId(json.get("secondaryOrderId").getAsLong());
    order.setOrderPriority(json.get("orderPriority").getAsLong());
    order.setClOrdId(json.get("clOrdId").getAsString());
    order.setSenderCompId(json.get("senderCompAsString").getAsString());
    order.setAccount(json.get("account").getAsInt());
    order.setOrdType(OrdType.valueOf(json.get("ordType").getAsString()));
    order.setType(json.get("type").getAsInt());
    order.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    order.setPriceInt(json.get("priceInt").getAsInt());
    order.setQuantityLong(json.get("quantityLong").getAsLong());
    order.setQuantityOrigLong(json.get("quantityOrigLong").getAsLong());
    order.setTimeInForce(TimeInForce.valueOf(json.get("timeInForce").getAsString()));
    order.setExpireTime(!json.get("expireTime").equals(JsonNull.INSTANCE) ? json.get("expireTime").getAsLong() : 0L);
    order.setStopPx(json.get("stopPx").getAsLong(), json.get("stopPx_scale").getAsShort());
    order.setStopPxInt(json.get("stopPxInt").getAsInt());
    order.setToClose(json.get("toClose").getAsBoolean());
    order.setUser(UserCache.get(order.getAccount()));
    order.setSnapId(json.get("snapId").getAsLong());

    return order;
  }
}
