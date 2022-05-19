package com.solfini.util.snapshot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class JsonReader {
  private final BufferedReader reader;

  public JsonReader(final String file) throws IOException {
    reader = new BufferedReader(new FileReader(new File(file)));
  }

  public JsonObject next() throws IOException {
    String line = reader.readLine();
    if (null == line) {
      return null;
    }

    JsonObject object = new JsonParser().parse(line).getAsJsonObject();

    final String type = object.get("class").getAsString();
    if (type.equals("BalanceAdminMessage")) {
      JsonArray positions = object.get("positionArr").getAsJsonArray();
      int i = 0;
      while (i < positions.size()) {
        JsonObject position = positions.get(i).getAsJsonObject();
        if ((position.get("quantity").getAsLong() == 0) && (position.get("availableQuantity").getAsLong() == 0)) {
          positions.remove(i);
        } else {
          position.remove("bankruptPriceInt");
          position.remove("quotedUsdMark");
          position.remove("settleCoinRealized");
          position.remove("settleCoinUnrealized");
          position.remove("settleCoinUsdMark");
          position.remove("usdAvgCostBasis");
          position.remove("usdRealized");
          position.remove("usdUnrealized");
          position.remove("usdValue");
          ++i;
        }
      }
    } else if (type.equals("Order")) {
      object.remove("marginCheckReferencePrice");
      object.remove("senderCompIdCharArr");
      object.remove("senderCompAsString");
    } else if (type.equals("SecurityDefinitionAdminMessage")) {
      object.remove("indexFeedUsdMark");
      object.remove("auctionFixingAttempts");
    } else if (type.equals("SnapResponseAdminMessage")) {
      object.remove("inputKafkaRecordOffset");
      object.remove("outputKafkaRecordOffset");
      object.remove("routeToDestination");
    }

    for (String field : new String[] { "kafkaRecordOffset", "sequenceNumber", "snapId", "sourceSeqNum" }) {
      if (object.has(field)) {
        object.remove(field);
      }
    }

    return object;
  }
}
