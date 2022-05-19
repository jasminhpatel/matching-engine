package com.solfini.matchengine.stats;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.orderbook.OrderBook;

/**
 *
 * @author Chris Mack
 *
 */
public class TradeHistory implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(TradeHistory.class);

  private static final int INITIAL_CHART_SIZE = 512;

  private final ChartStats[] ONE_SECOND_CHART = new ChartStats[INITIAL_CHART_SIZE];
  private final ChartStats[] ONE_MINUTE_CHART = new ChartStats[INITIAL_CHART_SIZE];
  private final ChartStats[] FIVE_MINUTE_CHART = new ChartStats[INITIAL_CHART_SIZE];
  private final ChartStats[] FIFTEEN_MINUTE_CHART = new ChartStats[INITIAL_CHART_SIZE];
  private final ChartStats[] ONE_HOUR_CHART = new ChartStats[INITIAL_CHART_SIZE];

  private int oneSecondIndex = 0;
  private int oneMinIndex = 0;
  private int fiveMinIndex = 0;
  private int fifteenMinIndex = 0;
  private int oneHourIndex = 0;

  private final int pairId;
  private final int underlyerId;
  private final InstrumentPair underlyerPair;
  private final OrderBook underlyerOrderbook;
  private long startTime;
  private long lastTime;

  public TradeHistory(final int pairId, final int underlyerId) {
    this.startTime = System.currentTimeMillis();
    this.pairId = pairId;
    this.underlyerId = underlyerId;
    if (underlyerId > 0) {
      underlyerPair = InstrumentCache.getPair(underlyerId);
      if (underlyerPair != null)
        underlyerOrderbook = underlyerPair.getOrderBook();
      else
        underlyerOrderbook = null;
    } else {
      underlyerPair = null;
      underlyerOrderbook = null;
    }
  }


  public void setChartData(final int fileIndex, final int pointIndex, final ChartStats chartStats) {
    switch (fileIndex) {
      case 0:
        ONE_SECOND_CHART[pointIndex] = chartStats;
        break;
      case 1:
        ONE_MINUTE_CHART[pointIndex] = chartStats;
        break;
      case 2:
        FIVE_MINUTE_CHART[pointIndex] = chartStats;
        break;
      case 3:
        FIFTEEN_MINUTE_CHART[pointIndex] = chartStats;
        break;
      case 4:
        ONE_HOUR_CHART[pointIndex] = chartStats;
        break;
    }
  }

  // addToChart(trade.getTimestampMillis(), (int) trade.getLastPx(), trade.getOrderQty() - trade.getLeavesQty());

  public void addToChart(final long timestamp, final int price, final long quantity) {
    try {
      final double underlyerMark = underlyerOrderbook != null ? underlyerOrderbook.getUsdMark() : 0;
      long timeDiff = timestamp - startTime;
      oneSecondIndex = (int) ((timeDiff / ONE_SECOND) % ONE_SECOND_CHART.length);
      oneMinIndex = (int) ((timeDiff / ONE_MINUTE) % ONE_MINUTE_CHART.length);
      fiveMinIndex = (int) ((timeDiff / FIVE_MINUTE) % FIVE_MINUTE_CHART.length);
      fifteenMinIndex = (int) ((timeDiff / FIFTEEN_MINUTE) % FIFTEEN_MINUTE_CHART.length);
      oneHourIndex = (int) ((timeDiff / ONE_HOUR) % ONE_HOUR_CHART.length);
      long oneSecondRounded = timestamp - (timestamp % ONE_SECOND);
      long oneMinRounded = timestamp - (timestamp % ONE_MINUTE);
      long fiveMinRounded = timestamp - (timestamp % FIVE_MINUTE);
      long fifteenMinRounded = timestamp - (timestamp % FIFTEEN_MINUTE);
      long oneHourRounded = timestamp - (timestamp % ONE_HOUR);

      if (ONE_SECOND_CHART[oneSecondIndex] == null) {
        ONE_SECOND_CHART[oneSecondIndex] = new ChartStats(oneSecondRounded, price, quantity, underlyerMark);
      } else {
        if (Math.abs(timestamp - ONE_SECOND_CHART[oneSecondIndex].getStartTime()) > ONE_SECOND)
          ONE_SECOND_CHART[oneSecondIndex].reset(oneSecondRounded, price, quantity, underlyerMark);
        else
          ONE_SECOND_CHART[oneSecondIndex].add(oneSecondRounded, price, quantity, underlyerMark);
      }

      if (ONE_MINUTE_CHART[oneMinIndex] == null) {
        ONE_MINUTE_CHART[oneMinIndex] = new ChartStats(oneMinRounded, price, quantity, underlyerMark);
      } else {
        if (Math.abs(timestamp - ONE_MINUTE_CHART[oneMinIndex].getStartTime()) > ONE_MINUTE)
          ONE_MINUTE_CHART[oneMinIndex].reset(oneMinRounded, price, quantity, underlyerMark);
        else
          ONE_MINUTE_CHART[oneMinIndex].add(oneMinRounded, price, quantity, underlyerMark);
      }

      if (FIVE_MINUTE_CHART[fiveMinIndex] == null) {
        FIVE_MINUTE_CHART[fiveMinIndex] = new ChartStats(fiveMinRounded, price, quantity, underlyerMark);
      } else {
        if (Math.abs(timestamp - FIVE_MINUTE_CHART[fiveMinIndex].getStartTime()) > FIVE_MINUTE)
          FIVE_MINUTE_CHART[fiveMinIndex].reset(fiveMinRounded, price, quantity, underlyerMark);
        else
          FIVE_MINUTE_CHART[fiveMinIndex].add(fiveMinRounded, price, quantity, underlyerMark);
      }

      if (FIFTEEN_MINUTE_CHART[fifteenMinIndex] == null) {
        FIFTEEN_MINUTE_CHART[fifteenMinIndex] = new ChartStats(fifteenMinRounded, price, quantity, underlyerMark);
      } else {
        if (Math.abs(timestamp - FIFTEEN_MINUTE_CHART[fifteenMinIndex].getStartTime()) > FIFTEEN_MINUTE)
          FIFTEEN_MINUTE_CHART[fifteenMinIndex].reset(fifteenMinRounded, price, quantity, underlyerMark);
        else
          FIFTEEN_MINUTE_CHART[fifteenMinIndex].add(fifteenMinRounded, price, quantity, underlyerMark);
      }

      if (ONE_HOUR_CHART[oneHourIndex] == null) {
        ONE_HOUR_CHART[oneHourIndex] = new ChartStats(oneHourRounded, price, quantity, underlyerMark);
      } else {
        if (Math.abs(timestamp - ONE_HOUR_CHART[oneHourIndex].getStartTime()) > ONE_HOUR)
          ONE_HOUR_CHART[oneHourIndex].reset(oneHourRounded, price, quantity, underlyerMark);
        else
          ONE_HOUR_CHART[oneHourIndex].add(oneHourRounded, price, quantity, underlyerMark);
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }


  public final long getStartTime() {
    return startTime;
  }

  public final void setStartTime(final long startTime) {
    this.startTime = startTime;
  }

  public final ChartStats[] getONE_SECOND_CHART() {
    return ONE_SECOND_CHART;
  }

  public final ChartStats[] getONE_MINUTE_CHART() {
    return ONE_MINUTE_CHART;
  }

  public final ChartStats[] getFIVE_MINUTE_CHART() {
    return FIVE_MINUTE_CHART;
  }

  public final ChartStats[] getFIFTEEN_MINUTE_CHART() {
    return FIFTEEN_MINUTE_CHART;
  }

  public final ChartStats[] getONE_HOUR_CHART() {
    return ONE_HOUR_CHART;
  }

  public final int getOneSecondIndex() {
    return oneSecondIndex;
  }

  public final int getOneMinIndex() {
    return oneMinIndex;
  }

  public final int getFiveMinIndex() {
    return fiveMinIndex;
  }

  public final int getFifteenMinIndex() {
    return fifteenMinIndex;
  }

  public final int getOneHourIndex() {
    return oneHourIndex;
  }

  // aggregate stats for the prev rolling 5 mins
  public final ChartStats getRolling5minsStats() {
    final ChartStats aggregate = new ChartStats(System.currentTimeMillis(), 0, 0, 0);
    try {
      final ChartStats[] chartStats = getONE_MINUTE_CHART();
      final int lastIndex = getOneMinIndex();

      int counter = 0;
      int limit = 5;
      for (int i = lastIndex; i >= 0; i--) {
        if (counter > limit)
          break;
        counter++;
        aggregate.add(chartStats[i]);
      }

      if (counter < limit) {
        for (int i = chartStats.length - 1; i >= lastIndex + 2; i--) {
          if (counter > limit)
            break;
          counter++;
          aggregate.add(chartStats[i]);
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return aggregate;
  }

  // aggregate stats for the prev rolling 24 hours
  public final ChartStats getRolling24hrStats() {
    final ChartStats aggregate = new ChartStats(System.currentTimeMillis(), 0, 0, 0);
    try {
      final ChartStats[] chartStats = getONE_HOUR_CHART();
      final int lastIndex = getOneHourIndex();

      int counter = 0;
      int limit = 24;
      for (int i = lastIndex; i >= 0; i--) {
        if (counter > limit)
          break;
        counter++;
        aggregate.add(chartStats[i]);
      }

      if (counter < limit) {
        for (int i = chartStats.length - 1; i >= lastIndex + 2; i--) {
          if (counter > limit)
            break;
          counter++;
          aggregate.add(chartStats[i]);
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return aggregate;
  }

  // aggregate stats for the prev rolling 480 mins
  public final int getRolling8HrTWAP() {
    long dataCount = 0;
    long priceTotal = 0;

    try {
      final ChartStats[] chartStats = getONE_MINUTE_CHART();
      final int lastIndex = getOneMinIndex();

      int counter = 0;
      int limit = 480;
      for (int i = lastIndex; i >= 0; i--) {
        if (counter > limit)
          break;
        counter++;

        final ChartStats chartStat = chartStats[i];
        if (chartStat != null) {
          if (chartStat.getOpen() > 0) {
            dataCount++;
            priceTotal += chartStat.getOpen();
          }
          if (chartStat.getHigh() > 0) {
            dataCount++;
            priceTotal += chartStat.getHigh();
          }
          if (chartStat.getLow() > 0) {
            dataCount++;
            priceTotal += chartStat.getLow();
          }
          if (chartStat.getClose() > 0) {
            dataCount++;
            priceTotal += chartStat.getClose();
          }
        }
      }

      if (counter < limit) {
        for (int i = chartStats.length - 1; i >= lastIndex + 2; i--) {
          if (counter > limit)
            break;
          counter++;

          final ChartStats chartStat = chartStats[i];
          if (chartStat != null) {
            if (chartStat.getOpen() > 0) {
              dataCount++;
              priceTotal += chartStat.getOpen();
            }
            if (chartStat.getHigh() > 0) {
              dataCount++;
              priceTotal += chartStat.getHigh();
            }
            if (chartStat.getLow() > 0) {
              dataCount++;
              priceTotal += chartStat.getLow();
            }
            if (chartStat.getClose() > 0) {
              dataCount++;
              priceTotal += chartStat.getClose();
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    if (dataCount > 0)
      return (int) (priceTotal / dataCount);
    return 0;
  }


  // aggregate stats for the prev rolling secondsLimit seconds, default to 3 seconds
  public final int getRollingNSecondTWAP(final int secondsLimit) {
    long dataCount = 0;
    long priceTotal = 0;

    try {
      final ChartStats[] chartStats = getONE_SECOND_CHART();
      final int lastIndex = getOneSecondIndex();

      int counter = 0;
      for (int i = lastIndex; i >= 0; i--) {
        if (counter > secondsLimit)
          break;
        counter++;

        final ChartStats chartStat = chartStats[i];
        if (chartStat != null) {
          if (chartStat.getOpen() > 0) {
            dataCount++;
            priceTotal += chartStat.getOpen();
          }
          if (chartStat.getHigh() > 0) {
            dataCount++;
            priceTotal += chartStat.getHigh();
          }
          if (chartStat.getLow() > 0) {
            dataCount++;
            priceTotal += chartStat.getLow();
          }
          if (chartStat.getClose() > 0) {
            dataCount++;
            priceTotal += chartStat.getClose();
          }
        }
      }

      if (counter < secondsLimit) {
        for (int i = chartStats.length - 1; i >= lastIndex + 2; i--) {
          if (counter > secondsLimit)
            break;
          counter++;

          final ChartStats chartStat = chartStats[i];
          if (chartStat != null) {
            if (chartStat.getOpen() > 0) {
              dataCount++;
              priceTotal += chartStat.getOpen();
            }
            if (chartStat.getHigh() > 0) {
              dataCount++;
              priceTotal += chartStat.getHigh();
            }
            if (chartStat.getLow() > 0) {
              dataCount++;
              priceTotal += chartStat.getLow();
            }
            if (chartStat.getClose() > 0) {
              dataCount++;
              priceTotal += chartStat.getClose();
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    if (dataCount > 0)
      return (int) (priceTotal / dataCount);
    return 0;
  }

}
