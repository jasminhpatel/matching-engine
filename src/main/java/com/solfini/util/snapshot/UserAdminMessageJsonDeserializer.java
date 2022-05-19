package com.solfini.util.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.solfini.instrument.Balance;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import uk.co.real_logic.artio.fields.DecimalFloat;
import java.lang.reflect.Type;

public class UserAdminMessageJsonDeserializer implements JsonDeserializer<UserAdminMessage> {

  @Override
  public UserAdminMessage deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    UserAdminMessage message = new Gson().fromJson(json, UserAdminMessage.class);
    message.setUpdateType(UpdateType.PUT);
    message.setPassword("PASSWORD");
    message.setRequestStatus(RequestStatus.get(json.get("requestStatus").getAsShort()));

    // --positionArr?

    JsonArray jsonBalanceList = json.get("balanceList").getAsJsonArray();
    for (int i = 0; i < jsonBalanceList.size(); ++i) {
      JsonObject jsonBalance = jsonBalanceList.get(i).getAsJsonObject();
      Balance balance = new Gson().fromJson(jsonBalance, Balance.class);
      DecimalFloat temp = toDecimalFloat(jsonBalance, "balance");
      balance.setBalance(temp.value(), temp.scale());
      temp = toDecimalFloat(jsonBalance, "balance_change");
      balance.setBalanceChange(temp.value(), temp.scale());
      message.addBalance(balance);
    }

    message.setExternalId(json.get("externalId").getAsLong());
    message.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    message.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());
    message.setSenderInstanceId(json.has("senderInstanceId") ? json.get("senderInstanceId").getAsString() : "");

    return message;
  }

  private DecimalFloat toDecimalFloat(final JsonObject json, final String field) {
    JsonArray jsonField = json.get(field).getAsJsonArray();
    return new DecimalFloat(jsonField.get(0).getAsLong(), jsonField.get(1).getAsInt());
  }
}
