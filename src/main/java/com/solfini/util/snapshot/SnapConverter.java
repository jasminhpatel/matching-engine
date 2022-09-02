package com.solfini.util.snapshot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;
import com.solfini.internal.admin.schema.FIXUserAdminMessageDecoder;
import com.solfini.internal.admin.schema.FeeAdminMessageDecoder;
import com.solfini.internal.admin.schema.FundingRateCalcAdminMessageDecoder;
import com.solfini.internal.admin.schema.SecurityDefinitionAdminMessageDecoder;
import com.solfini.internal.admin.schema.SnapResponseAdminMessageDecoder;
import com.solfini.internal.admin.schema.TradeStateAdminMessageDecoder;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageDecoder;
import com.solfini.matchengine.decoder.InboundAdminMessageHandler;
import com.solfini.matchengine.drmode.ExecutionReportParser;
import com.solfini.matchengine.drmode.PositionReportParser;
import com.solfini.matchengine.kafka.KafkaPublisher;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.SnapResponseAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;

/**
 * Export: Binary to JSON Import: JSON to Binary
 */
public class SnapConverter implements Constants {

  private static final int ADMIN_API_OFFSET = 19;
  private static final String TRANSFORM_ETH_QUANTITY_SCALE_6_TO_8 = "eth-quantity-scale-6-to-8";
  private static final String TRANSFORM_BTC_QUANTITY_SCALE_6_TO_8 = "btc-quantity-scale-6-to-8";
  private static final String TRANSFORM_USDC_QUANTITY_SCALE_2_TO_6 = "usdc-quantity-scale-2-to-6";
  private static final String TRANSFORM_DERIVE_ASSET_TYPES = "derive-asset-types";
  private static final String TRANSFORM_MOVE_USD_TO_USDC = "move-usd-to-usdc";
  private static final String TRANSFORM_RESET_OPTION_MARK = "reset-option-mark";
  private static final String TRANSFORM_FIX_USD_SCALING = "fix-usd-scaling";

  private final boolean debug;
  private final boolean prune;
  private final boolean clean;
  private final Statistics statistics = new Statistics();
  private final SnapValidator validator = new SnapValidator();
  private final ExecutionReportParser executionReportParser = new ExecutionReportParser();
  private final PositionReportParser positionReportParser = new PositionReportParser();

  private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();
  private final PositionReportDecoder positionReportDecoder = new PositionReportDecoder();
  private final AssetGroupDecoder assetGroupDecoder = new AssetGroupDecoder();
  private final SnapTransformer transformer;
  private final Set<String> transforms = new HashSet<>();

  public SnapConverter() {
    this(false, false, false, null);
  }

  public SnapConverter(final boolean debug, final boolean prune, final boolean clean, final SnapTransformer transformer) {
    this.debug = debug;
    this.prune = clean || prune;
    this.clean = clean;
    this.transformer = transformer;

    Context.getMessagePublisher().publishToKafka(false);
  }

  public void addTransform(final String transform) {
    transforms.add(transform);
    System.out.println("transform: " + transform);
  }

  public static class Statistics {
    private final Map<String, Integer> counts = new HashMap<>();
    private long start = System.currentTimeMillis();
    private long total = 0;

    public void reset() {
      start = System.currentTimeMillis();
    }

    public void count(final String type) {
      total++;
      counts.put(type, counts.containsKey(type) ? counts.get(type) + 1 : 1);

      if (total % 100_000 == 0) {
        print(false);
      }
    }

    public void print(final boolean summary) {
      final NumberFormat format = NumberFormat.getInstance();
      format.setGroupingUsed(true);

      final long duration = System.currentTimeMillis() - start;
      if (duration != 0) {
        System.out.println(
            format.format(total) + " records: " + format.format(duration) + " ms (" + format.format(total * 1_000 / duration) + " rec/s)");
      }

      if (summary) {
        System.out.println("Summary: " + format.format(total) + " total messages");

        final List<String> keys = new ArrayList<>(counts.keySet());
        Collections.sort(keys);
        for (final String key : keys) {
          System.out.println("  " + key + ": " + format.format(counts.get(key)));
        }
      }
    }
  }

