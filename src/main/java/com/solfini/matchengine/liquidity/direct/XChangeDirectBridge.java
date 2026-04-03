package com.solfini.matchengine.liquidity.direct;

import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import com.solfini.util.StringUtil;

public class XChangeDirectBridge {

  public static final org.knowm.xchange.dto.account.Balance toXBalance(final ExchangeBalance exchangeBalance) {
    final Balance balance = new Balance(exchangeBalance.getCurrency(), BigDecimal.valueOf(exchangeBalance.getTotal()),
        BigDecimal.valueOf(exchangeBalance.getAvailable()), BigDecimal.valueOf(exchangeBalance.getFrozen()),
        BigDecimal.valueOf(exchangeBalance.getBorrowed()), BigDecimal.valueOf(exchangeBalance.getLoaned()),
        BigDecimal.valueOf(exchangeBalance.getWithdrawing()), BigDecimal.valueOf(exchangeBalance.getDepositing()),
        exchangeBalance.getTimestamp());
    return balance;
  }

  public static final ExchangeBalance toExchangeBalance(final org.knowm.xchange.dto.account.Balance balance) {
    final ExchangeBalance exchangeBalance =
        new ExchangeBalance(balance.getCurrency(), StringUtil.toDouble(balance.getTotal()), StringUtil.toDouble(balance.getAvailable()),
            StringUtil.toDouble(balance.getFrozen()), StringUtil.toDouble(balance.getBorrowed()), StringUtil.toDouble(balance.getLoaned()),
            StringUtil.toDouble(balance.getWithdrawing()), StringUtil.toDouble(balance.getDepositing()), balance.getTimestamp());
    return exchangeBalance;
  }

  public static final void setExchangeBalance(final org.knowm.xchange.dto.account.Balance balanceFrom, final ExchangeBalance exchangeBalanceTo) {
    exchangeBalanceTo.set(balanceFrom.getCurrency(), StringUtil.toDouble(balanceFrom.getTotal()), StringUtil.toDouble(balanceFrom.getAvailable()),
        StringUtil.toDouble(balanceFrom.getFrozen()), StringUtil.toDouble(balanceFrom.getBorrowed()), StringUtil.toDouble(balanceFrom.getLoaned()),
        StringUtil.toDouble(balanceFrom.getWithdrawing()), StringUtil.toDouble(balanceFrom.getDepositing()), balanceFrom.getTimestamp());
  }
}
