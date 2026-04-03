package com.solfini.matchengine.liquidity.direct.aiGenerated.exchange;

public interface NettyWebSocketListenerInterface {
  public void onMessage(final String message);

  public void connect() throws Exception;

  public void reconnect();

  public void disconnect();

  public boolean isConnected();

  public void setConnected(final boolean connected);

  public void setAuthenticated(final boolean authenticated);

  public void setLastPongReceived(final long timeStamp);

  public void onBinaryMessage(final byte[] bytes);
}
