package com.solfini.common;

import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;

public class DecodeUtil {
  private DecodeUtil() {
    // hidden default constructor
  }

  public static final Side getSide(final int s) {
    switch (s) {
      case 1:
        return Side.BUY;
      case 2:
        return Side.SELL;
      default:
        return Side.BUY;
    }
  }

  public static final OrdType getOrdType(final int s) {
    switch (s) {
      case 1:
        return OrdType.MARKET;
      case 2:
        return OrdType.LIMIT;
      case 3:
        return OrdType.STOP;
      case 4:
        return OrdType.STOP_LIMIT;
      default:
        return OrdType.LIMIT;
    }
  }

  public static final TimeInForce getTimeInForce(final int s) {
    switch (s) {
      case 0:
        return TimeInForce.DAY;
      case 1:
        return TimeInForce.GOOD_TILL_CANCEL;
      case 2:
        return TimeInForce.AT_THE_OPENING;
      case 3:
        return TimeInForce.IMMEDIATE_OR_CANCEL;
      case 4:
        return TimeInForce.FILL_OR_KILL;
      case 5:
        return TimeInForce.GOOD_TILL_CROSSING;
      case 6:
        return TimeInForce.GOOD_TILL_DATE;
      case 7:
        return TimeInForce.AT_THE_CLOSE;
      default:
        return TimeInForce.GOOD_TILL_CANCEL;
    }
  }
}
