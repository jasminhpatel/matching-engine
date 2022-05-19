package com.solfini.common;

/**
 * 
 * @author Chris Mack
 *
 */
public abstract class AdminMessage extends Message {
  public static final String ALL = "ALL";

  protected long connectionId;
  protected long externalId;
  protected long triggerTimeMillis;
  protected String senderInstanceId;
  protected String routeToDestination;


  public long getConnectionId() {
    return connectionId;
  }

  public void setConnectionId(final long connectionId) {
    this.connectionId = connectionId;
  }

  public long getExternalId() {
    return externalId;
  }

  public void setExternalId(final long externalId) {
    this.externalId = externalId;
  }

  public long getTriggerTimeMillis() {
    return triggerTimeMillis;
  }

  public void setTriggerTimeMillis(final long triggerTimeMillis) {
    this.triggerTimeMillis = triggerTimeMillis;
  }

  public final String getRouteToDestination() {
    if (routeToDestination == null)
      return ALL;
    return routeToDestination;
  }

  public final void setRouteToDestination(final String routeToDestination) {
    this.routeToDestination = routeToDestination;
  }

  public final String getSenderInstanceId() {
    return senderInstanceId;
  }

  public final void setSenderInstanceId(final String senderInstanceId) {
    this.senderInstanceId = senderInstanceId;
  }
}
