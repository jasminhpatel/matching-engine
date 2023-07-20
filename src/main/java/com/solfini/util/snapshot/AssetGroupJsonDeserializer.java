package com.solfini.util.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.sbe.encoder.TokenType;
import com.solfini.sbe.encoder.UpdateType;

import java.lang.reflect.Type;

public class AssetGroupJsonDeserializer implements JsonDeserializer<AssetGroup> {
  @Override
  public AssetGroup deserialize(final JsonElement element, final Type type, final JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {
    final JsonObject json = element.getAsJsonObject();
    final AssetGroup message = new AssetGroup();
    message.setUpdateType(UpdateType.PUT);
    message.setId(json.get("id").getAsLong());
    message.setOwnerUserId(json.get("ownerUserId").getAsLong());
    message.setQuantity(json.get("quantity").getAsLong());
    if (json.has("name"))
      message.setName(json.get("name").getAsString());
    message.setSecurityId(json.get("securityId").getAsLong());
    message.setAssetId(json.get("assetId").getAsLong());
    message.setTokenType(TokenType.get(json.get("tokenType").getAsShort()));

    if (json.has("groups")) {
      final JsonArray assetIdGroupArray = json.get("groups").getAsJsonArray();
      for (int i = 0; i < assetIdGroupArray.size(); ++i) {
        JsonArray assetIdGroup = assetIdGroupArray.get(i).getAsJsonArray();
        message.addAssetIdToList(assetIdGroup.get(0).getAsLong(), assetIdGroup.get(1).getAsInt());
      }
    }
    System.out.println(message.toJSON());
    return message;
  }
}
