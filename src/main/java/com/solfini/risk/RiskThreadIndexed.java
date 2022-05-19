package com.solfini.risk;

import java.util.concurrent.ConcurrentHashMap;
import org.agrona.concurrent.IdleStrategy;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.InstrumentCache;
import com.solfini.matchengine.message.admin.CollateralSwapMessage;
import com.solfini.preordercheck.MarginPreOrderCheckAndSettle;
import com.solfini.preordercheck.PreOrderCheck;
import com.solfini.user.User;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class RiskThreadIndexed implements Runnable, Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(RiskThreadIndexed.class);

  private final IdleStrategy idleStrategy;
  private final PreOrderCheck marginCheck = new MarginPreOrderCheckAndSettle();
  private final int[] instruments;
  private final int[] sides;
  private final int[] buckets;
  private final boolean isCollateralSwapEnabled = Context.isCollateralSwapEnabled();

  public RiskThreadIndexed(final IdleStrategy idleStrategy, final int[] instruments, final int[] sides, final int[] buckets) {
    this.idleStrategy = idleStrategy;
    this.instruments = instruments;
    this.sides = sides;
    this.buckets = buckets;
  }

  public void run() {
    LOGGER.info(LOG_FMT_6, ">>> starting RiskThreadIndexed ,", StringUtil.getCurrentDateYYYYMMDDHHMMSSsss(), NAME_EQ,
        Thread.currentThread().getName(), ISCOLLATERALSWAPENABLED_EQ, isCollateralSwapEnabled);
    final double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];
    while (true) {
      try {
        // we need to force a memory barrier sync
        Thread.sleep(0);

        for (int pairId = 0; pairId < instruments.length; pairId++) {
          for (int side = 0; side < sides.length; side++) {
            for (int bucket = 0; bucket < buckets.length; bucket++) {
              final ConcurrentHashMap<Integer, User> map = UserRiskCache.getIndex(pairId, side, bucket);
              Thread.sleep(2000);

              if (!map.isEmpty()) {
                for (final User user : map.values()) {
                  if (user != null) {
                    marginCheck.updateRiskAndCalcBankruptcyPrices(user, usdMarkPricesToSet);
                    UserRiskCache.reIndex(user);
                    if (isCollateralSwapEnabled)
                      CollateralSwapMessage.checkCollateralBalance(user);
                  }
                }
              }

            }
          }
        }

        idleStrategy.idle();
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
      }
    }
  }


}