  public void exportSnapshot(final String snapshot, final String json) throws IOException {
    System.out.println("EXPORT: " + snapshot + " -> " + json);

    final BufferedWriter writer = new BufferedWriter(new FileWriter(json, false));
    final ChronicleQueue queue = SingleChronicleQueueBuilder.single(snapshot).blockSize(1048576).rollCycle(RollCycles.DAILY).build();
    final ExcerptTailer tailer = queue.createTailer();
    final Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(32768);
    final ByteBuffer buffer = ByteBuffer.allocateDirect(32768);

    ArrayList<Message> snapshotMessages = new ArrayList<>();

    statistics.reset();
    while (true) {
      bytes.clear();
      final boolean read = tailer.readBytes(bytes);

      if (read) {
        final byte[] data = bytes.underlyingObject().array();
        final int len = (int) bytes.readRemaining();

        // seqNum
        long seqNum = 0;
        for (int i = 0; i < 8; i++) {
          seqNum <<= 8;
          seqNum |= (data[i] & 0xFF);
        }

        // sendTime
        long sendTime = 0;
        for (int i = 8; i < 16; i++) {
          sendTime <<= 8;
          sendTime |= (data[i] & 0xFF);
        }

        // messageType
        final byte messageType = data[16];

        buffer.clear();
        buffer.put(data, ADMIN_API_OFFSET, len - ADMIN_API_OFFSET);

        Message message = null;
        if (KafkaPublisher.ADMIN_API == messageType) {
          message = decodeAdminMessage(seqNum, sendTime, buffer, len - ADMIN_API_OFFSET);
          if (message != null) {
            statistics.count(message.getMessageType().name());
            if (MessageType.SNAP_RESPONSE == message.getMessageType()) {
              statistics.print(true);
              snapshotMessages.add(message);
              break;
            }
          }
        } else {
          message = decodeNormalMessage(data, len - ADMIN_API_OFFSET, statistics);
          if (message != null) {
            statistics.count(message.getMessageType().name());
          }
        }

        if (message == null) {
          continue;
        }

        snapshotMessages.add(message);
      }
    }

    // Transform
    if (transformer != null) {
      snapshotMessages = transformer.transform(snapshotMessages);
    }

    // Write JSON
    for (final Message message : snapshotMessages) {

      writer.write(message.toJSON());
      writer.newLine();
    }

    writer.close();
  }

  public boolean validate() {
    return validator.validate();
  }

  private Message decodeAdminMessage(final long seqNum, final long sendTime, final ByteBuffer buffer, final int length) throws IOException {
    final InboundAdminMessageHandler inboundAdminMessageHandler = new InboundAdminMessageHandler();
    final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);
    final com.solfini.internal.admin.schema.MessageHeaderDecoder messageHeaderDecoder =
        new com.solfini.internal.admin.schema.MessageHeaderDecoder();
    messageHeaderDecoder.wrap(unsafeBuffer, 0);

    final int connectionId = 0;
    Message message = null;
    final int templateId = messageHeaderDecoder.templateId();

    switch (templateId) {
      case UserAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeAdminUserUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        break;
      case BalanceAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeBalanceAdminUserUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        break;
      case FeeAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeFeeAdminUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        if (message instanceof FeeAdminMessage)
          ((FeeAdminMessage) message).setUpdateType(UpdateType.PUT);
        break;
      case SecurityDefinitionAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeSecurityDefinitionAdminUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        ((SecurityDefinitionAdminMessage) message).setUpdateType(UpdateType.PUT);
        ((SecurityDefinitionAdminMessage) message).setSnapConverterMode(true);
        break;
      case TradeStateAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeTradeStateAdminUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        break;
      case FIXUserAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeFIXUserAdminUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        break;
      case FundingRateCalcAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeFundingRateCalcAdminUpdate(unsafeBuffer, messageHeaderDecoder, connectionId);
        break;
      case SnapResponseAdminMessageDecoder.TEMPLATE_ID:
        message = inboundAdminMessageHandler.decodeSnapResponseAdminMessage(unsafeBuffer, messageHeaderDecoder, connectionId);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported admin message: templateId=" + templateId);
    }

    if (message != null) {
      final long transactionId = messageHeaderDecoder.transactionId();
      final boolean transactionEnd = (messageHeaderDecoder.transactionEnd() == 1);

      message.setTransactionId(transactionId);
      message.setLastMessageInTransaction(transactionEnd);
    }

