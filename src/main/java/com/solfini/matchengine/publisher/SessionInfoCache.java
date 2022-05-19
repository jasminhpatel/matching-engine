package com.solfini.matchengine.publisher;

import java.util.HashMap;
import java.util.Map;
import com.solfini.matchengine.session.SessionInfo;

public class SessionInfoCache {
  public static final SessionInfo DEFAULT = new SessionInfo("1000000009");
  private static final Map<String, SessionInfo> senderCompToSessionInfoMap = new HashMap<>();
  private static final SessionInfo[] tradeApiArr = {DEFAULT};
  private static final int TRADE_API_ARR_INDEX = 0;

  private SessionInfoCache() {}

  public static final void put(final String senderCompId, final SessionInfo sessionInfo) {
    // Not required
  }

  public static final SessionInfo get(final String senderCompId) {
    return DEFAULT;
  }

  public static final boolean containsKey(final String senderCompId) {
    return senderCompToSessionInfoMap.containsKey(senderCompId);
  }

  public static final void putTradeApi(final SessionInfo sessionInfo) {
    // check for dups and resize
    tradeApiArr[TRADE_API_ARR_INDEX] = sessionInfo;
  }

  public static final SessionInfo[] getTradeApiArr() {
    return tradeApiArr;
  }
}
