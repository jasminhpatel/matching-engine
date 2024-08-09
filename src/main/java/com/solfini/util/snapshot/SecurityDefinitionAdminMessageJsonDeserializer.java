package com.solfini.util.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.internal.admin.schema.*;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import java.lang.reflect.Type;

public class SecurityDefinitionAdminMessageJsonDeserializer implements JsonDeserializer<SecurityDefinitionAdminMessage> {

  @Override
  public SecurityDefinitionAdminMessage deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    SecurityDefinitionAdminMessage message = new Gson().fromJson(json, SecurityDefinitionAdminMessage.class);
    message.setUpdateType(UpdateType.PUT);
    message.setAssetType(AssetType.get(json.get("assetType").getAsShort()));
    message.setMarketStatus(MarketStatus.get(json.get("marketStatus").getAsShort()));
    message.setMarketType(MarketType.get(json.get("marketType").getAsShort()));
    message.setSector(Sector.get(json.get("sector").getAsShort()));

    // base?
    // quoted?

    message.setExternalId(json.get("externalId").getAsLong());
    message.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    message.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    message.setSenderInstanceId(json.has("senderInstanceId") ? json.get("senderInstanceId").getAsString() : "");

    return message;
  }
}
