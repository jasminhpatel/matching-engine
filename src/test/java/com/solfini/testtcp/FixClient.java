package com.solfini.testtcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;
import com.solfini.internal.admin.schema.AdminAcknowledgementEncoder;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.TradeStateAdminMessageEncoder;
import com.solfini.sbe.encoder.NewOrderSingleEncoder;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

/**
 *
 * @author Chris Mack
 *
 */
public class FixClient implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(FixClient.class);

  public static final String FIX_HOST = PropertyReader.getProperty("FIX_HOST", "127.0.0.1");
  // public static final int FIX_PORT = PropertyReader.getProperty("FIX_PORT", 9999);
  public static final int FIX_PORT = 54999; // PropertyReader.getProperty("FIX_PORT", 54999);
  public static final int SBE_PORT = PropertyReader.getProperty("SBE_PORT", 5998);
  public static final char[] beginString = "FIX.4.4".toCharArray();
  private final short encodedLengthSize = 2;

  // public static final char MARKET = OrdType.MARKET.representation();
  // public static final char LIMIT = OrdType.LIMIT.representation();
  // public static final char STOP_LIMIT = OrdType.STOP_LIMIT.representation();

  // public static final char BUY = Side.BUY.representation();
  // public static final char SELL = Side.SELL.representation();
  //
  // public static final String HEARTBEAT = MsgType.HEARTBEAT.representation();
  // public static final String LOGON = MsgType.LOGON.representation();
  // public static final String ORDER_SINGLE = MsgType.ORDER_SINGLE.representation();
  // public static final String ORDER_CANCEL_REQUEST = MsgType.ORDER_CANCEL_REQUEST.representation();
  // public static final String ORDER_CANCEL_REPLACE_REQUEST = MsgType.ORDER_CANCEL_REPLACE_REQUEST.representation();
  // public static final String EXECUTION_REPORT = MsgType.EXECUTION_REPORT.representation();

  public static final char DAY = (char) TimeInForce.DAY.value();
  public static final char GOOD_TILL_CANCEL = (char) TimeInForce.GOOD_TILL_CANCEL.value();
  public static final char FILL_OR_KILL = (char) TimeInForce.FILL_OR_KILL.value();
  public static final char IMMEDIATE_OR_CANCEL = (char) TimeInForce.IMMEDIATE_OR_CANCEL.value();
  public static final char[] senderCompId = "1000000018".toCharArray();


  protected SocketChannel fixSocketChannel = null;
  protected SocketChannel sbeSocketChannel = null;

  private Thread fixListenerThread;
  private Thread sbeListenerThread;
  private Thread fixModelThread;
  private Thread tradeHistoryLoggerThread;
  private AtomicLong clOrdID;
  private AtomicInteger msgSeqNum;

  public FixClient() {
    clOrdID = new AtomicLong();
    msgSeqNum = new AtomicInteger();
    try {
      connect();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void connect() throws IOException {
    fixSocketChannel = SocketChannel.open();
    fixSocketChannel.connect(new InetSocketAddress(FIX_HOST, FIX_PORT));
    fixSocketChannel.configureBlocking(false);
    fixSocketChannel.socket().setTcpNoDelay(true);

    // sbeSocketChannel = SocketChannel.open();
    // sbeSocketChannel.connect(new InetSocketAddress(FIX_HOST, SBE_PORT));
    // sbeSocketChannel.configureBlocking(false);

    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      LOGGER.error("error", e);
    }
    System.out.println("\nListening");
    LOGGER.info("Listening");
  }



  public void sendOrder(final NewOrderSingleEncoder newOrderSingleEncoder, final User user) {
    try {
      final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
      simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
      byte[] timestamp = simpleDateFormat.format(new Date()).getBytes();
      ByteBuffer messageBuffer = ByteBuffer.allocateDirect(4096);
      MutableAsciiBuffer mutableAsciiBuffer = new MutableAsciiBuffer(messageBuffer);

      /*
       * newOrderSingleEncoder.account(String.valueOf(1_000_000_000 + user.getId())); newOrderSingleEncoder.transactTime(timestamp);
       *
       * HeaderEncoder headerEncoder = newOrderSingleEncoder.header(); headerEncoder.beginString(beginString); //
       * headerEncoder.msgType(ORDER_SINGLE); headerEncoder.senderCompID(senderCompId); headerEncoder.sendingTime(timestamp);
       * headerEncoder.targetCompID(senderCompId); headerEncoder.msgSeqNum(msgSeqNum.incrementAndGet());
       *
       * long encoded = newOrderSingleEncoder.encode(mutableAsciiBuffer, 0); int length = (int) encoded; int realStart =
       * newOrderSingleEncoder.trailer().realStart();
       *
       *
       * long encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; int realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       */
      /*
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       *
       * encoded2 = newOrderSingleEncoder.encode(mutableAsciiBuffer, length); length += (int) encoded2; realStart2 =
       * newOrderSingleEncoder.trailer().realStart();
       */
      // Shift message to start of buffer
      // mutableAsciiBuffer.putBytes(0, mutableAsciiBuffer, realStart, length);
      // mutableAsciiBuffer.byteBuffer().limit(length);

      Assert.fail(); // TODO Replace with new encoder

      // System.out.print("sending: ");
      // for (int i = 0; i < length; i++) {
      // System.out.print(mutableAsciiBuffer.getChar(i));
      // }
      // System.out.println("");

      fixSocketChannel.write(mutableAsciiBuffer.byteBuffer());
    } catch (Exception e) {
      LOGGER.error("error in sendUser", e);
      resetFixChannel();
    }
  }

  //@formatter:off
  /*
  public void sendCancelOrder(final OrderCancelRequestEncoder orderCancelRequestEncoder, final User user) {
    try {
      final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
      simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
      byte[] timestamp = simpleDateFormat.format(new Date()).getBytes();
      ByteBuffer messageBuffer = ByteBuffer.allocateDirect(4096);
      MutableAsciiBuffer mutableAsciiBuffer = new MutableAsciiBuffer(messageBuffer);

      orderCancelRequestEncoder.account(String.valueOf(1_000_000_000 + user.getId()));
      orderCancelRequestEncoder.clOrdID(StringUtil.copyWithBuffer(String.valueOf(clOrdID.incrementAndGet()).toCharArray()));
      orderCancelRequestEncoder.transactTime(timestamp);

      //
       // HeaderEncoder headerEncoder = orderCancelRequestEncoder.header(); headerEncoder.beginString(beginString);
      //headerEncoder.msgType(ORDER_CANCEL_REQUEST); headerEncoder.senderCompID(senderCompId); headerEncoder.sendingTime(timestamp);
      // headerEncoder.targetCompID(senderCompId); headerEncoder.msgSeqNum(msgSeqNum.incrementAndGet());
       //
      Assert.fail(); // TODO Replace with new encoder



      long encoded = orderCancelRequestEncoder.encode(mutableAsciiBuffer, 0);
      int length = (int) encoded;
      int realStart = orderCancelRequestEncoder.trailer().realStart();

      // Shift message to start of buffer
      mutableAsciiBuffer.putBytes(0, mutableAsciiBuffer, realStart, length);
      mutableAsciiBuffer.byteBuffer().limit(length);

      System.out.print("sending: ");
      for (int i = 0; i < length; i++) {
        System.out.print(mutableAsciiBuffer.getChar(i));
      }
      System.out.println("");
      fixSocketChannel.write(mutableAsciiBuffer.byteBuffer());
    } catch (Exception e) {
      LOGGER.error("error in sendUser", e);
      resetFixChannel();
    }
  }
*/
  //@formatter:on


  public final void resetFixChannel() {
    LOGGER.info(">>> resetFixChannel");
    if (fixSocketChannel != null && !fixSocketChannel.isOpen()) {
      try {
        fixSocketChannel.close();
      } catch (Exception e) {
      }
      try {
        fixSocketChannel = SocketChannel.open();
        fixSocketChannel.connect(new InetSocketAddress(FIX_HOST, FIX_PORT));
        fixSocketChannel.configureBlocking(false);
        LOGGER.info(">>> resetFixChannel");
        Thread.sleep(2000);
      } catch (Exception e) {
        LOGGER.error("error", e);
      }
      sendStateAdmin(MarketStatus.RESTATE, 0);
    }
  }

  public final boolean sendStateAdmin(final MarketStatus marketStatus, final int instrumentId) {
    try {
      short encodedLength = encodedLengthSize;
      ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
      UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      TradeStateAdminMessageEncoder tradeStateAdminMessageEncoder = new TradeStateAdminMessageEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      tradeStateAdminMessageEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      encodedLength += headerEncoder.encodedLength();

      tradeStateAdminMessageEncoder.marketStatus(marketStatus); // MarketStatus.RESTATE
      if (instrumentId > 0)
        tradeStateAdminMessageEncoder.securityId(instrumentId);

      encodedLength += tradeStateAdminMessageEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      sbeSocketChannel.write(adminMessageBuffer);
    } catch (Exception e) {
      LOGGER.error("error", e);
    }
    return true;
  }

  public final boolean sendAdminAcknowledgement() {
    try {
      short encodedLength = encodedLengthSize;
      ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(4096);
      UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

      AdminAcknowledgementEncoder adminAcknowledgementEncoder = new AdminAcknowledgementEncoder();
      com.solfini.internal.admin.schema.MessageHeaderEncoder headerEncoder =
          new com.solfini.internal.admin.schema.MessageHeaderEncoder();
      adminAcknowledgementEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, encodedLength, headerEncoder);
      encodedLength += headerEncoder.encodedLength();

      adminAcknowledgementEncoder.requestStatus(RequestStatus.SUCCESS);

      encodedLength += adminAcknowledgementEncoder.encodedLength();
      adminMessageBuffer.limit(encodedLength);
      adminMessageUnsafeBuffer.putShort(0, encodedLength);

      sbeSocketChannel.write(adminMessageBuffer);
    } catch (Exception e) {
      LOGGER.error("error", e);
    }
    return true;
  }

  static final User user = new User(18);
}
