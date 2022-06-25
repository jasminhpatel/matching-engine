package com.solfini.preordercheck;

import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.user.User;

/**
 *
 * @author Chris Mack
 *
 */
public interface PreOrderCheck {

  public boolean checkOrder(final Order order, final int referencePrice);

  public boolean updateFill(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId);

  public boolean updateFillPhysicalSettle(final Order order, final int referencePrice, final long referenceQuantity,
      final ExecutionReportMessage execReport, final double quotedUsdMark, final double settleCoinUsdMark, final double quotedCoinUsdMark,
      final boolean isMaker, final Order causingMessage, final int counterpartyId, final double baseCoinUsdMark);

  public boolean updateCancel(final Order order);

  public void updateRisk(final User user, final double[] usdMarkPricesToSet);

  public void updateRiskAndCalcBankruptcyPrices(final User user, final double[] usdMarkPricesToSet);

  public void addOrderDuringRebuild(final Order newPtr, final int priceInt);

  public boolean checkOrderNoValidation(final Order order, final int referencePrice);

  public boolean updateCancelNoValidation(final Order order);
}
