package com.solfini.report.check;

import java.util.Map;

public class InternalCheckReport {

  public InternalCheckReport() {}

  private OrderBookCheckResult orderBookCheckResult;
  private Map<Integer, ReconciliationResult> reconciliationResult;
  private UserCheckReport userCheckReport;

  public InternalCheckReport(final OrderBookCheckResult orderBookCheckResult, final Map<Integer, ReconciliationResult> reconciliationResult,
      UserCheckReport userCheckReport) {
    this.orderBookCheckResult = orderBookCheckResult;
    this.reconciliationResult = reconciliationResult;
    this.userCheckReport = userCheckReport;
  }

  public final OrderBookCheckResult getOrderBookCheckResult() {
    return orderBookCheckResult;
  }

  public final Map<Integer, ReconciliationResult> getReconciliationResult() {
    return reconciliationResult;
  }

  public final UserCheckReport getUserCheckReport() {
    return userCheckReport;
  }

  public final void setOrderBookCheckResult(final OrderBookCheckResult orderBookCheckResult) {
    this.orderBookCheckResult = orderBookCheckResult;
  }

  public final void setReconciliationResult(final Map<Integer, ReconciliationResult> reconciliationResult) {
    this.reconciliationResult = reconciliationResult;
  }

  public final void setUserCheckReport(final UserCheckReport userCheckReport) {
    this.userCheckReport = userCheckReport;
  }

  @Override
  public String toString() {
    return "InternalCheckReport []";
  }


}
