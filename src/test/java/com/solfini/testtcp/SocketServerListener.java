package com.solfini.testtcp;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.solfini.common.IdleStrategyFactory;
import com.solfini.common.Message;
import com.solfini.matchengine.decoder.CancelReplaceRequestHandler;
import com.solfini.matchengine.decoder.CancelRequestHandler;
import com.solfini.matchengine.decoder.MassCancelRequestHandler;
import com.solfini.matchengine.decoder.SessionHandler;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.pool.ByteBufferObjectPool;
import com.solfini.util.StringUtil;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

public class SocketServerListener implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SocketServerListener.class);
  private static final int FIX_TRAILER_LENGTH = 8;
  private static final int FIX_BEGIN_HEADER = 12;
  private static final int FIX_READ_MINIMUM = 22;

  private final NewOrderSingleHandler newOrderSingleHandler = new NewOrderSingleHandler();
  private final CancelRequestHandler cancelRequestHandler = new CancelRequestHandler();
  private final CancelReplaceRequestHandler cancelReplaceRequestHandler = new CancelReplaceRequestHandler();
  private final MassCancelRequestHandler massCancelRequestHandler = new MassCancelRequestHandler();
  private final SessionHandler sessionHandler = new SessionHandler();

  private final com.solfini.internal.schema.MessageHeaderDecoder messageHeaderDecoder =
      new com.solfini.internal.schema.MessageHeaderDecoder();


  // private final HeaderDecoder headerDecoder = new HeaderDecoder();

  private final OneToOneConcurrentArrayQueue<Message> receiverToMatcherQueue = new OneToOneConcurrentArrayQueue<Message>(8_388_608);



  private final ConnectionWrapper connectionWrapper;
  private final IdleStrategy idleStrategy;
  private final MutableAsciiBuffer mab;
  private final long responseChannelId;
  private boolean active = true;
  private static final int BUFFER_SIZE = 65_536 * 2;
  private static final int READ_BUFFER_LIMIT = 32_768 * 2;

  public SocketServerListener(final ConnectionWrapper connectionWrapper, final long responseChannelId) {
    this.connectionWrapper = connectionWrapper;
    this.idleStrategy = IdleStrategyFactory.create("NoOpIdleStrategy");
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    this.mab = new MutableAsciiBuffer(byteBuffer);
    this.responseChannelId = responseChannelId;
  }

  @Override
  public void run() {
    process();
  }

  static final byte[] bytes = new byte[READ_BUFFER_LIMIT];

  public void process() {
    ByteBuffer buffer = ByteBufferObjectPool.get();
    final SocketChannel socketChannel = connectionWrapper.getSocketChannel();
    long counter = 0;
    LOGGER.info("socketChannel process");
    System.out.println("socketChannel process");
    int available = 0;
    int read = 0;

    while (active) {
      try {
        read = socketChannel.read(buffer);
        if (read > 0) {
          available += read;
          counter++;
          SocketServer.listenerToParserQueue.add(buffer);
          buffer = ByteBufferObjectPool.get();
          buffer.clear();
          LOGGER.info("read=" + read + ", counter=" + counter + ", available=" + available);
        }
      } catch (Exception e) {
        e.printStackTrace();
        LOGGER.error("error", e);
      }
    }


    while (active) {
      try {

        if (available <= READ_BUFFER_LIMIT) {
          read = socketChannel.read(buffer);
          if (read > 0)
            available += read;

        } else
          read = 0;

        if (available > buffer.limit()) {
          LOGGER.error("Resetting available from " + available + ", to " + buffer.limit());
          // available = buffer.limit();
        }
        if (available == 0 && read == 0 && buffer.limit() == 0) {
          LOGGER.error("Clearing buffer");
          buffer.clear();
          continue;
        }

        if (available > FIX_READ_MINIMUM) {
          buffer.flip();
          int bodyLength = 0;
          int headerLength = FIX_BEGIN_HEADER;

          for (; headerLength < available; headerLength++) {
            byte b = buffer.get(headerLength);
            if (b == 1)
              break;
            bodyLength *= 10;
            bodyLength += (b - '0');
          }

          final int totalFixLength = headerLength + bodyLength + FIX_TRAILER_LENGTH;
          if (bodyLength <= 0 || available < totalFixLength) { // full message isn't available yet
            String data = StringUtil.fixToString(buffer, Math.min(available, BUFFER_SIZE));

            System.out.println("SKIP headerLength=" + headerLength + ", bodyLength=" + bodyLength + ", read=" + read + ", available="
                + available + ", totalFixLength=" + totalFixLength + ", BUFFER_SIZE=" + BUFFER_SIZE + ", data=" + data);
            LOGGER.info("SKIP headerLength=" + headerLength + ", bodyLength=" + bodyLength + ", read=" + read + ", available=" + available
                + ", totalFixLength=" + totalFixLength + ", BUFFER_SIZE=" + BUFFER_SIZE + ", data=" + data);

            continue;
          }

          counter++;
          if (counter % 10000 == 0) {
            System.out.println("totalFixLength=" + totalFixLength + ", counter=" + counter);
            LOGGER.info("totalFixLength=" + totalFixLength + ", counter=" + counter);
          }

          // deep copy
          // ByteBuffer clone = ByteBufferObjectPool.get();
          // for (int i = 0; i < totalFixLength; i++) {
          // clone.put(buffer.get(i));
          // bytes[i] = buffer.get(i);
          // }
          // clone.flip();

          //// handleMessage(buffer, totalFixLength);
          buffer.position(totalFixLength);
          buffer.mark();
          buffer.compact();
          available -= totalFixLength;
        }


      } catch (Exception e) {
        e.printStackTrace();
        LOGGER.error("error", e);
        if (socketChannel == null || !socketChannel.isConnected() || e instanceof java.nio.channels.ClosedChannelException
            || e instanceof java.io.IOException) {
          active = false;
          LOGGER.error("closing socket. error", e);
        }
      }
      idleStrategy.idle();
    }
  }

  /*
   * private void handleMessage(final ByteBuffer buffer, final int length) { mab.wrap(buffer, 0, length);
   *
   * headerDecoder.decode(mab, 0, length);
   *
   * MsgType msgType = headerDecoder.msgTypeAsEnum(); Message message = null; String senderCompId = headerDecoder.senderCompIDAsString();
   * int msgSeqNum = headerDecoder.msgSeqNum();
   *
   * // LOGGER.info("Receiving for " + senderCompId + ", sb=" + sb.toString()); message = sessionHandler.validateFIXSession(msgType,
   * msgSeqNum, senderCompId, responseChannelId);
   *
   * if (message == null) { switch (msgType) { case ORDER_SINGLE: LOGGER.debug("New Order Single Received"); message =
   * newOrderSingleHandler.decodeNewOrderSingle(mab, length, senderCompId); break; case ORDER_CANCEL_REQUEST:
   * LOGGER.debug("Order Cancel Request"); long cancelId = newOrderSingleHandler.getNextOrderId(); message =
   * cancelRequestHandler.decodeCancelRequest(mab, length, senderCompId, cancelId); break; case ORDER_CANCEL_REPLACE_REQUEST:
   * LOGGER.debug("Order Cancel Replace Request"); cancelId = newOrderSingleHandler.getNextOrderId(); long newOrderId =
   * newOrderSingleHandler.getNextOrderId(); message = cancelReplaceRequestHandler.decodeCancelReplaceRequest(mab, length, senderCompId,
   * cancelId, newOrderId); break; case ORDER_MASS_CANCEL_REQUEST: LOGGER.debug("Order Mass Cancel Request"); cancelId =
   * newOrderSingleHandler.getNextOrderId(); message = massCancelRequestHandler.decodeCancelRequest(mab, length, senderCompId, cancelId);
   * break; case LOGON: connectionWrapper.setSenderCompId(senderCompId); LOGGER.debug("Logon Message Received, senderCompId=" + senderCompId
   * + ", connectionWrapper=" + connectionWrapper); SessionInfo inboundSessionInfo = buildSessionInfo(responseChannelId); SessionInfo
   * outboundSessionInfo = buildSessionInfo(responseChannelId); message = sessionHandler.decodeLogon(mab, length, inboundSessionInfo,
   * outboundSessionInfo); break; case LOGOUT: LOGGER.debug("Logout Message Received"); case HEARTBEAT:
   * LOGGER.debug("Heartbeat Message Received"); message = sessionHandler.decodeHeartbeat(mab, length, senderCompId); break; case
   * RESEND_REQUEST: LOGGER.debug("Resend Request Message Received"); message = sessionHandler.decodeResendRequest(mab, length,
   * senderCompId); break; case SEQUENCE_RESET: LOGGER.debug("Sequence Reset Message Received"); message =
   * sessionHandler.decodeSequenceReset(mab, length, senderCompId); break;
   *
   * default: break;
   *
   * } }
   *
   * if (message != null) { // LOGGER.info("socketServer recieved message=" + message); receiverToMatcherQueue.add(message); } }
   */

  private SessionInfo buildSessionInfo(final long responseChannelId) {
    SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setConnectionId(responseChannelId);
    // sessionInfo.setSenderCompId(headerDecoder.senderCompIDAsString());

    return sessionInfo;

  }
}
