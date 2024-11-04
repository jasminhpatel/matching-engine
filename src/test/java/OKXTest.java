import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.okex.OkexExchange;
import org.knowm.xchange.service.account.AccountService;

import java.util.Map;

import static com.solfini.common.Constants.*;
import static org.knowm.xchange.okex.OkexExchange.PARAM_PASSPHRASE;

public class OKXTest {
  public static void main(String[] args) {
    boolean isFutures = true;
    ExchangeSpecification specification = new OkexExchange().getDefaultExchangeSpecification();
    specification.setApiKey("b3061e75-1132-404d-9ef7-90fc9280a0d3");
    specification.setSecretKey("8F137EA57CBD54F738A44FDF1448DA20");
    //specification.setPassword();
    specification.setExchangeSpecificParametersItem(PARAM_PASSPHRASE, "Password@77");
    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);
    XExchange.Balance balance = getBalanceFromExchange(exchange, "ETH");
    System.out.println(balance.toJson());
  }

  public static XExchange.Balance getBalanceFromExchange(final Exchange exchange, final String symbol) {
    final XExchange.Balance balance = new XExchange.Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null)
        return balance;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balance;
      final Map<Currency, Balance> balances = wallet.getBalances();
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
      e.printStackTrace();
    }
    return balance;
  }

  public static XExchange.Balance getStableCoinBalanceFromExchange(final Exchange exchange) {
    final XExchange.Balance balance = new XExchange.Balance();
    balance.setLastUpdated(System.currentTimeMillis());
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null)
        return balance;
      final Wallet wallet = accountService.getAccountInfo().getWallet();
      if (wallet == null)
        return balance;
      final Map<Currency, org.knowm.xchange.dto.account.Balance> balances = wallet.getBalances();
      if (balances == null)
        return balance;

      org.knowm.xchange.dto.account.Balance usdBalance = balances.get(Currency.getInstance(USD));
      org.knowm.xchange.dto.account.Balance usdcBalance = balances.get(Currency.getInstance(USDC));
      org.knowm.xchange.dto.account.Balance usdtBalance = balances.get(Currency.getInstance(USDT));
      if (usdBalance != null) {
        balance.setUsdBalance(MbxMath.roundToBestPrecision(usdBalance.getAvailable().doubleValue()));
      }
      if (usdcBalance != null) {
        balance.setUsdcBalance(MbxMath.roundToBestPrecision(usdcBalance.getAvailable().doubleValue()));
      }
      if (usdtBalance != null) {
        balance.setUsdtBalance(MbxMath.roundToBestPrecision(usdtBalance.getAvailable().doubleValue()));
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return balance;
  }

}
