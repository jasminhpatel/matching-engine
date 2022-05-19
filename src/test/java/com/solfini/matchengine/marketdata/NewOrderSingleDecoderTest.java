package com.solfini.matchengine.marketdata;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.event.Level;

import com.solfini.common.Message;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.decoder.NewOrderSingleHandler;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.model.ModelTestScaffold;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.NewOrderSingleDecoder;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.TimeUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class NewOrderSingleDecoderTest extends ModelTestScaffold {

    public static final char[] beginString = "FIX.4.4".toCharArray();
    private char[] tradeApiSenderComp;
    private char[] tradeApiTargetComp;
    private AtomicInteger msgSeqNum;
    final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
    final NewOrderSingleEncoder marketEncoder = new NewOrderSingleEncoder();
    final ByteBuffer messageBuffer = ByteBuffer.allocate(4096);



    public static final int KAFKA_OFFSET = 17;
    public static final int HEADER_LENGTH = 2;
    private static final int OFFSET = 19;

    private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();
    private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();

    @Before
    public void before() {
        LogLevel.setLevel(Level.TRACE);

        try {
            Properties properties = new Properties();
            PoolSize.minimize(properties);
            PropertyReader.initialize(null, properties);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

        Thread.currentThread().setName(Thread.currentThread().getName() + "_0");

        InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", (short) 2, (short) 2));
        InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", (short) 4, (short) 3));
        InstrumentCache.updateSecurityDefinition(
                createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, (short) 2, (short) 2));

        InstrumentPair pair = InstrumentCache.getPair(BTC_USDT_F);
        User user = createUser(18);
        // user.addPosition(pair.getId(), 10_000);
        // expectMessage("userId=28");
        // assertMessages();
    }

    @Test
    public void testEncodeDecode() {
        Thread.currentThread().setName(Thread.currentThread().getName() + "_0");

        InstrumentPair pair = InstrumentCache.getPair(BTC_USDT_F);



        byte[] data = encode();

        Message message = decode(data, data.length);
        Order order = ((Order) message);
        System.out.println(order);

        Assert.assertEquals(18, order.getAccount());
        Assert.assertEquals(BTC_USDT_F, order.getSecurityId());

    }

    private final Message decode(final byte[] data, final int length) {
        // wrap bytes
        decoderUnsafeBuffer.wrap(data);
        headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);

        try {
            switch (headerDecoder.templateId()) {
                case NewOrderSingleDecoder.TEMPLATE_ID:

                    newOrderSingleDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                            headerDecoder.version());
                    Message message = newOrderSingleHandler.decodeNewOrderSingle(headerDecoder, newOrderSingleDecoder);
                    return message;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] encode() {
        long msgSeqNum = 1;

        short encodedLength = HEADER_LENGTH;
        ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
        UnsafeBuffer unsafeBuffer = new UnsafeBuffer(buffer);

        NewOrderSingleEncoder newOrderSingleEncoder = new NewOrderSingleEncoder();
        com.solfini.sbe.encoder.MessageHeaderEncoder headerEncoder = new com.solfini.sbe.encoder.MessageHeaderEncoder();
        newOrderSingleEncoder.wrapAndApplyHeader(unsafeBuffer, encodedLength, headerEncoder);

        headerEncoder.senderCompId("test");
        headerEncoder.sendingTime(TimeUtil.getTime());
        headerEncoder.msgSeqNum(msgSeqNum++);
        encodedLength += headerEncoder.encodedLength();

        newOrderSingleEncoder.userId(18);
        newOrderSingleEncoder.securityId(BTC_USDT_F);
        newOrderSingleEncoder.side(Side.BUY);
        newOrderSingleEncoder.ordType(OrdType.LIMIT);
        newOrderSingleEncoder.timeInForce(TimeInForce.GOOD_TILL_CANCEL);
        newOrderSingleEncoder.price(100);
        newOrderSingleEncoder.priceScale((short) 2);
        newOrderSingleEncoder.qty(500);
        newOrderSingleEncoder.qtyScale((short) 2);
        newOrderSingleEncoder.clOrdID("2134124342");
        newOrderSingleEncoder.expireTime(TimeUtil.getTime());

        encodedLength += newOrderSingleEncoder.encodedLength();
        buffer.limit(encodedLength);
        unsafeBuffer.putShort(0, encodedLength);

        byte[] encodedMsg = StringUtil.bufferToArrayBulk(unsafeBuffer.byteBuffer(), encodedLength, KAFKA_OFFSET);

        return encodedMsg;
    }
}
