package com.solfini.risk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.instrument.Position;
import com.solfini.user.User;

/**
 *
 * @author Chris Mack
 *
 */
public class UserRiskCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(UserRiskCache.class);

  public static final int RISK_BUCKET_ALL_POS = 0;
  public static final int RISK_BUCKET_LEVERAGE_1 = 1;
  public static final int RISK_BUCKET_LEVERAGE_2 = 2;
  public static final int RISK_BUCKET_LEVERAGE_3 = 3;
  public static final int RISK_BUCKET_LEVERAGE_4 = 4;
  public static final int RISK_BUCKET_LEVERAGE_5 = 5;
  public static final int RISK_BUCKET_LEVERAGE_6 = 6;
  public static final int RISK_BUCKET_LEVERAGE_7 = 7;
  public static final int RISK_BUCKET_LEVERAGE_8 = 8;
  public static final int RISK_BUCKET_LEVERAGE_9 = 9;
  public static final int RISK_BUCKET_LEVERAGE_10 = 10;
  public static final int RISK_BUCKET_SIZE_1 = 11;
  public static final int RISK_BUCKET_SIZE_2 = 12;
  public static final int RISK_BUCKET_SIZE_3 = 13;
  public static final int RISK_BUCKET_SIZE_4 = 14;
  public static final int RISK_BUCKET_SIZE_5 = 15;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_1 = 16;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_2 = 17;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_3 = 18;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_4 = 19;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_5 = 20;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_6 = 21;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_7 = 22;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_8 = 23;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_9 = 24;
  public static final int RISK_BUCKET_LEVERAGE_AND_PNL_10 = 25;

  private static final int MAX_BUCKETS = 26;
  private static final int MAX_PAIRS = Math.max(128, InstrumentCache.getPairCapacity() + 16);
  private static final ConcurrentHashMap<Integer, User>[][][] data = build(); // [instrumentId][long/short][bucket]

  private UserRiskCache() {
    // hidden default constructor
  }

  // cache disruptors for each bucket
  // [instrumentId][long/short][bucket][userId]
  public static final ConcurrentHashMap<Integer, User>[][][] build() {
    @SuppressWarnings("unchecked")
    final ConcurrentHashMap<Integer, User>[][][] data = new ConcurrentHashMap[MAX_PAIRS][2][MAX_BUCKETS];
    try {
      for (int i = 0; i < MAX_PAIRS; i++) {
        InstrumentPair pair = null;
        if (InstrumentCache.getPairCapacity() > i)
          pair = InstrumentCache.getPair(i);
        if (pair == null)
          continue;

        for (int j = 0; j < 2; j++) {
          for (int k = 0; k < MAX_BUCKETS; k++) {
            int defaultSize = pair.getEstimatedUserCount();
            data[i][j][k] = new ConcurrentHashMap<>(defaultSize);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return data;
  }

  private static final void addIndex(final int pairId, final int side, final int bucket, final User user) {
    try {
      if (bucket <= RISK_BUCKET_LEVERAGE_10) {
        for (int i = RISK_BUCKET_LEVERAGE_1; i < RISK_BUCKET_LEVERAGE_10; i++) {
          if ((i != bucket) && (null != data[pairId][side][i])) {
            data[pairId][side][i].remove(user.getId());
          }
        }
      } else if (bucket <= RISK_BUCKET_SIZE_5) {
        for (int i = RISK_BUCKET_SIZE_1; i < RISK_BUCKET_SIZE_5; i++) {
          if ((i != bucket) && (null != data[pairId][side][i])) {
            data[pairId][side][i].remove(user.getId());
          }
        }
      } else if (bucket <= RISK_BUCKET_LEVERAGE_AND_PNL_10) {
        for (int i = RISK_BUCKET_LEVERAGE_AND_PNL_1; i < RISK_BUCKET_LEVERAGE_AND_PNL_10; i++) {
          if ((i != bucket) && (null != data[pairId][side][i])) {
            data[pairId][side][i].remove(user.getId());
          }
        }
      }

      final Map<Integer, User> map = getIndex(pairId, side, bucket);
      map.put(user.getId(), user);

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static final ConcurrentHashMap<Integer, User> getIndex(final int pairId, final int side, final int bucket) {
    ConcurrentHashMap<Integer, User> map = data[pairId][side][bucket];
    if (map == null) {
      data[pairId][side][bucket] = new ConcurrentHashMap<>();
      map = data[pairId][side][bucket];
    }
    return map;
  }


  public static final void reIndex(final User user) {
    try {
      double marginRatio = user.getUsdValue() > 0 ? user.getUsdMarginMaintValue() / user.getUsdValue() : 0;
      if (user.getUsdValue() < 0)
        marginRatio = 1;

      final Position[] positionArr = user.getPositionArr();
      for (final Position position : positionArr) {
        if (position != null && position.getQuantity() != 0) {
          final InstrumentPair instrumentPair = InstrumentCache.getPair(position.getInstrumentId());
          if (instrumentPair == null)
            continue;
          int side = position.getQuantity() >= 0 ? 0 : 1;

          double usdMark = instrumentPair.getIndexFeedUsdMark();
          if (usdMark == 0) {
            usdMark = instrumentPair.getOrderBook().getUsdMark();
          }
          final double notional = Math.abs(usdMark * position.getQuantity());

          // risk buckets by margin ratio
          if (marginRatio > 0.98) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_10, user);
          } else if (marginRatio > 0.96) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_9, user);
          } else if (marginRatio > 0.92) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_8, user);
          } else if (marginRatio > 0.86) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_7, user);
          } else if (marginRatio > 0.80) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_6, user);
          } else if (marginRatio > 0.70) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_5, user);
          } else if (marginRatio > 0.60) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_4, user);
          } else if (marginRatio > 0.50) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_3, user);
          } else if (marginRatio > 0.20) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_2, user);
          } else {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_1, user);
          }


          // risk buckets by notional
          if (notional > 1_000_000) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_SIZE_5, user);
          } else if (notional > 250_000) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_SIZE_4, user);
          } else if (notional > 100_000) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_SIZE_3, user);
          } else if (notional > 10_000) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_SIZE_2, user);
          } else {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_SIZE_1, user);
          }

          double pnlPercent = 0;
          if (user.getUsdValue() > 0 && user.getUsdUnrealized() > 0)
            pnlPercent = user.getUsdUnrealized() / (Math.max(1, user.getUsdValue() - user.getUsdUnrealized()));

          final double leveragePnl = pnlPercent * marginRatio;

          // risk buckets by leveragePnl=pnlPercent*marginRatio
          if (leveragePnl > 4) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_10, user);
          } else if (leveragePnl > 3) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_9, user);
          } else if (leveragePnl > 2) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_8, user);
          } else if (leveragePnl > 1) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_7, user);
          } else if (leveragePnl > .8) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_6, user);
          } else if (leveragePnl > .6) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_5, user);
          } else if (leveragePnl > .4) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_4, user);
          } else if (leveragePnl > .2) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_3, user);
          } else if (leveragePnl > 0) {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_2, user);
          } else {
            addIndex(position.getInstrumentId(), side, RISK_BUCKET_LEVERAGE_AND_PNL_1, user);
          }

        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }

  public static final String stringValue() {
    final StringBuilder sb = new StringBuilder("Risk Buckets-\n");
    for (int i = 0; i < 32; i++) {
      for (int j = 0; j < 2; j++) {
        for (int k = 0; k < MAX_BUCKETS; k++) {
          final ConcurrentHashMap<Integer, User> map = data[i][j][k];
          if (map == null || map.size() == 0)
            continue;
          for (final User user : map.values()) {
            if (user.getPositionArr()[i] == null)
              continue;
            sb.append("bucket[").append(i).append("][").append(j).append("][").append(k).append("][").append(user.getId())
                .append("]=" + user.getPositionArr()[i] + "\n");
          }
        }
      }
    }
    return sb.toString();
  }
}
