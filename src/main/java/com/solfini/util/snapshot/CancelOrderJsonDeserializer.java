package com.solfini.util.snapshot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.matchengine.message.internal.CancelOrder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.UserCache;
import java.lang.reflect.Type;

public class CancelOrderJsonDeserializer implements JsonDeserializer<CancelOrder> {

  @Override
  public CancelOrder deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    CancelOrder cancelOrder = new CancelOrder();
    cancelOrder.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    cancelOrder.setSecurityId(json.get("securityId").getAsInt());
    cancelOrder.setPrice(json.get("price").getAsLong(), (short) 2);
    cancelOrder.setPriceInt(json.get("priceInt").getAsInt());
    cancelOrder.setQty(json.get("qty").getAsLong(), (short) json.get("qty_scale").getAsInt());
    cancelOrder.setSide(Side.valueOf(json.get("side").getAsString()));
    cancelOrder.setOrigOrderId(json.get("origOrderId").getAsLong());
    cancelOrder.setSecondaryOrderId(json.get("secondaryOrderId").getAsLong());
    cancelOrder.setCancelId(json.get("cancelId").getAsLong());
    cancelOrder.setCancelPriority(json.get("cancelPriority").getAsLong());
    cancelOrder.setClOrdId(json.get("clOrdId").getAsString());
    cancelOrder.setSenderCompId(json.get("senderCompAsString").getAsString());
    cancelOrder.setAccount(json.get("account").getAsInt());
    cancelOrder.setOrdType(OrdType.valueOf(json.get("ordType").getAsString()));
    cancelOrder.setType(json.get("type").getAsInt());
    cancelOrder.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    cancelOrder.setQuantityLong(json.get("quantityLong").getAsLong());
    cancelOrder.setQuantityOrigLong(json.get("quantityOrigLong").getAsLong());
    cancelOrder.setUser(UserCache.get(cancelOrder.getAccount()));
    cancelOrder.setSnapId(json.get("snapId").getAsLong());

    return cancelOrder;
  }
}
