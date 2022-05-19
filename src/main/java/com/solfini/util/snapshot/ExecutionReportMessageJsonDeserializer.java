package com.solfini.util.snapshot;

import com.google.gson.*;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.ExecRestatementReason;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;
import java.lang.reflect.Type;

public class ExecutionReportMessageJsonDeserializer implements JsonDeserializer<ExecutionReportMessage> {

  @Override
  public ExecutionReportMessage deserialize(final JsonElement element, final Type type, final JsonDeserializationContext context) {

    JsonObject json = element.getAsJsonObject();

    Order order = new Order();
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
    order.setSubmitterId(json.get("submitterId").getAsInt());
    order.setOrdType(OrdType.valueOf(json.get("ordType").getAsString()));
    order.setType(json.get("type").getAsInt());
    order.setPriceInt(json.get("priceInt").getAsInt());
    order.setQuantityLong(json.get("quantityLong").getAsLong());
    order.setQuantityOrigLong(json.get("quantityOrigLong").getAsLong());
    order.setTimeInForce(TimeInForce.valueOf(json.get("timeInForce").getAsString()));
    order.setExpireTime(json.get("expireTime").getAsLong());
    order.setStopPx(json.get("stopPx").getAsLong(), json.get("stopPx_scale").getAsShort());
    order.setStopPxInt(json.get("stopPxInt").getAsInt());
    order.setToClose(json.get("toClose").getAsBoolean());
    order.setFeeAccumulatedQuantity(json.get("feeAccumulatedQuantity").getAsLong());
    order.setFeeEstimatedQuantity(json.get("feeEstimatedQuantity").getAsLong());
    order.setAvailableEstimatedQuantity(json.get("availableEstimatedQuantity").getAsLong());
    order.setAvailableAccumulatedQuantity(json.get("availableAccumulatedQuantity").getAsLong());
    order.setUser(UserCache.get(order.getAccount()));

    InstrumentPair instrument = InstrumentCache.getPair(order.getSecurityId());
    ExecutionReportMessage message =
        ExecutionReportMessage.createRestateExecutionReport(order, instrument, ExecRestatementReason.OTHER, order.getSnapId(), order);

    message.setUser(UserCache.get(order.getAccount()));
    message.setSourceSeqNum(json.get("sourceSeqNum").getAsLong());
    message.setKafkaRecordOffset(json.get("kafkaRecordOffset").getAsLong());

    return message;
  }
}
