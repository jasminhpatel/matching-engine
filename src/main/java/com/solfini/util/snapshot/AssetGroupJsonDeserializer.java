package com.solfini.util.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.sbe.encoder.UpdateType;

import java.lang.reflect.Type;

public class AssetGroupJsonDeserializer implements JsonDeserializer<AssetGroup> {
  @Override
  public AssetGroup deserialize(final JsonElement element, final Type type, final JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {
    final JsonObject json = element.getAsJsonObject();
    final AssetGroup message = new AssetGroup();
    message.setId(json.get("id").getAsLong());
    message.setOwnerUserId(json.get("ownerUserId").getAsLong());
    message.setGroupAssetId(json.get("groupAssetId").getAsLong());
    message.setName(json.get("name").getAsString());
    message.setUpdateType(UpdateType.PUT);
    if (json.has("assetIdGroupTreeSet")) {
      final JsonArray assetIdGroupArray = json.get("assetIdGroupTreeSet").getAsJsonArray();
      for (int i = 0; i < assetIdGroupArray.size(); ++i) {
        JsonArray assetIdGroup = assetIdGroupArray.get(i).getAsJsonArray();
        message.addAssetId(assetIdGroup.get(0).getAsLong(), assetIdGroup.get(1).getAsInt());
      }
    }

    return message;
  }
}
