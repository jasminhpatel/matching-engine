package com.solfini.testtcp;

import java.nio.channels.SocketChannel;

public class ConnectionWrapper {
  private final SocketChannel socketChannel;
  private final long responseChannelId;
  private String senderCompId;

  public ConnectionWrapper(final long responseChannelId, final SocketChannel socketChannel) {
    this.responseChannelId = responseChannelId;
    this.socketChannel = socketChannel;
  }

  public final String getSenderCompId() {
    return senderCompId;
  }

  public final void setSenderCompId(final String senderCompId) {
    this.senderCompId = senderCompId;
  }

  public final SocketChannel getSocketChannel() {
    return socketChannel;
  }

  public final long getResponseChannelId() {
    return responseChannelId;
  }

  @Override
  public String toString() {
    return "ConnectionWrapper [socketChannel=" + socketChannel + ", responseChannelId=" + responseChannelId + ", senderCompId="
        + senderCompId + "]";
  }
}