    if (message == null) {
      return null;
    } else {
      validator.process(message);
    }

    if (debug) {
      System.out.println("<< " + message);
    }

    if (transforms.contains(TRANSFORM_ETH_QUANTITY_SCALE_6_TO_8)) {
      // set eth quantity scale to 8
      if (message instanceof SecurityDefinitionAdminMessage) {
        final String symbol = ((SecurityDefinitionAdminMessage) message).getSymbol();
        if (symbol.equals("ETH")) {
          ((SecurityDefinitionAdminMessage) message).setQuantityScale((short) 8);
        }
      }
    }

    if (transforms.contains(TRANSFORM_BTC_QUANTITY_SCALE_6_TO_8)) {
      // set eth quantity scale to 8
      if (message instanceof SecurityDefinitionAdminMessage) {
        final String symbol = ((SecurityDefinitionAdminMessage) message).getSymbol();
        if (symbol.equals("BTC")) {
          ((SecurityDefinitionAdminMessage) message).setQuantityScale((short) 8);
        }
      }
    }

    if (transforms.contains(TRANSFORM_USDC_QUANTITY_SCALE_2_TO_6)) {
      // set usdc quantity scale to 6
      if (message instanceof SecurityDefinitionAdminMessage) {
        final String symbol = ((SecurityDefinitionAdminMessage) message).getSymbol();
        if (symbol.equals("USDC")) {
          ((SecurityDefinitionAdminMessage) message).setQuantityScale((short) 6);
        }
      }
    }

