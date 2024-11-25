import com.solfini.matchengine.copytrade.ExternalExchangeUtil;
import com.solfini.matchengine.copytrade.xchangewrappers.XExchange;
import com.solfini.sbe.encoder.Side;
import com.solfini.util.MbxMath;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.derivative.FuturesContract;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.orders.OrderQueryParams;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.solfini.common.Constants.*;
import static org.knowm.xchange.bybit.dto.account.walletbalance.BybitAccountType.UNIFIED;
import static org.knowm.xchange.utils.DigestUtils.bytesToHex;

public class ByBitTest {
  public static final String HMAC_SHA_256 = "HmacSHA256";
  public static void main(String[] args) throws Exception {

    //System.out.println(getSignature());
    boolean isFutures = true;
    ExchangeSpecification specification = new BybitExchange().getDefaultExchangeSpecification();
    specification.setApiKey("VeMQdq6SgQQW2bT86V");
    specification.setSecretKey("hsOjuLSeJunPe1PSy36WK0TVs1T7wQS5p8kp");

    specification.setExchangeSpecificParametersItem(BybitExchange.SPECIFIC_PARAM_ACCOUNT_TYPE, UNIFIED);

    Exchange exchange = ExchangeFactory.INSTANCE.createExchange(specification);
    XExchange.Balance balance = getBalanceFromExchange(exchange, "ETH");
    System.out.println(balance.toJson());
    //XExchange.Balance balance1 = getStableCoinBalanceFromExchange(exchange);
    //System.out.println(balance1.toJson());
    //org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), isFutures);
    //String ret = sendOrder(exchange, "2625.0", isFutures);
    //String ret = "1782992506926144000";
    //String ret = "1783317500851919360";
    //String ret = "c929769f-28da-4ecd-b072-46fe5517c5d7";
    String ret = "fd70f52b-62ba-45cb-97c7-c9bb222bd516";
    System.out.println(ret);
    getOrder(exchange, ret);

    //System.out.println(isValidExchangeApiKeys(exchange));
    //getOrder(exchange, ret);
    //getOrder(exchange, "411861921081");
    //getOrders(exchange, instrument);

    System.out.println("Done");
  }

  public static String getSignature() throws InvalidKeyException, NoSuchAlgorithmException {
    String secretKeyBase64 = "hsOjuLSeJunPe1PSy36WK0TVs1T7wQS5p8kp";
    byte[] secBytes = secretKeyBase64.getBytes(StandardCharsets.UTF_8);
    final SecretKey secretKey = new SecretKeySpec(secBytes, HMAC_SHA_256);

    String input = "1727372443465VeMQdq6SgQQW2bT86V";
    Mac mac = Mac.getInstance(HMAC_SHA_256);
    mac.init(secretKey);

    mac.update(input.getBytes(StandardCharsets.UTF_8));
    return bytesToHex(mac.doFinal());
  }

  public static org.knowm.xchange.instrument.Instrument getInstrument(final Exchange exchange, final CurrencyPair currencyPair, boolean isFuture) {
    try {
      final List<Instrument> instruments = exchange.getExchangeInstruments();
      org.knowm.xchange.instrument.Instrument instrument = null;
      for (org.knowm.xchange.instrument.Instrument i : instruments) {
        if (i.getBase().getSymbol().equalsIgnoreCase(currencyPair.getBase().getSymbol()) && i.getCounter().getSymbol()
            .equalsIgnoreCase(currencyPair.getCounter().getSymbol())) {
          if (isFuture && i instanceof FuturesContract && "PERP".equalsIgnoreCase(((FuturesContract) i).getPrompt())) {
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
      e.printStackTrace();
    }
    return null;
  }

  public static String sendOrder(Exchange exchange, String limitPrice, boolean isFutures) throws IOException {
    Side side = Side.BUY;
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("ETH", "USDT"), isFutures);
    final Order.OrderType xOrderType = Side.BUY == side ? Order.OrderType.BID : Order.OrderType.ASK;
    final BigDecimal quantity = new BigDecimal("0.01");
    final BigDecimal price = new BigDecimal(limitPrice);

    final LimitOrder
        order = new LimitOrder(xOrderType, quantity, instrument, "ABC124", null, price);
    final String returnValue = tradeService.placeLimitOrder(order);

    System.out.println("Return value: " + returnValue);
    return returnValue;
  }

  public static void getOrder(Exchange exchange, String reference) {
    final TradeService tradeService = exchange.getTradeService();
    final org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, new CurrencyPair("BTC", "USDT"), true);
    int retryCount = 0;
    while (retryCount < 5) {
      System.out.println("retryCount: " + retryCount);
      retryCount++;
      try {
        final OrderQueryParams orderQueryParams =
            ExternalExchangeUtil.createOrderQueryParams("MEXC", instrument, reference,
                true);
        final Collection<Order> orders = tradeService.getOrder(orderQueryParams);
        if (orders != null && !orders.isEmpty()) {
          Order summary = orders.iterator().next();

          if (summary.getAveragePrice() != null)
            System.out.println("Price: " + MbxMath.changeScale(summary.getAveragePrice().doubleValue(), 2));
          System.out.println(summary.getOriginalAmount().doubleValue());
          System.out.println(summary.getCumulativeAmount().doubleValue());
          System.out.println(summary.getStatus().name());
          System.out.println(summary.getId());
          //System.out.println(summary.g());
          if ("FILLED".equalsIgnoreCase(summary.getStatus().name())) {
            System.out.println(summary.getCumulativeAmount().doubleValue() * summary.getAveragePrice().doubleValue());
            break;
          }
        }
        Thread.sleep(250);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
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

  public static boolean isValidExchangeApiKeys(final Exchange exchange) {
    try {
      final AccountService accountService = exchange.getAccountService();
      if (accountService == null) {
        return false;
      }

      final AccountInfo accountInfo = accountService.getAccountInfo();
      if (accountInfo == null) {
        return false;
      }
      return true;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }
}
