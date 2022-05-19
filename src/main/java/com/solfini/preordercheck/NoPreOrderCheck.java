package com.solfini.preordercheck;

import com.solfini.common.Context;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.user.User;

/**
 *
 * @author Chris Mack
 *
 */
public class NoPreOrderCheck implements PreOrderCheck {

  public boolean checkOrder(final Order order, final int referencePrice) {
    return true;
  }

  public boolean updateFill(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId) {
    Context.getMatcherToPublisherQueue().add(execReport);
    return true;
  }

  public boolean updateFillPhysicalSettle(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId, final double baseCoinUsdMark) {
    Context.getMatcherToPublisherQueue().add(execReport);
    return true;
  }

  public boolean updateCancel(final Order order) {
    return true;
  }

  @Override
  public void updateRisk(final User user, final double[] usdMarkPricesToSet) {
    // do nothing
  }

  @Override
  public void updateRiskAndCalcBankruptcyPrices(final User user, final double[] usdMarkPricesToSet) {
    // do nothing
  }

  @Override
  public void addOrderDuringRebuild(Order newPtr, int priceInt) {
    // do nothing
  }

  @Override
  public boolean checkOrderNoValidation(Order order, int referencePrice) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean updateCancelNoValidation(Order order) {
    // TODO Auto-generated method stub
    return false;
  }
}