    if (transforms.contains(TRANSFORM_DERIVE_ASSET_TYPES)) {
      // derive asset type based on symbol
      if (message instanceof SecurityDefinitionAdminMessage) {
        final String symbol = ((SecurityDefinitionAdminMessage) message).getSymbol();
        if (symbol.contains("[F]")) {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.PERPETUAL_SWAP);
        } else if (symbol.contains("[DF]")) {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.DATED_FUTURE);
        } else if (symbol.contains("[C]")) {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.OPTION_CALL);
        } else if (symbol.contains("[P]")) {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.OPTION_PUT);
        } else if (symbol.contains("[A]")) {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.PAIR);
        } else if (symbol.contains("/")) {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.PAIR);
        } else {
          ((SecurityDefinitionAdminMessage) message).setAssetType(AssetType.ASSET);
        }
      }
    }

    if (transforms.contains(TRANSFORM_RESET_OPTION_MARK)) {
      // set index feed mark to 0 for options
      if (message instanceof SecurityDefinitionAdminMessage) {
        final String symbol = ((SecurityDefinitionAdminMessage) message).getSymbol();
        if (symbol.contains("[C]") || symbol.contains("[P]")) {
          ((SecurityDefinitionAdminMessage) message).setIndexFeedUsdMark(0);
        }
      }
    }

    if (prune) {
      // remove users with userid > 20
      if (message instanceof UserAdminMessage) {
        if (((UserAdminMessage) message).getUserId() > 20) {
          return null;
        }
      }
      // set usd mark to 1.0 for usd, usd pegged stable coins
      if (message instanceof SecurityDefinitionAdminMessage) {
        final String symbol = ((SecurityDefinitionAdminMessage) message).getSymbol();
        if (symbol.equals("USD") || symbol.equals("USDC") || symbol.equals("USDT") || symbol.equals("PAX") || symbol.equals("TUSD")) {
          ((SecurityDefinitionAdminMessage) message).setIndexFeedUsdMark(1.0);
        }
      }
    }

    if (clean) {
      // set secondary order and exec ids to 0
      if (message instanceof SecurityDefinitionAdminMessage) {
        ((SecurityDefinitionAdminMessage) message).setSecondaryOrderId(0);
        ((SecurityDefinitionAdminMessage) message).setSecondaryExecId(0);
      }
      // set order and exec ids to 0
      // set input and output kafka record offsets to 0
      if (message instanceof SnapResponseAdminMessage) {
        ((SnapResponseAdminMessage) message).setOrderId(0);
        ((SnapResponseAdminMessage) message).setExecId(0);
        ((SnapResponseAdminMessage) message).setInputKafkaRecordOffset(0);
        ((SnapResponseAdminMessage) message).setOutputKafkaRecordOffset(0);
      }
    }

    message.onMatcher();

    if (debug) {
      System.out.println(">> " + message.toJSON());
    }

    return message;
  }

  private Message decodeNormalMessage(final byte[] data, final int length, final Statistics statistics) throws IOException {
    // wrap bytes
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, ADMIN_API_OFFSET);

    Message message;
    switch (headerDecoder.templateId()) {
      case ExecutionReportDecoder.TEMPLATE_ID:
        executionReportDecoder.wrap(decoderUnsafeBuffer, ADMIN_API_OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        message = executionReportParser.parse(headerDecoder, executionReportDecoder);

        if (message != null) {
          message.setSenderCompId(headerDecoder.senderCompId());
          message.setSequenceNumber(headerDecoder.msgSeqNum());
          message.setSourceSeqNum(headerDecoder.sourceSeqNum());
          message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
        }

        break;
      case PositionReportDecoder.TEMPLATE_ID:
        positionReportDecoder.wrap(decoderUnsafeBuffer, ADMIN_API_OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        message = positionReportParser.parse(headerDecoder, positionReportDecoder);
        if (message != null) {
          message.setSenderCompId(headerDecoder.senderCompId());
          message.setSequenceNumber(headerDecoder.msgSeqNum());
          message.setSourceSeqNum(headerDecoder.sourceSeqNum());
          message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
        }
        break;
      case AssetGroupDecoder.TEMPLATE_ID:
        assetGroupDecoder.wrap(decoderUnsafeBuffer, ADMIN_API_OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
            headerDecoder.version());

        final AssetGroup assetGroup = new AssetGroup();
        assetGroup.set(assetGroupDecoder);
        message = assetGroup;
        if (message != null) {
          message.setSenderCompId(headerDecoder.senderCompId());
          message.setSequenceNumber(headerDecoder.msgSeqNum());
          message.setSourceSeqNum(headerDecoder.sourceSeqNum());
          message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
        }
        break;
      default:
        throw new UnsupportedOperationException("Unsupported message: data=" + StringUtil.fixToString(data));
    }

    if (message == null) {
      return null;
    } else {
      validator.process(message);
    }

    if (!(message instanceof Order)) {
      message.onMatcher();
    }

    if (debug) {
      System.out.println("<< " + message);
    }

    if (transforms.contains(TRANSFORM_ETH_QUANTITY_SCALE_6_TO_8)) {
      // adjust estimated and accumulated values
      if (message instanceof Order) {
        final Order order = (Order) message;
        final String symbol = InstrumentCache.getPair(order.getSecurityId()).getSymbol();
        if (symbol.contains("/ETH")) {
          order.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity() * 100);
          order.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity() * 100);
          if (order.getSide() == Side.BUY) {
            order.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity() * 100);
            order.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity() * 100);
          }
        }
      }
      // adjust scale on usdc positions
      if (message instanceof BalanceAdminMessage) {
        final int id = InstrumentCache.getBySymbol("ETH").getId();
        Position[] positions = ((BalanceAdminMessage) message).getPositionArr();
        if ((id < positions.length) && (positions[id] != null)) {
          positions[id].setQuantity(positions[id].getQuantity() * 100);
          positions[id].setAvailableQuantity(positions[id].getAvailableQuantity() * 100);
        }
      }
    }

    if (transforms.contains(TRANSFORM_BTC_QUANTITY_SCALE_6_TO_8)) {
      // adjust estimated and accumulated values
      if (message instanceof Order) {
        final Order order = (Order) message;
        final String symbol = InstrumentCache.getPair(order.getSecurityId()).getSymbol();
        if (symbol.contains("/BTC")) {
          order.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity() * 100);
          order.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity() * 100);
          if (order.getSide() == Side.BUY) {
            order.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity() * 100);
            order.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity() * 100);
          }
        }
      }
      // adjust scale on usdc positions
      if (message instanceof BalanceAdminMessage) {
        final int id = InstrumentCache.getBySymbol("BTC").getId();
        Position[] positions = ((BalanceAdminMessage) message).getPositionArr();
        if ((id < positions.length) && (positions[id] != null)) {
          positions[id].setQuantity(positions[id].getQuantity() * 100);
          positions[id].setAvailableQuantity(positions[id].getAvailableQuantity() * 100);
        }
      }
    }

    if (transforms.contains(TRANSFORM_USDC_QUANTITY_SCALE_2_TO_6)) {
      // adjust estimated and accumulated values
      if (message instanceof Order) {
        final Order order = (Order) message;
        final String symbol = InstrumentCache.getPair(order.getSecurityId()).getSymbol();
        if (symbol.contains("/USDC")) {
          order.setFeeAccumulatedQuantity(order.getFeeAccumulatedQuantity() * 10000);
          order.setFeeEstimatedQuantity(order.getFeeEstimatedQuantity() * 10000);
          if (order.getSide() == Side.BUY) {
            order.setAvailableAccumulatedQuantity(order.getAvailableAccumulatedQuantity() * 10000);
            order.setAvailableEstimatedQuantity(order.getAvailableEstimatedQuantity() * 10000);
          }
        }
      }
      // adjust scale on usdc positions
      if (message instanceof BalanceAdminMessage) {
        final int id = InstrumentCache.getBySymbol("USDC").getId();
        Position[] positions = ((BalanceAdminMessage) message).getPositionArr();
        if ((id < positions.length) && (positions[id] != null)) {
          positions[id].setQuantity(positions[id].getQuantity() * 10000);
          positions[id].setAvailableQuantity(positions[id].getAvailableQuantity() * 10000);
        }
      }
    }

    if (transforms.contains(TRANSFORM_MOVE_USD_TO_USDC)) {
      // move usd positions to usdc
      if (message instanceof BalanceAdminMessage) {
        final Instrument usd = InstrumentCache.getBySymbol("USD");
        final Instrument usdc = InstrumentCache.getBySymbol("USDC");

        Position usdPosition = null;
        Position usdcPosition = null;
        for (Position position : ((BalanceAdminMessage) message).getPositionArr()) {
          if (position != null) {
            if (position.getInstrumentId() == usd.getId()) {
              usdPosition = position;
            }
            if (position.getInstrumentId() == usdc.getId()) {
              usdcPosition = position;
            }
          }
        }

        if (usdPosition != null) {
          if (usdcPosition == null) {
            usdcPosition = new Position(usdc.getId());
            ((BalanceAdminMessage) message).getPositionArr()[usdc.getId()] = usdcPosition;
          }
          long amount = usdPosition.getQuantity();
          if (usd.getQuantityScale() > usdc.getQuantityScale()) {
            for (int i = 0; i < (usd.getQuantityScale() - usdc.getQuantityScale()); i++) {
              amount /= 10;
            }
          }
          if (usd.getQuantityScale() < usdc.getQuantityScale()) {
            for (int i = 0; i < (usdc.getQuantityScale() - usd.getQuantityScale()); i++) {
              amount *= 10;
            }
          }
          usdcPosition.addQuantity(amount);
          usdcPosition.addAvailableQuantity(amount);
          usdPosition.setQuantity(0);
          usdPosition.setAvailableQuantity(0);
        }
      }
    }

    if (transforms.contains(TRANSFORM_FIX_USD_SCALING)) {
      // adjust usd positions to fix scaling from 6 to 2
      if (message instanceof BalanceAdminMessage) {
        final int id = InstrumentCache.getBySymbol("USD").getId();
        Position[] positions = ((BalanceAdminMessage) message).getPositionArr();
        if ((id < positions.length) && (positions[id] != null)) {
          positions[id].setQuantity(positions[id].getQuantity() / 10000);
          positions[id].setAvailableQuantity(positions[id].getAvailableQuantity() / 10000);
        }
      }
    }

    if (prune) {
      // remove open orders for userid > 20
      if (message instanceof Order) {
        if (((Order) message).getUser().getId() > 20) {
          return null;
        }
      }
      // remove balances for userid > 20
      if (message instanceof BalanceAdminMessage) {
        if (((BalanceAdminMessage) message).getUserId() > 20) {
          return null;
        }
      }
    }

    if (clean) {
      // remove open orders for all users
      if (message instanceof Order) {
        return null;
      }
      // reset user positions
      if (message instanceof BalanceAdminMessage) {
        final Position[] positions = ((BalanceAdminMessage) message).getPositionArr();
        for (int i = 0; i < positions.length; i++) {
          if (positions[i] != null) {
            positions[i].setUsdAvgCostBasis(0);
            positions[i].setUsdAvgCostBasisScale((short) 0);
            positions[i].setUsdCostBasis(0);
            positions[i].setUsdRealized(0);
            positions[i].setUsdUnrealized(0);
            positions[i].setQuantity(0);
            positions[i].setAvailableQuantity(0);
            positions[i].setUsdValue(0);
          }
        }
      }
    }

    if (debug) {
      System.out.println(">> " + message.toJSON());
    }

    return message;
  }

  public long importSnapshot(final String json, final String snapshot) throws IOException {
    final long snapId = TimeUtil.getTime();
    System.out.println("IMPORT: " + json + " -> " + snapshot + "/" + snapId);

    try (final BufferedReader reader = new BufferedReader(new FileReader(new File(json)))) {
      String message = reader.readLine();

      statistics.reset();
      while (message != null) {
        final String type = importMessage(snapId, message);
        statistics.count(type);
        message = reader.readLine();
      }
    }
    statistics.print(true);
    System.out.println("SnapId: " + snapId);

    return snapId;
  }

  private String importMessage(final long snapId, final String json) {
    if ((json == null) || json.trim().isEmpty()) {
      return null;
    }

    if (debug) {
      System.out.println("<< " + json);
    }

    final JsonElement jsonParser = new JsonParser().parse(json);
    final String type = jsonParser.getAsJsonObject().get("class").getAsString();

    final GsonBuilder builder = new GsonBuilder();
    Message message = null;
    switch (type) {
      case "UserAdminMessage":
        builder.registerTypeAdapter(UserAdminMessage.class, new UserAdminMessageJsonDeserializer());
        message = builder.create().fromJson(json, UserAdminMessage.class);
        break;
      case "BalanceAdminMessage":
        builder.registerTypeAdapter(BalanceAdminMessage.class, new BalanceAdminMessageJsonDeserializer());
        message = builder.create().fromJson(json, BalanceAdminMessage.class);
        if (message.getUser() == null) {
          message.setUser(new User(((BalanceAdminMessage) message).getUserId()));
        }
        break;
      case "FeeAdminMessage":
        builder.registerTypeAdapter(FeeAdminMessage.class, new FeeAdminMessageJsonDeserializer());
        message = builder.create().fromJson(json, FeeAdminMessage.class);
        break;
      case "SecurityDefinitionAdminMessage":
        builder.registerTypeAdapter(SecurityDefinitionAdminMessage.class, new SecurityDefinitionAdminMessageJsonDeserializer());
        message = builder.create().fromJson(json, SecurityDefinitionAdminMessage.class);
        ((SecurityDefinitionAdminMessage) message).setSnapConverterMode(true);
        break;
      case "SnapResponseAdminMessage":
        message = builder.create().fromJson(json, SnapResponseAdminMessage.class);
        break;
      case "Order":
        builder.registerTypeAdapter(ExecutionReportMessage.class, new ExecutionReportMessageJsonDeserializer());
        message = builder.create().fromJson(json, ExecutionReportMessage.class);
        break;
      case "AssetGroup":
        builder.registerTypeAdapter(AssetGroup.class, new AssetGroupJsonDeserializer());
        message = builder.create().fromJson(json, AssetGroup.class);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported message: class=" + type + MESSAGE_EQ + json);
    }

    if (null != message) {
      message.setSnapId(snapId);

      if (message instanceof UserAdminMessage) {
        UserCache.addToCache((UserAdminMessage) message);
      } else {
        message.onMatcher();
      }

      message.onPublish();
      if (debug) {
        System.out.println(">> " + message);
      }

    } else {
      System.out.println("WARNING: Message ignored from import - " + json);
    }

    return type;
  }

  public static void main(final String[] args) {

    final Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("s").longOpt("snapshot").desc("snapshot directory").hasArg().argName("dir").required().build());
    options.addOption(Option.builder("j").longOpt("json").desc("json file path").hasArg().argName("file").required().build());
    options.addOption(
        Option.builder("m").longOpt("mode").desc("mode of operation (export|import)").hasArg().argName("mode").required().build());
    options.addOption(Option.builder().longOpt("debug").desc("enable debug logging").required(false).build());
    options.addOption(Option.builder().longOpt("prune").desc("enable snapshot pruning").required(false).build());
    options.addOption(Option.builder().longOpt("clean").desc("enable snapshot cleaning").required(false).build());
    options.addOption(Option.builder().longOpt("validate").desc("enable snapshot validation").required(false).build());
    options.addOption(Option.builder("c").longOpt("config").desc("configuration file path").hasArg().argName("file").build());
    options.addOption(Option.builder().longOpt("data-port").desc("data port configuration file path").hasArg().argName("file").build());
    options.addOption(Option.builder("").longOpt("transform").desc("apply transformation").hasArgs().argName("name").build());

    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("SnapConverter", options);
        System.out.println();
        return;
      }
    }

    final CommandLineParser parser = new DefaultParser();
    try {
      final CommandLine cmd = parser.parse(options, args);
      final String snapshot = cmd.getOptionValue("s");
      final String json = cmd.getOptionValue("j");
      final String mode = cmd.getOptionValue("m").toLowerCase();

      // Load config file
      InputStream stream = null;
      if (cmd.hasOption("c")) {
        File file = new File(cmd.getOptionValue("c"));
        if (file.exists() && file.isDirectory()) {
          file = new File(cmd.getOptionValue("c") + "/config.properties");
        }
        stream = new FileInputStream(file);
      }

      final Properties properties = new Properties();
      properties.setProperty("CHRONICLE_ENGINE_SNAP_DIRECTORY", snapshot);
      properties.setProperty("PUBLISH_MARKET_DATA", "false");
      properties.setProperty("NUM_ENCODER_THREADS", "0");
      PoolSize.minimize(properties);

      PropertyReader.initialize(stream, properties);

      // Load data port config file
      SnapTransformer transformer = null;
      if (cmd.hasOption("data-port")) {
        final File file = new File(cmd.getOptionValue("data-port"));
        final InputStream s = new FileInputStream(file);
        properties.clear();
        properties.load(s);
        transformer = new SnapTransformer(properties);
      }

      final SnapConverter snapConverter =
          new SnapConverter(cmd.hasOption("debug"), cmd.hasOption("prune"), cmd.hasOption("clean"), transformer);

      if (cmd.hasOption("transform")) {
        for (final String name : cmd.getOptionValues("transform")) {
          snapConverter.addTransform(name);
        }
      }

      if (mode.equals("export")) {
        boolean valid = false;
        final File snapshotPath = new File(snapshot);
        if (snapshotPath.exists() && snapshotPath.isDirectory()) {
          final File doneFile = new File(snapshot + "/done");
          if (doneFile.exists() && doneFile.isFile()) {
            valid = true;
          }
        }
        if (!valid) {
          System.out.println("ERROR: Specified directory " + snapshot + " does not contain a valid snapshot");
          System.exit(1);
        }

        final File jsonFile = new File(json);
        if (jsonFile.exists() && !jsonFile.isFile()) {
          System.out.println("ERROR: Specified output file " + json + " already exists and is not a regular file");
          System.exit(1);
        }

        snapConverter.exportSnapshot(snapshot, json);
        if (cmd.hasOption("validate") && !snapConverter.validate()) {
          System.exit(1);
        }
      } else if (mode.equals("import")) {
        final File snapshotPath = new File(snapshot);
        if (!snapshotPath.exists() || !snapshotPath.isDirectory()) {
          System.out.println("ERROR: Specified directory " + snapshot + " does not exist");
          System.exit(1);
        }

        final File jsonFile = new File(json);
        if (!jsonFile.exists() || !jsonFile.isFile()) {
          System.out.println("ERROR: Specified input file " + json + " does not exist or is not a regular file");
          System.exit(1);
        }

        snapConverter.importSnapshot(json, snapshot);
      } else {
        System.out.println("ERROR: Invalid mode specified - " + mode);
      }
    } catch (final Exception e) {
      System.out.println("ERROR: " + e.getMessage());
      e.printStackTrace();
      System.out.println("Run with --help option for usage information");
      System.exit(1);
    }

    System.exit(0);
  }
}
