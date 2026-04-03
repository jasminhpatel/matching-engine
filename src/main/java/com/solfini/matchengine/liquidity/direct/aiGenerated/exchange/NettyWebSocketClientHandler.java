package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange;

// --------------------- Handler ---------------------

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.bitmart.BitmartStringCompress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;

public final class NettyWebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(NettyWebSocketClientHandler.class);

  private final WebSocketClientHandshaker handshaker;
  private ChannelPromise handshakeFuture;
  private final NettyWebSocketListenerInterface client;
  private final String name;

  public NettyWebSocketClientHandler(final WebSocketClientHandshaker handshaker, final NettyWebSocketListenerInterface client,
      final String name) { // TODO pass identifier and have logs accordingly
    this.handshaker = handshaker;
    this.client = client;
    this.name = name;
  }

  public ChannelFuture handshakeFuture() {
    return handshakeFuture;
  }

  @Override
  public void handlerAdded(final ChannelHandlerContext ctx) {
    handshakeFuture = ctx.newPromise();
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) {
    handshaker.handshake(ctx.channel());
  }

  @Override
  public void channelInactive(final ChannelHandlerContext ctx) {
    LOGGER.warn(name + " WS closed, scheduling reconnect...");
    client.setConnected(false);
    client.setAuthenticated(false);
    client.reconnect();
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) throws Exception {
    if (!handshaker.isHandshakeComplete()) {
      handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
      handshakeFuture.setSuccess();
      LOGGER.debug(name + " WS handshake completed");
      client.setConnected(true);
      return;
    }

    if (msg instanceof FullHttpResponse) {
      throw new IllegalStateException("Unexpected FullHttpResponse: " + msg);
    }

    final WebSocketFrame frame = (WebSocketFrame) msg;
    if (frame instanceof TextWebSocketFrame) {
      final String text = ((TextWebSocketFrame) frame).text();
      client.onMessage(text);
    } else if (frame instanceof BinaryWebSocketFrame && name.contains("BITMART")) {
      final BinaryWebSocketFrame binaryWebSocketFrame = (BinaryWebSocketFrame) frame;
      client.onMessage(BitmartStringCompress.decode(binaryWebSocketFrame.content()));
    } else if (frame instanceof BinaryWebSocketFrame) {
      final ByteBuf buf = ((BinaryWebSocketFrame) frame).content();
      byte[] bytes = null;
      try {
        // Extract bytes BEFORE releasing the buffer
        bytes = ByteBufUtil.getBytes(buf);
        LOGGER.debug(name + " binary message: " + bytes.length + " bytes");

        // Pass the extracted bytes to the listener
        if (bytes != null && bytes.length > 0) {
          client.onBinaryMessage(bytes);
        }
      } catch (final Exception e) {
        LOGGER.error(name + " error processing binary message", e);
      }
      // Don't manually release - Netty handles this automatically for WebSocketFrame
    } else if (frame instanceof PongWebSocketFrame) {
      LOGGER.debug(name + " WebSocket pong frame received");
      client.setLastPongReceived(System.currentTimeMillis());
    } else if (frame instanceof PingWebSocketFrame) {
      LOGGER.debug(name + " WebSocket ping frame received, sending pong");
      ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
    } else if (frame instanceof CloseWebSocketFrame) {
      LOGGER.warn(name + " WebSocket close frame received");
      ctx.channel().close();
    }
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    LOGGER.error(name + " WebSocket error", cause);
    if (!handshakeFuture.isDone()) {
      handshakeFuture.setFailure(cause);
    }
    client.setConnected(false);
    client.setAuthenticated(false);
    ctx.close();
  }
}
