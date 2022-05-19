package com.solfini.report.check;

import com.google.common.base.Stopwatch;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.user.UserCache;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class InternalCheckService implements Constants {

  private static final CustomLogger LOGGER = CustomLogger.getLogger(InternalCheckService.class);
  private OrderBookStatesChecker orderBookStatesChecker = new OrderBookStatesChecker();
  private Stopwatch stopwatch = Stopwatch.createUnstarted();


  public InternalCheckReport internalCheck(final long snapId) {

    stopwatch.start();

    if (LOGGER.isWarnEnabled())
      LOGGER.warn(LOG_FMT_2, "Try to internal check with snapId-", snapId);

    final OrderBookCheckResult orderBookCheckResult = orderBookStatesChecker.checkOrderBookStatus();
    final Map<Integer, ReconciliationResult> reconciliationResult = UserCache.processReconciliation();
    final InternalCheckReport report = new InternalCheckReport(orderBookCheckResult, reconciliationResult, null);
    logReport(snapId, report);

    if (LOGGER.isInfoEnabled())
      LOGGER.info(LOG_FMT_4, "Internal status check with snapID-", snapId, " cost " + stopwatch.elapsed(TimeUnit.MILLISECONDS),
        " milliseconds");
    stopwatch.stop();
    return report;
  }

  protected void logReport(final long snapId, final InternalCheckReport report) {
    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(LOG_FMT_2, "Now log status check/reconciliation report with snapId", snapId);
      LOGGER.warn(LOG_FMT_2, "Internal Status result:", report.getOrderBookCheckResult().getCheckResultStr());
      LOGGER.warn(LOG_FMT_2, "Reconciliation done, result: ", report.getReconciliationResult().toString());
    }
  }
}
