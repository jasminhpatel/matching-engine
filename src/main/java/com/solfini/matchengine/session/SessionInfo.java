package com.solfini.matchengine.session;

import java.util.concurrent.atomic.AtomicLong;

import com.solfini.common.Constants;

/**
 * Contains connection session info to route messages correctly
 *
 * @author Chris Mack
 *
 */
public class SessionInfo implements Constants {

  private long connectionId;

  private String senderCompId;

  private final AtomicLong messageSequenceNumber = new AtomicLong(1);

  private boolean authenticated;


  public SessionInfo() {}

  public SessionInfo(final String senderCompId) {
    this.senderCompId = senderCompId;
    this.authenticated = true;
  }

  public final long getConnectionId() {
    return connectionId;
  }

  public final void setConnectionId(final long connectionId) {
    this.connectionId = connectionId;
  }

  public final String getSenderCompId() {
    return senderCompId;
  }

  public final void setSenderCompId(final String senderCompId) {
    this.senderCompId = senderCompId;
  }

  public final long getMessageSequenceNumber() {
    return messageSequenceNumber.get();
  }

  public final void setMessageSequenceNumber(final long newValue) {
    this.messageSequenceNumber.set(newValue);
  }

  public final long incrementAndGetMessageSequenceNumber() {
    return messageSequenceNumber.incrementAndGet();
  }

  public final boolean isAuthenticated() {
    return authenticated;
  }

  public final void setAuthenticated(final boolean authenticated) {
    this.authenticated = authenticated;
  }

  @Override
  public final String toString() {
    return "SessionInfo [connectionId=" + connectionId + SENDERCOMPID_EQ + senderCompId + "]";
  }

}
