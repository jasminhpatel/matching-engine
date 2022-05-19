package com.solfini.testtcp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.solfini.common.IdleStrategyFactory;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.pool.ByteBufferObjectPool;
import com.solfini.util.StringUtil;

public final class SocketServer implements Runnable {
  public static final int NUM_THREADS = 32;
  public static final int PORT = 54999;
  private static final Logger LOGGER = LoggerFactory.getLogger(SocketServer.class);
  private long id = 0;
  private long count = 0;

  private final IdleStrategy idleStrategy;

  private List<ConnectionWrapper> connections = new ArrayList<>();

  public SocketServer(final IdleStrategy idleStrategy) {
    this.idleStrategy = idleStrategy;
  }

  public void run() {
    try {
      ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
      serverSocketChannel.socket().bind(new InetSocketAddress(PORT));
      serverSocketChannel.configureBlocking(false);

      while (true) {
        idleStrategy.idle();

        SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel == null)
          continue;
        LOGGER.info("socketChannel accept");
        socketChannel.configureBlocking(false);
        socketChannel.socket().setTcpNoDelay(true);
        LOGGER.info("socketChannel orig getReceiveBufferSize=" + socketChannel.socket().getReceiveBufferSize());
        socketChannel.socket().setReceiveBufferSize(8_388_608 * 2);


        final long responseChannelId = id++;
        ConnectionWrapper connectionWrapper = new ConnectionWrapper(responseChannelId, socketChannel);
        connections.add(connectionWrapper);


        Thread thread = new Thread(new SocketServerListener(connectionWrapper, responseChannelId));
        thread.start();
      }
    } catch (Exception e) {
      LOGGER.error("error", e);
      e.printStackTrace();
    }
  }

  public final void send(final ByteBuffer byteBuffer, final int length, final String senderCompId) {
    boolean found = false;

    LOGGER.info(">>>>> socketChannel send senderCompId=" + senderCompId + ", length=" + length + ", conns=" + connections.size() + ", fix="
        + StringUtil.fixToString(byteBuffer, length));

    for (int i = connections.size() - 1; i >= 0; i--) {
      final ConnectionWrapper connectionWrapper = connections.get(i);
      try {
        if (senderCompId.equals(connectionWrapper.getSenderCompId())) {
          // Shift message to start of buffer
          // mutableAsciiBuffer.putBytes(0, mutableAsciiBuffer, realStart, length);
          // mutableAsciiBuffer.byteBuffer().limit(length);
          LOGGER.info("socketChannel send count=" + (count++) + " senderCompId=" + senderCompId + ", length=" + length + ", fix="
              + StringUtil.fixToString(byteBuffer, length) + ", connectionWrapper=" + connectionWrapper);

          byteBuffer.limit(length);
          SocketChannel socketChannel = connectionWrapper.getSocketChannel();
          if (socketChannel != null) {
            socketChannel.write(byteBuffer);
            // (byteBuffer, 0, length);
            found = true;
            // break;
          }
        }
      } catch (Exception e) {
        LOGGER.error("error connections.size()=" + connections.size(), e);
        if (connectionWrapper.getSocketChannel() != null)
          LOGGER.info("error, conn open " + connectionWrapper.getSocketChannel().isOpen());
        e.printStackTrace();
        if (connectionWrapper.getSocketChannel() != null && !connectionWrapper.getSocketChannel().isOpen()
            || e.getMessage().contains("Broken pipe")) {
          try {
            LOGGER.info(">>> connection.close()");
            new Thread() {
              public void run() {
                try {
                  LOGGER.info(">>> connection.close() 2");
                  connectionWrapper.getSocketChannel().close();
                  LOGGER.info("<<< connection.close() 2");
                } catch (Exception e) {
                  e.printStackTrace();
                }
              }
            }.start();
            LOGGER.info("<<< connection.close()");
          } catch (Exception e1) {
            e1.printStackTrace();
          }
          LOGGER.info("connections.remove " + i + ", connections.size()=" + connections.size());
          connections.remove(i);
        }
      }
    }
    if (!found)
      LOGGER.info("socketChannel NOT found senderCompId=" + senderCompId + ", length=" + length + ", fix="
          + StringUtil.fixToString(byteBuffer, length));


    LOGGER.info("<<<<< socketChannel send senderCompId=" + senderCompId + ", length=" + length + ", fix="
        + StringUtil.fixToString(byteBuffer, length));
  }

  public static final ManyToOneConcurrentArrayQueueCustom<ByteBuffer> listenerToParserQueue =
      new ManyToOneConcurrentArrayQueueCustom<ByteBuffer>(8_388_608, "listenerToParserQueue");

  public static void main(String[] args) throws Exception {
    System.out.println("starting server");
    ByteBufferObjectPool.get();

    ParserThread parserThread = new ParserThread(IdleStrategyFactory.create("NoOpIdleStrategy"));
    new Thread(parserThread).start();

    SocketServer server = new SocketServer(IdleStrategyFactory.create("NoOpIdleStrategy"));
    new Thread(server).start();
  }
}
