package com.solfini.util.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import java.lang.reflect.Type;

public class BalanceAdminMessageJsonDeserializer implements JsonDeserializer<BalanceAdminMessage> {

  @Override
  public BalanceAdminMessage deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    final BalanceAdminMessage message = new Gson().fromJson(json, BalanceAdminMessage.class);
    message.setUpdateType(UpdateType.PUT);
    message.setRequestStatus(RequestStatus.get(json.get("requestStatus").getAsShort()));

    final JsonArray jsonPositionArr = json.get("positionArr").getAsJsonArray();
    final Position[] positionArr = new Position[Math.max(jsonPositionArr.size(), 64)];
    for (int i = 0; i < jsonPositionArr.size(); ++i) {
      JsonObject jsonPosition = jsonPositionArr.get(i).getAsJsonObject();
      final Position position = new Gson().fromJson(jsonPosition, Position.class);
      position.setAssetType(AssetType.get(jsonPosition.get("assetType").getAsShort()));
      position.setUsdAvgCostBasis((long) jsonPosition.get("usdAvgCostBasis").getAsDouble() * Position.DEFAULT_COST_BASIS_SCALE_MULT);
      positionArr[i] = position;
    }
    message.setPositionArr(positionArr);
    message.setPositionsLength(positionArr.length);


    for (int i = 0; i < positionArr.length; i++) {
      if (positionArr[i] != null) {
        final Instrument instrument = InstrumentCache.get(positionArr[i].getInstrumentId());
        int scale = instrument != null ? instrument.getQuantityScale() : 0;
        if (instrument == null) {
          final InstrumentPair pair = InstrumentCache.getPair(positionArr[i].getInstrumentId());
          scale = pair != null ? pair.getQuantityScale() : 0;
        }

        final Balance balance = new Balance();
        balance.set(positionArr[i], scale);
        message.addBalance(balance);
      }
    }

    message.setExternalId(json.get("externalId").getAsLong());
    message.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    message.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    message.setSenderInstanceId(json.has("senderInstanceId") ? json.get("senderInstanceId").getAsString() : "");

    return message;
  }
}
