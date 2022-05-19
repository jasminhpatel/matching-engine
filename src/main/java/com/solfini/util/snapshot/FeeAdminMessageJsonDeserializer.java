package com.solfini.util.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import java.lang.reflect.Type;

public class FeeAdminMessageJsonDeserializer implements JsonDeserializer<FeeAdminMessage> {

  @Override
  public FeeAdminMessage deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    FeeAdminMessage message = new Gson().fromJson(json, FeeAdminMessage.class);
    message.setUpdateType(UpdateType.PUT);
    message.setFeeType(FeeType.get(json.get("feeType").getAsShort()));
    message.setMakerTaker(MakerTaker.get(json.get("makerTaker").getAsShort()));

    message.setExternalId(json.get("externalId").getAsLong());
    message.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    message.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    message.setSenderInstanceId(json.has("senderInstanceId") ? json.get("senderInstanceId").getAsString() : "");

    return message;
  }
}
