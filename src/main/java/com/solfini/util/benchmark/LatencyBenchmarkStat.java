package com.solfini.util.benchmark;

import com.solfini.common.Message;

public class LatencyBenchmarkStat {
  private long inputTime = 0;
  private long publishTime = 0;
  private long decodedTime = 0;
  private long matchTime = 0;

  public LatencyBenchmarkStat() {
    // do nothing
  }

  public final long getInputTime() {
    return inputTime;
  }

  public final void setInputTime(final long inputTime) {
    this.inputTime = inputTime;
  }

  public final long getDecodedTime() {
    return decodedTime;
  }

  public final void setDecodedTime(final long decodedTime) {
    this.decodedTime = decodedTime;
  }

  public final long getMatchTime() {
    return matchTime;
  }

  public final void setMatchTime(final long matchTime) {
    this.matchTime = matchTime;
  }

  public final long getPublishTime() {
    return publishTime;
  }

  public final void setPublishTime(final long publishTime) {
    this.publishTime = publishTime;
  }

  // Reset all values to zero
  public void reset() {
    inputTime = 0;
    publishTime = 0;
    decodedTime = 0;
    matchTime = 0;
  }

  // Copies values from the message provided
  public void set(Message msg) {
    inputTime = msg.getInputTime();
    decodedTime = msg.getDecodedTime();
    matchTime = msg.getMatchTime();
  }
}
