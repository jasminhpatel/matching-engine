package com.solfini.matchengine.marketdata;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Test;

import com.solfini.common.Message;
import com.solfini.matchengine.decoder.MarketDataHandler;
import com.solfini.matchengine.message.outbound.MarketDataSnapMessage;
import com.solfini.matchengine.model.orderbook.OrderBookTest;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshDecoder;
import com.solfini.sbe.encoder.MarketDataSnapshotFullRefreshEncoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MessageHeaderEncoder;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.StringUtil;

import static com.solfini.sbe.encoder.TimeInForce.DAY;


/**
 * Created by Chris Mack
 */
public class MarketDataTest extends OrderBookTest {

  public static final char[] beginString = "FIX.4.4".toCharArray();
  final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
  // process bytes with 17 offset
  private static final int KAFKA_OFFSET = 17;
  // process bytes with 17 offset+2
  private static final int OFFSET = 19;
  private static final short ADMIN_ENCODED_LENGTH_SIZE = 2;
  private static final SessionInfo sessionInfo = new SessionInfo();


  @Test
  public void testEncodeDecode() {

    orderBook.addOrder(createOrder(1, user, pair.getId(), 1010, 500, Side.SELL, DAY));
    orderBook.addOrder(createOrder(2, user, pair.getId(), 1010, 300, Side.SELL, DAY));
    orderBook.addOrder(createOrder(3, user, pair.getId(), 1010, 200, Side.SELL, DAY));
    orderBook.addOrder(createOrder(4, user, pair.getId(), 1011, 300, Side.SELL, DAY));
    orderBook.addOrder(createOrder(5, user, pair.getId(), 1011, 200, Side.SELL, DAY));


    MarketDataSnapMessage marketDataSnapMessage =  MarketDataSnapMessage.create(pair, sessionInfo);

    byte[] bytes = encode(marketDataSnapMessage);
    int securityId = decode(bytes);

    System.out.println(securityId);
    Assert.assertEquals(pair.getId(), securityId);
  }


  private byte[] encode(final MarketDataSnapMessage marketDataSnapMessage) {

    final MarketDataSnapshotFullRefreshEncoder mdEncoder = marketDataSnapMessage.getEncoder();
    final ByteBuffer directBuffer = marketDataSnapMessage.getDirectBuffer();
    final UnsafeBuffer unsafeBuffer = marketDataSnapMessage.getUnsafeBuffer();
    final MessageHeaderEncoder headerEncoder = marketDataSnapMessage.getHeaderEncoder();

    short encodedLength = ADMIN_ENCODED_LENGTH_SIZE;
    // mdEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);
    // populateHeader(headerEncoder, marketDataSnapMessage);
    encodedLength += headerEncoder.encodedLength();

    // convert and publish
    encodedLength += mdEncoder.encodedLength();
    directBuffer.limit(encodedLength);
    unsafeBuffer.putShort(0, encodedLength);
    byte[] bytesWithKafkaOffset = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);
    return bytesWithKafkaOffset;
  }

  private int decode(final byte[] data) {

    final MarketDataSnapshotFullRefreshDecoder marketDataSnapshotFullRefreshDecoder = new MarketDataSnapshotFullRefreshDecoder();
    final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    final MarketDataHandler marketDataHandler = new MarketDataHandler();
    final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
    final ByteBuffer messageBuffer = ByteBuffer.allocate(4096);

    // wrap bytes
    decoderUnsafeBuffer.wrap(data);
    headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);
    marketDataSnapshotFullRefreshDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
        headerDecoder.version());
    Message message = marketDataHandler.decodeMarketData(headerDecoder, marketDataSnapshotFullRefreshDecoder);

    int securityId = marketDataSnapshotFullRefreshDecoder.securityId();

    return securityId;
  }

  /*
   * private void buildHeader(HeaderEncoder headerEncoder) { byte[] timestamp = dateFmt.format(new Date()).getBytes();
   * headerEncoder.beginString(beginString); headerEncoder.msgType("0"); //FIXME: Replace with sbe encoding
   * headerEncoder.senderCompID(tradeApiSenderComp); headerEncoder.sendingTime(timestamp); headerEncoder.targetCompID(tradeApiTargetComp);
   * headerEncoder.msgSeqNum(msgSeqNum.incrementAndGet()); }
   */
}
