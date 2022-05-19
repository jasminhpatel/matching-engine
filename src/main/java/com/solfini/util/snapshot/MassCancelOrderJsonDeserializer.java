package com.solfini.util.snapshot;

import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.user.UserCache;

public class MassCancelOrderJsonDeserializer implements JsonDeserializer<MassCancelOrder> {

  @Override
  public MassCancelOrder deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    MassCancelOrder massCancelOrder = new MassCancelOrder();
    massCancelOrder.setSnapId(json.get("snapId").getAsLong());
    massCancelOrder.setAccount(json.get("account").getAsInt());
    massCancelOrder.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    massCancelOrder.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    massCancelOrder.setOrigOrderId(json.get("origOrderId").getAsLong());
    massCancelOrder.setSecurityId(json.get("securityId").getAsInt());
    massCancelOrder.setSecondaryOrderId(json.get("secondaryOrderId").getAsLong());
    massCancelOrder.setCancelId(json.get("cancelId").getAsLong());
    massCancelOrder.setCancelPriority(json.get("cancelPriority").getAsLong());
    massCancelOrder.setClOrdId(json.get("clOrdId").getAsString());
    massCancelOrder.setUser(UserCache.get(massCancelOrder.getAccount()));
    massCancelOrder.setSenderCompId((json.get("senderCompAsString") != null ? json.get("senderCompAsString").getAsString() : ""));
    massCancelOrder.setOrdType(OrdType.valueOf(json.get("ordType").getAsString()));
    massCancelOrder.setType(json.get("type").getAsInt());

    return massCancelOrder;
  }
}
