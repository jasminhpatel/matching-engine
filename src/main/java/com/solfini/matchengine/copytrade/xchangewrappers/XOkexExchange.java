package com.solfini.matchengine.copytrade.xchangewrappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.service.account.AccountService;

import java.util.List;
import java.util.Map;

public class XOkexExchange extends XExchange {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(XOkexExchange.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private final boolean futures;

  public XOkexExchange(final Exchange exchange, final boolean futures) {
    super(exchange);
    this.futures = futures;
  }

  @Override
  public List<SymbolStatus> getExchangeInstrumentsFull() {
    return List.of();
  }

  public org.knowm.xchange.instrument.Instrument getInstrument(final CurrencyPair currencyPair, boolean isFuture) {
    try {
      final List<org.knowm.xchange.instrument.Instrument> instruments = getExchange().getExchangeInstruments();
      org.knowm.xchange.instrument.Instrument instrument = null;
      for (org.knowm.xchange.instrument.Instrument i : instruments) {
        if (i.getBase().getSymbol().equalsIgnoreCase(currencyPair.getBase().getSymbol()) && i.getCounter().getSymbol()
            .equalsIgnoreCase(currencyPair.getCounter().getSymbol())) {
          if (isFuture && i instanceof FuturesContract && "SWAP".equalsIgnoreCase(((FuturesContract) i).getPrompt())) {
            instrument = i;
            break;
          } else if (!isFuture && !(i instanceof FuturesContract)) {
            instrument = i;
            break;
          }
        }
      }
      return instrument;
    } catch (Exception e) {
      LOGGER.info(Constants.LOG_FMT_2, "Failed to load instrument. ", currencyPair.toString());
    }
    return null;
  }

  public Balance getBalanceFromExchange(final String symbol) {
    final Balance balance = new Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = this.exchange.getAccountService();
      if (accountService == null)
        return balance;
      if (accountService.getAccountInfo().getWallets() == null) {
        return balance;
      }
      Wallet wallet = null;
      if (futures) {
        wallet = accountService.getAccountInfo().getWallets().get("futures");
      } else {
        wallet = accountService.getAccountInfo().getWallets().get("trading");
      }
      if (wallet == null)
        return balance;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      final Currency currency = Currency.getInstance(symbol);
      if (currency == null) {
        return balance;
      }

      org.knowm.xchange.dto.account.Balance bal = balances.get(currency);

      if (bal != null) {
        balance.setCoinBalance(MbxMath.roundToBestPrecision(bal.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      LOGGER.error("Error occurred wile fetching balance. ", e);
    }
    return balance;
  }
}
