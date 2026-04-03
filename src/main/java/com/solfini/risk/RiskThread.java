package com.solfini.risk;

import com.solfini.matchengine.liquidity.LiquiditySubscriptionCache;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.InstrumentCache;
import com.solfini.user.UserCache;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class RiskThread implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(RiskThread.class);

  private final IdleStrategy idleStrategy;

  public RiskThread(final IdleStrategy idleStrategy) {
    this.idleStrategy = idleStrategy;

  }

  public void run() {
    LOGGER.info(LOG_FMT_2, ">>> starting RiskThread ,", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss());
    final double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];

    while (true) {
      try {
        // we need to force a memory barrier sync
        Thread.sleep(0);

        // calc risk
        UserCache.processRisk(usdMarkPricesToSet);
        // handle QT imbalance
        LiquiditySubscriptionCache.processImbalance();

        if (Context.isDebugLogRisk() && LOGGER.isDebugEnabled()) {
          LOGGER.debug(LOG_FMT_2, ">>>> stringValue()=", UserRiskCache.stringValue());
          Thread.sleep(5000); // slow it down for tracing
        }

        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }


}
