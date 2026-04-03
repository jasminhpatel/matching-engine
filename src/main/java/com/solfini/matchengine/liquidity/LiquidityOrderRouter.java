package com.solfini.matchengine.liquidity;

import com.solfini.common.*;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.matchengine.executionexchange.*;
import com.solfini.matchengine.executionexchange.xchangewrappers.XExchange;
import com.solfini.matchengine.liquidity.direct.Binance;
import com.solfini.matchengine.liquidity.direct.Bybit;
import com.solfini.matchengine.liquidity.direct.Mexc;
import com.solfini.matchengine.message.internal.LiquidityResponse;
import com.solfini.matchengine.message.internal.LiquidityResponse.Liquidity;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.internal.WsOrderUpdate;
import com.solfini.matchengine.message.outbound.BusinessRejectMessage;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.orderbook.LiquidityOrderBook;
import com.solfini.pool.OrderObjectPool;
import com.solfini.sbe.encoder.BusinessRejectReason;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.OrdStatus;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.UserCache;
import com.solfini.util.CMCTop30Checker;
import com.solfini.util.MbxMath;

import com.solfini.util.StringUtil;
import org.agrona.concurrent.IdleStrategy;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.meta.InstrumentMetaData;
import org.knowm.xchange.exceptions.ExchangeException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.solfini.common.Constants.*;

public class LiquidityOrderRouter implements Runnable {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityOrderRouter.class);
  private static final ManyToManyConcurrentArrayQueueCustom<Message> LIQUIDITY_ORDER_QUEUE = Context.getLiquidityRouterQueue();
  private static final ConcurrentHashMap<String, Set<org.knowm.xchange.dto.Order.IOrderFlags>> EXCHANGE_ORDER_FLAGS =
      new ConcurrentHashMap<>();
  private static final InstrumentMetaData DEFAULT_META_DATA = ExternalExchangeCache.buildDEFAULT_META_DATA();
  private static final List<String> EXCHANGE_PREFERENCE = ExternalExchangeCache.buildEXCHANGE_PREFERENCE();
  private static final ConcurrentHashMap<String, XExchange> X_EXCHANGE_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, WsOrderUpdate> WS_ORDER_CACHE = new ConcurrentHashMap<>();

  public static final String BINANCE = "binance";
  public static final String BYBIT = "bybit";
  public static final String MEXC = "mexc";
  public static final String BITGET = "bitget";
  public static final long POLL_SLEEP_TIME = 20; // TODO: lower this
  private static final String PRICE_FILTER = "PRICE_FILTER";
  private static final String NOTIONAL = "NOTIONAL";
  private static final String MIN_NOTIONAL = "MIN_NOTIONAL";
  private static final String LOT_SIZE = "LOT_SIZE";

  private final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();
  private final ManyToOneConcurrentArrayQueueCustom<Message> receiverToMatcherQueue = Context.getReceiverToMatcherQueue();
  private final IdleStrategy idleStrategy;

  public LiquidityOrderRouter(final IdleStrategy idleStrategy) {
    this.idleStrategy = idleStrategy;
  }

  @Override
  public void run() {
    LOGGER.info(Constants.LOG_FMT_2, "Starting liquidity order router thread: ", Thread.currentThread().getName());
    while (true) {
      try {
        final Message message = LIQUIDITY_ORDER_QUEUE.poll();

        if (message == null) {
          Thread.sleep(POLL_SLEEP_TIME);
          continue;
        }

        if (message instanceof Order order) {
          LOGGER.info(Constants.LOG_FMT_4, "Processing liquidity trade: ", order.getClOrdId(), " symbol: ", order.getSymbol());
          // serialises all copy trades per user
          if ("TEST".equalsIgnoreCase(Context.getEnvironment())) {
            routeOrderXchange(order);
          } else {
            routeOrder(order);
          }
          //if (Context.getTestUsers().isEmpty() || Context.getTestUsers().contains(order.getAccount())) {
          // routeOrder(order);
          //} else {
          //  routeOrderXchange(order);
          //}
          if (order.isRejected()) {
            receiverToMatcherQueue.addGuaranteed(order);
          }
          LOGGER.info(Constants.LOG_FMT_6, "Processing liquidity trade completed: ", order.getClOrdId(), " symbol: ", order.getSymbol(),
              " rejected: ", order.isRejected());
        } else {
          LOGGER.warn("Invalid message: " + message.toJSON());
        }

      } catch (Exception e) {
        LOGGER.error(Constants.ERROR_LOG, e);
      }
      idleStrategy.idle();
    }
  }

  private Order routeOrder(final Order order) throws Exception {
    LOGGER.info("Processing Liquidity Order (Direct API): " + order.toJSON());
    long start = System.currentTimeMillis();

    double orderQty = MbxMath.scaleDown(order.getQuantityOrigLong(), order.getQuantityOrigScale());
    double orderPrice = MbxMath.scaleDown(order.getPrice(), order.getPriceScale());
    double orderValue = orderQty * orderPrice;
    final boolean isSmallOrder = orderValue < Context.getLiquidityDexSmallOrderValueThreshold();
    boolean futuresEnabled = false;
    final int symbolSeparatorIndex = order.getSymbol().indexOf("/");
    int startIndex = "TEST".equalsIgnoreCase(Context.getEnvironment()) ? 2 : 0;
    final String baseSymbol = order.getSymbol().substring(startIndex, symbolSeparatorIndex); // testnet has T_<symbol name> format
    // final String quoteSymbol = order.getSymbol().substring(symbolSeparatorIndex + 1);
    final Side side = order.getSide();

    final List<ExternalSymbol> availableExchanges = ExternalInstrumentCache.getAvailableExchanges(baseSymbol);

    if (availableExchanges == null || availableExchanges.isEmpty()) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, QUOTE_SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));
      order.setRejected(true);
      return order;
    }

    final Map<String, Map<Boolean, SymbolBalance>> selectedExchanges = new HashMap<>();
    for (final ExternalSymbol symbolStatus : availableExchanges) {
      if (EXCHANGE_PREFERENCE.contains(symbolStatus.getExchange().toLowerCase())) {
        try {
          if (symbolStatus.isTradable() && (USD.equalsIgnoreCase(symbolStatus.getQuote()) || USDC.equalsIgnoreCase(symbolStatus.getQuote())
              || USDT.equalsIgnoreCase(symbolStatus.getQuote()))) {

            final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(symbolStatus.getExchange(), symbolStatus.isFutures());
            if (subscription == null) {
              LOGGER.info(LOG_FMT_4, "Subscription not found: exchange: ", symbolStatus.getExchange(), " futures: ",
                  symbolStatus.isFutures());
              continue;
            }

            if (side == Side.BUY) {
              final double quoteBalance = subscription.getBalance(symbolStatus.getQuote());
              if (symbolStatus.isFutures()) {
                final double baseBalance = subscription.getPosition(symbolStatus.getBase() + symbolStatus.getQuote());
                final double multiplier = symbolStatus.getMultiplierContract() > 0 ? symbolStatus.getMultiplierContract() : 1D;
                // has short positions, buy back
                LOGGER.info(LOG_FMT_12, "Check for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " qty: ",
                    (orderQty / multiplier), " available: ", quoteBalance, " ", symbolStatus.getQuote(), " short: ", -baseBalance);
                if (-baseBalance >= (orderQty / multiplier)) {
                  LOGGER.info(LOG_FMT_12, "Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " qty: ",
                      (orderQty / multiplier), " available: ", quoteBalance, " ", symbolStatus.getQuote(), " short: ", -baseBalance);
                  selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                      .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, baseBalance));
                  continue;
                }
              }

              if (quoteBalance > orderValue) {
                LOGGER.info(LOG_FMT_10, "Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderValue: ",
                    orderValue, " available: ", quoteBalance, " ", symbolStatus.getQuote()/* , " fullBalance: ", balance.toJson() */);
                selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                    .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, quoteBalance));
              } else {
                LOGGER.info(LOG_FMT_10, "Not Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderValue: ",
                    orderValue, " available: ", quoteBalance, " ", symbolStatus.getQuote()/* , " fullBalance: ", balance.toJson() */);
              }
            } else {
              final double quoteBalance = subscription.getBalance(symbolStatus.getQuote());
              final double baseBalance = subscription.getBalance(symbolStatus.getBase());
              final double multiplier = symbolStatus.getMultiplierContract() > 0 ? symbolStatus.getMultiplierContract() : 1D;

              final double minimumRequiredBalance = (orderQty * (1 - Context.getExternalExchangeSellQtyTolerancePercentage()));
              if (baseBalance >= minimumRequiredBalance
                  | (symbolStatus.isFutures() && orderValue < Context.getExternalExchangeMaxLeverage() * quoteBalance)) {
                LOGGER.info(LOG_FMT_12, "Selected for SELL: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderQty: ",
                    orderQty, " available: ", baseBalance, " ", baseSymbol, " minimumRequiredBalance: ",
                    minimumRequiredBalance/* , " fullBalance: ", balance.toJson() */);
                selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                    .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, baseBalance));
              } else {
                LOGGER.info(LOG_FMT_10, "Not Selected for SELL: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderQty: ",
                    orderQty, " available: ", baseBalance, " ", baseSymbol/* , " fullBalance: ", balance.toJson() */);
              }
              // use the best quote symbol of the exchange
            }
          }
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }
      }
    }

    if (selectedExchanges.isEmpty()) {
      LOGGER.info(Constants.LOG_FMT_6, "Symbol not found: symbol: ", order.getSymbol(), " order: ", order.getClOrdId(), " futuresEnabled: ",
          futuresEnabled);
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, QUOTE_SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));

      order.setRejected(true);
      return order;
    }

    final SymbolBalance selectedExchangeSymbol = selectBestExchangeNew(selectedExchanges, order, orderQty, orderValue);
    if (selectedExchangeSymbol == null) {
      LOGGER.info(Constants.LOG_FMT_6, "Best quote exchange/symbol is empty. symbol: ", order.getSymbol(), " order: ", order.getClOrdId(),
          " futuresEnabled: ", futuresEnabled);
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, QUOTE_SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));

      order.setRejected(true);
      return order;
    }
    long end = System.currentTimeMillis();
    LOGGER.info(LOG_FMT_4, "External order - selectBestExchange time ", (end - start), " order: ", order.getClOrdId());
    start = end;

    futuresEnabled = selectedExchangeSymbol.getSymbolData().isFutures();

    LOGGER.info(LOG_FMT_4, " order: ", order.getClOrdId(), " Selected exchange: ", selectedExchangeSymbol.toString());
    //final String exchange = selectedExchangeSymbol.getSymbolData().getExchange();
    final String bestQuoteSymbol = selectedExchangeSymbol.getSymbolData().getQuote().toUpperCase();
    final Instrument bestQuoteInstrument = InstrumentCache.getBySymbol(bestQuoteSymbol);
    final double fxRate = 1d / bestQuoteInstrument.getIndexFeedUsdMark();
    LOGGER.info(LOG_FMT_8, " order: ", order.getClOrdId(), "quote currency: ", bestQuoteInstrument.getSymbol(), " rate: ", fxRate, " IndexFeedUsdMark: ",
        bestQuoteInstrument.getIndexFeedUsdMark());
    final ExchangeSubscription subscription = selectedExchangeSymbol.getSubscription();
    // store external symbol reference in selectId field.
    //order.setSelectId(selectedExchangeSymbol.getSymbolData().getId());

    final int priceScale = selectedExchangeSymbol.getSymbolData().getPriceScale();
    final int qtyScale = selectedExchangeSymbol.getSymbolData().getQtyScale();
    final double contractMultiplier =
        selectedExchangeSymbol.getSymbolData().getMultiplierContract() > 0 ? selectedExchangeSymbol.getSymbolData().getMultiplierContract()
            : 1D;
    double xPrice = 0;
    double xQuantity = 0;
    long qtyUnits = 0;
    long priceUnits = 0;
    final double priceMul = Math.pow(10, priceScale);
    final double qtyMul = Math.pow(10, qtyScale);
    double rawQty = MbxMath.scaleDown(order.getQuantityOrigLong(), order.getQuantityOrigScale());
    double safetyFactorBps = CMCTop30Checker.getBPS(baseSymbol);
    double totalQtyToCoverFee = rawQty;
    if (side == Side.BUY) {
      long marketPrice = order.getPrice();
      if (futuresEnabled) {
        final double perpsPrice = getPerpPrice(selectedExchangeSymbol.getSymbolData(), order);
        LOGGER.info(LOG_FMT_6, "Order: ", order.getClOrdId(), " perpsPrice: ", perpsPrice, " orderPrice: ", orderPrice);
        if (perpsPrice < orderPrice) {
          marketPrice = MbxMath.changeScale(perpsPrice, order.getPriceScale());
        }
      }
      xPrice = MbxMath.scaleDown(
          (long) (marketPrice * fxRate * (1 + safetyFactorBps - Context.getProfitMarginBpsForLiquidity()) / (1 + safetyFactorBps)),
          order.getPriceScale());
      if (!futuresEnabled) {
        // increase qty to cover the fee.
        totalQtyToCoverFee = rawQty * (1 + Context.getExternalExchangeTransactionFee());
        qtyUnits = Math.round(totalQtyToCoverFee * qtyMul);
        xQuantity = qtyUnits / qtyMul;
      } else {
        qtyUnits = (long) ((rawQty / contractMultiplier) * qtyMul);
        xQuantity = qtyUnits / qtyMul;
      }
      priceUnits = (long) Math.floor(xPrice * contractMultiplier * priceMul);
    } else {
      long marketPrice = order.getPrice();
      if (futuresEnabled) {
        final double perpsPrice = getPerpPrice(selectedExchangeSymbol.getSymbolData(), order);
        LOGGER.info(LOG_FMT_6, "Order: ", order.getClOrdId(), " perpsPrice: ", perpsPrice, " orderPrice: ", orderPrice);
        if (perpsPrice > orderPrice) {
          marketPrice = MbxMath.changeScale(perpsPrice, order.getPriceScale());
        }
      }
      xPrice = MbxMath.scaleDown(
          (long) (marketPrice * fxRate * (1 - (safetyFactorBps - Context.getProfitMarginBpsForLiquidity())) / (1 - safetyFactorBps)),
          order.getPriceScale());
      qtyUnits = (long) ((rawQty / contractMultiplier) * qtyMul);
      priceUnits = (long) Math.ceil(xPrice * contractMultiplier * priceMul);

      xQuantity = qtyUnits / qtyMul;
      // even if exchange have 1% lower continue.
      if (selectedExchangeSymbol.getAvailableBalance() < xQuantity) {
        LOGGER.info(LOG_FMT_4, "Exchange doesn't have the full amount.  availableBalance:", selectedExchangeSymbol.getAvailableBalance(),
            " xQuantity: ", xQuantity);
      }
      if (selectedExchangeSymbol.getAvailableBalance() > 0) {
        xQuantity = Math.min(xQuantity, selectedExchangeSymbol.getAvailableBalance());
      }
    }
    xPrice = priceUnits / priceMul;

    final String sPrice = StringUtil.toNumericString(xPrice);
    final String sQuantity = StringUtil.toNumericString(xQuantity);
    LOGGER.info(LOG_FMT_8, "xPrice ", xPrice, " sPrice: ", sPrice, " xQuantity ", xQuantity, " sQuantity: ", sQuantity);

    final Order externalOrder = OrderObjectPool.get();
    externalOrder.setUser(order.getUser());
    externalOrder.setClOrdId(order.getClOrdId());
    externalOrder.setSide(order.getSide());
    externalOrder.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
    externalOrder.setTimeInForce(TimeInForce.FILL_OR_KILL);
    externalOrder.setPrice(MbxMath.changeScaleWithRounding(xPrice, priceScale), (short) priceScale);
    externalOrder.setQty(MbxMath.changeScaleWithRounding(xQuantity, qtyScale), (short) qtyScale);

    LOGGER.info(LOG_FMT_4, "Update pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
    final boolean reserved = subscription.reserve(orderValue, bestQuoteInstrument);
    LOGGER.info(LOG_FMT_6, "Pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue, " reserved: ",
        reserved);

    final ExecutionReportMessage executionReport =
        subscription.getClient().sendOrder(externalOrder, futuresEnabled, bestQuoteSymbol, baseSymbol, priceScale, qtyScale, fxRate);

    if (executionReport.getOrdStatus() == OrdStatus.FILLED) {
      order.setExecuted(true);
      // don't override the qty field of the original order
      // order.setQty(0, 0);
      // These values are expected from Cache
      order.setQuantityOrigLong(executionReport.getCumQty());
      order.setQuantityOrigScale(externalOrder.getQtyScale());
      order.setPrice2(executionReport.getAvgPx(), externalOrder.getPriceScale());
      order.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
      order.setFeeAccumulatedQuantity(executionReport.getFeeAccumulatedQuantity());
      order.setAssetId(executionReport.getFeePositionId());//
      receiverToMatcherQueue.addGuaranteed(order);
      LOGGER.info(Constants.LOG_FMT_20, "Liquidity trade successful. clOrdId: ", order.getClOrdId(), " symbol: ", baseSymbol.toUpperCase(),
          "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ", order.getSide().name(), " quantity: ",
          executionReport.getOrderQty(), " averagePrice:", order.getPrice2(), " xPrice: ", xPrice, " xQuantity: ", xQuantity, " result: ",
          "success");

      end = System.currentTimeMillis();
      LOGGER.info(LOG_FMT_2, "External order liquidityOrder done time ", (end - start));

      LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
      subscription.release(orderValue, bestQuoteInstrument);
      LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);

      return order;
    } else {
      if (isSmallOrder) {
        //accept small orders even if the exchange rejects it.
        LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected from exchange but processed internally. clOrdId: ", order.getClOrdId(),
            " symbol: ", baseSymbol.toUpperCase(),
            "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ",
            order.getSide().name(), " quantity: ", 0, " price: ",
            xPrice, " quantity: ", xQuantity, " result: ", "failed from exchange, processed internally");
        order.setExecuted(true);
        // don't override the qty field of the original order
        // order.setQty(0, 0);
        // These values are expected from Cache
        order.setQuantityOrigLong(0L);
        order.setQuantityOrigScale((short) 0);
        order.setPrice2(0L, (short) 0);
        order.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
        order.setFeeAccumulatedQuantity(0);
        order.setAssetId(0);

        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);

        receiverToMatcherQueue.addGuaranteed(order);

      } else {
        LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected. clOrdId: ", order.getClOrdId(),
            " symbol: ", baseSymbol.toUpperCase(),
            "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ",
            order.getSide().name(), " quantity: ", 0, " price: ",
            xPrice, " quantity: ", xQuantity, " result: ", "failed");
        matcherToPublisherQueue.addGuaranteed(
            BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(),
                MsgType.ORDER_SINGLE,
                Long.toString(order.getOrderId()),
                BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, ERROR_PLEASE_TRY_AGAIN_LATER,
                order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                order.getSecurityId(), order.getClOrdId(), order.getAccount()));
        order.setRejected(true);
      }

      LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
      subscription.release(orderValue, bestQuoteInstrument);
      LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);

      return order;
    }
  }

  private Order routeOrderXchange(final Order order) {
    LOGGER.info("Processing Liquidity Order: " + order.toJSON());
    long start = System.currentTimeMillis();
    double orderQty = MbxMath.scaleDown(order.getQty(), order.getQtyScale());
    double orderPrice = MbxMath.scaleDown(order.getPrice(), order.getPriceScale());
    double orderValue = orderQty * orderPrice;
    final boolean isSmallOrder = orderValue < Context.getLiquidityDexSmallOrderValueThreshold();
    boolean futuresEnabled = false;
    final int symbolSeparatorIndex = order.getSymbol().indexOf("/");
    int startIndex = "TEST".equalsIgnoreCase(Context.getEnvironment()) ? 2 : 0;
    final String baseSymbol = order.getSymbol().substring(startIndex, symbolSeparatorIndex); // testnet has T_<symbol name> format
    // final String quoteSymbol = order.getSymbol().substring(symbolSeparatorIndex + 1);
    final Side side = order.getSide();
    final InstrumentPair instrumentPair = InstrumentCache.getPair(order.getSecurityId());

    final List<ExternalSymbol> availableExchanges = ExternalInstrumentCache.getAvailableExchanges(baseSymbol);

    if (availableExchanges == null || availableExchanges.isEmpty()) {
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, QUOTE_SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));
      order.setRejected(true);
      return order;
    }
    final Map<String, Map<Boolean, SymbolBalance>> selectedExchanges = new HashMap<>();
    for (ExternalSymbol symbolStatus : availableExchanges) {
      if (EXCHANGE_PREFERENCE.contains(symbolStatus.getExchange().toLowerCase())) {
        try {
          if (symbolStatus.isTradable() && (USD.equalsIgnoreCase(symbolStatus.getQuote()) || USDC.equalsIgnoreCase(symbolStatus.getQuote())
              || USDT.equalsIgnoreCase(symbolStatus.getQuote()))) {

            final String key = (symbolStatus.getExchange() + "_" + symbolStatus.isFutures()).toLowerCase();
            final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(symbolStatus.getExchange(), symbolStatus.isFutures());
            if (subscription == null) {
              LOGGER.info(LOG_FMT_4, "Subscription not found: exchange: ", symbolStatus.getExchange(), " futures: ",
                  symbolStatus.isFutures());
              continue;
            }
            XExchange xExchange = X_EXCHANGE_CACHE.get(key);
            if (xExchange == null) {
              LOGGER.warn(LOG_FMT_4, "Liquidity Order, creating exchange instance. exchange: ", symbolStatus.getExchange(), " futures: ",
                  symbolStatus.isFutures());
              xExchange = ExternalExchangeUtil.createXExchange(subscription);
              X_EXCHANGE_CACHE.put(key, xExchange);
            }
            XExchange.Balance balance = null;
            if (side == Side.BUY) {
              balance = ExternalExchangeHandler.getStableCoinBalance(subscription, xExchange);
              final double availableBalance = balance.getBalance(symbolStatus.getQuote());

              if (symbolStatus.isFutures()) {
                final XExchange.Balance baseCoinBalance =
                    ExternalExchangeHandler.getBalance(subscription, xExchange, symbolStatus.getBase());
                // has short positions, buy back
                if (symbolStatus.getMultiplierContract() > 0) {
                  if (-baseCoinBalance.getCoinBalance() >= (orderQty / symbolStatus.getMultiplierContract())) {
                    LOGGER.info(LOG_FMT_12, "Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderValue: ",
                        (orderQty / symbolStatus.getMultiplierContract()), " available: ", availableBalance, " ", symbolStatus.getQuote(),
                        " short: ", -baseCoinBalance.getCoinBalance());
                    selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                        .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, baseCoinBalance.getCoinBalance()));
                    continue;
                  }
                } else {
                  // has short positions, buy back
                  if (-baseCoinBalance.getCoinBalance() >= orderQty) {
                    LOGGER.info(LOG_FMT_12, "Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderValue: ",
                        orderQty, " available: ", availableBalance, " ", symbolStatus.getQuote(), " short: ",
                        -baseCoinBalance.getCoinBalance());
                    selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                        .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, baseCoinBalance.getCoinBalance()));
                    continue;
                  }
                }
              }

              if (availableBalance > orderValue) {
                LOGGER.info(LOG_FMT_10, "Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderValue: ",
                    orderValue, " available: ", availableBalance, " ", symbolStatus.getQuote()/* , " fullBalance: ", balance.toJson() */);
                selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                    .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, availableBalance));
              } else {
                LOGGER.info(LOG_FMT_10, "Not Selected for Buy: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderValue: ",
                    orderValue, " available: ", availableBalance, " ", symbolStatus.getQuote()/* , " fullBalance: ", balance.toJson() */);
              }
            } else {
              balance = ExternalExchangeHandler.getBalance(subscription, xExchange, baseSymbol);
              final XExchange.Balance quoteBalance = ExternalExchangeHandler.getStableCoinBalance(subscription, xExchange);
              final double availableBalance = balance.getCoinBalance();
              final double minimumRequiredBalance = (orderQty * (1 - Context.getExternalExchangeSellQtyTolerancePercentage()));
              if (availableBalance >= minimumRequiredBalance | (symbolStatus.isFutures()
                  && orderValue < Context.getExternalExchangeMaxLeverage() * quoteBalance.getBalance(symbolStatus.getQuote()))) {
                LOGGER.info(LOG_FMT_12, "Selected for SELL: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderQty: ",
                    orderQty, " available: ", availableBalance, " ", baseSymbol, " minimumRequiredBalance: ",
                    minimumRequiredBalance/* , " fullBalance: ", balance.toJson() */);
                selectedExchanges.computeIfAbsent(symbolStatus.getExchange().toLowerCase(), v -> new HashMap<>())
                    .put(symbolStatus.isFutures(), new SymbolBalance(subscription, symbolStatus, availableBalance));
              } else {
                LOGGER.info(LOG_FMT_10, "Not Selected for SELL: ", order.getClOrdId(), " ", symbolStatus.toShortString(), " orderQty: ",
                    orderQty, " available: ", availableBalance, " ", baseSymbol/* , " fullBalance: ", balance.toJson() */);
              }
              // use the best quote symbol of the exchange
            }
          }
        } catch (Exception e) {
          LOGGER.error(ERROR_LOG, e);
        }
      }
    }

    if (selectedExchanges.isEmpty()) {
      LOGGER.info(Constants.LOG_FMT_6, "Symbol not found: symbol: ", order.getSymbol(), " order: ", order.getClOrdId(), " futuresEnabled: ",
          futuresEnabled);
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, QUOTE_SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));

      order.setRejected(true);
      return order;
    }

    final SymbolBalance selectedExchangeSymbol = selectBestExchange(selectedExchanges, order, orderQty, orderValue);
    if (selectedExchangeSymbol == null) {
      LOGGER.info(Constants.LOG_FMT_6, "Best quote exchange/symbol is empty. symbol: ", order.getSymbol(), " order: ", order.getClOrdId(),
          " futuresEnabled: ", futuresEnabled);
      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, QUOTE_SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));

      order.setRejected(true);
      return order;
    }
    long end = System.currentTimeMillis();
    LOGGER.info(LOG_FMT_2, "External order - selectBestExchange time ", (end - start));
    start = end;

    futuresEnabled = selectedExchangeSymbol.getSymbolData().isFutures();
    LOGGER.info(LOG_FMT_2, "Selected exchange: ", selectedExchangeSymbol.toString());
    final String exchange = selectedExchangeSymbol.getSymbolData().getExchange();
    final String bestQuoteSymbol = selectedExchangeSymbol.getSymbolData().getQuote().toUpperCase();
    final Instrument bestQuoteInstrument = InstrumentCache.getBySymbol(bestQuoteSymbol);
    final double fxRate = 1d / bestQuoteInstrument.getIndexFeedUsdMark();
    LOGGER.info(LOG_FMT_6, "quote currency: ", bestQuoteInstrument.getSymbol(), " rate: ", fxRate, " IndexFeedUsdMark: ",
        bestQuoteInstrument.getIndexFeedUsdMark());
    final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, futuresEnabled);
    // store external symbol reference in selectId field.
    //order.setSelectId(selectedExchangeSymbol.getSymbolData().getId());

    /*
     * if (subscription.isFuturesEnabled()) { if (Side.SELL == order.getSide()) { final LastBalance lastPositions =
     * subscription.getPositionCache().computeIfAbsent(baseSymbol.toUpperCase(), v -> new LastBalance(baseSymbol.toUpperCase(), 0)); double
     * rawQty = MbxMath.scaleDown(order.getQty(), order.getQtyScale()); final double reservedQty = lastPositions.getReservedAmount().get();
     *
     * } }
     */
    //double imbalance = LiquiditySubscriptionCache.processImbalance(instrumentPair);


    if (BYBIT.equalsIgnoreCase(exchange))
      return Bybit.sendOrderWithDirectHttp(side, futuresEnabled, order, bestQuoteSymbol, baseSymbol, subscription, exchange,
          selectedExchangeSymbol, fxRate, orderValue, bestQuoteInstrument);
    else if (!"TEST".equalsIgnoreCase(Context.getEnvironment()) && BINANCE.equalsIgnoreCase(exchange))
      return Binance.sendOrderWithDirectHttp(side, futuresEnabled, order, bestQuoteSymbol, baseSymbol, subscription, exchange,
          selectedExchangeSymbol, fxRate, orderValue, bestQuoteInstrument);
    else if (BITGET.equalsIgnoreCase(exchange))
      return Bybit.sendOrderWithDirectHttp(side, futuresEnabled, order, bestQuoteSymbol, baseSymbol, subscription, exchange,
          selectedExchangeSymbol, fxRate, orderValue, bestQuoteInstrument);
    else if (MEXC.equalsIgnoreCase(exchange))
      return Mexc.sendOrderWithDirectHttp(side, futuresEnabled, order, bestQuoteSymbol, baseSymbol, subscription, exchange,
          selectedExchangeSymbol, fxRate, orderValue, bestQuoteInstrument);

    start = System.currentTimeMillis();
    // LOGGER.info(LOG_FMT_2, "Subscription: ", subscription);
    final XExchange xExchange = ExternalExchangeUtil.createXExchange(subscription);

    if (xExchange == null) {
      LOGGER.info(Constants.LOG_FMT_6, "Order rejected. clOrdId: ", order.getClOrdId(), " invalid exchange: ", exchange,
          " futuresEnabled: ", futuresEnabled);

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.MARKET_IS_CLOSED, MARKET_IS_CLOSED, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));
      order.setRejected(true);
      return order;
    }

    final CurrencyPair currencyPair = ExternalCurrencyPairCache.get(baseSymbol, bestQuoteSymbol);
    LOGGER.info(LOG_FMT_8, " Exchange: ", exchange, " baseSymbol: ", baseSymbol, " bestQuoteSymbol: ", bestQuoteSymbol, " futuresEnabled: ",
        futuresEnabled);

    org.knowm.xchange.instrument.Instrument instrument = getInstrument(exchange, xExchange, currencyPair, futuresEnabled);
    if (instrument == null) {
      LOGGER.info(Constants.LOG_FMT_8, "Order rejected. clOrdId: ", order.getClOrdId(), " invalid instrument: ", baseSymbol, " ",
          bestQuoteSymbol, " futuresEnabled: ", futuresEnabled);

      matcherToPublisherQueue.addGuaranteed(BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(), MsgType.ORDER_SINGLE,
          Long.toString(order.getOrderId()), BusinessRejectReason.SYMBOL_NOT_FOUND, SYMBOL_NOT_FOUND, order.getOrderId(),
          order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getClOrdId(), order.getAccount()));
      order.setRejected(true);
      return order;
    }
    double safetyFactorBps = Context.getSafetyFactorBpsForLiquidity();
    if (baseSymbol != null) {
      safetyFactorBps = CMCTop30Checker.getBPS(baseSymbol);
    }
    BigDecimal xPrice = null;
    if (side == Side.BUY) {
      xPrice = BigDecimal.valueOf(MbxMath.scaleDown(
          (long) (order.getPrice() * fxRate * (1 + safetyFactorBps - Context.getProfitMarginBpsForLiquidity()) / (1 + safetyFactorBps)),
          order.getPriceScale()));
      xPrice = xPrice.setScale(selectedExchangeSymbol.getSymbolData().getPriceScale(), RoundingMode.HALF_UP);
    } else {
      xPrice = BigDecimal.valueOf(MbxMath.scaleDown(
          (long) (order.getPrice() * fxRate * (1 - (safetyFactorBps - Context.getProfitMarginBpsForLiquidity())) / (1 - safetyFactorBps)),
          order.getPriceScale()));
      xPrice = xPrice.setScale(selectedExchangeSymbol.getSymbolData().getPriceScale(), RoundingMode.HALF_DOWN);
    }

    BigDecimal xQuantity = BigDecimal.valueOf(MbxMath.scaleDown(order.getQty(), order.getQtyScale()));
    xQuantity = xQuantity.setScale(selectedExchangeSymbol.getSymbolData().getQtyScale(),
        (side == Side.BUY) ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN);

    //LOGGER.info("###Handle imbalance qty: " + imbalance + " orderQty: " + xQuantity + " qty with imbalance: " + (xQuantity.doubleValue() + imbalance));

    final LiquidityOrder liquidityOrder = new LiquidityOrder();
    liquidityOrder.setUserId(UserCache.getMarketMakerUser().getId());
    liquidityOrder.setSecurityId(order.getSecurityId());
    // liquidityOrder.setSubscriptionId(subscription.getId());
    liquidityOrder.setBaseSymbol(baseSymbol);
    liquidityOrder.setQuotedSymbol(bestQuoteSymbol);
    // liquidityOrder.setOrigClOrdId();
    liquidityOrder.setClOrdId(order.getClOrdId());
    liquidityOrder.setExchange(exchange);
    liquidityOrder.setSide(side);
    liquidityOrder.setOrdType(order.getOrdType());
    liquidityOrder.setTimeInForce(order.getTimeInForce());

    liquidityOrder.setxExchange(xExchange);
    liquidityOrder.setFuturesEnabled(futuresEnabled);
    liquidityOrder.setInstrument(instrument);
    liquidityOrder.setxPrice(xPrice);
    liquidityOrder.setxQuantity(xQuantity);
    liquidityOrder.setSubscription(subscription);

    try {
      /*
       * InstrumentMetaData instrumentMetaData = getInstrumentMetadata(xExchange, instrument); if (instrumentMetaData.getMinimumAmount() !=
       * null && instrumentMetaData.getMinimumAmount().compareTo(xQuantity) > 0) { LOGGER.info(Constants.LOG_FMT_2,
       * "Liquidity trade rejected. clOrdId: ", liquidityOrder.getClOrdId(), " symbol: ", baseSymbol.toUpperCase(), "/", bestQuoteSymbol,
       * " futuresEnabled: ", futuresEnabled, " side: ", liquidityOrder.getSide().name(), " quantity: ",
       * liquidityOrder.getCumulativeAmount(), " price: ", xPrice, " quantity: ", xQuantity, " result: ", INSUFFICIENT_ORDER_QTY);
       * matcherToPublisherQueue.addGuaranteed( BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE,
       * Long.toString(order.getOrderId()), BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, INSUFFICIENT_ORDER_QTY,
       * order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
       *
       * return; }
       *
       * if (instrumentMetaData.getMaximumAmount() != null && instrumentMetaData.getMaximumAmount().compareTo(xQuantity) < 0) {
       * LOGGER.info(Constants.LOG_FMT_2, "Liquidity trade rejected. clOrdId: ", liquidityOrder.getClOrdId(), " symbol: ",
       * baseSymbol.toUpperCase(), "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ", liquidityOrder.getSide().name(),
       * " quantity: ", liquidityOrder.getCumulativeAmount(), " price: ", xPrice, " quantity: ", xQuantity, " result: ",
       * EXCEEDS_THE_MAX_QUANTITY); matcherToPublisherQueue.addGuaranteed(
       * BusinessRejectMessage.createBusinessReject(order.getSenderCompId(), MsgType.ORDER_SINGLE, Long.toString(order.getOrderId()),
       * BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, EXCEEDS_THE_MAX_QUANTITY, order.getOrderId(), order.getSourceSeqNum(),
       * order.getSecondaryOrderId(), order.getSecurityId(), order.getSubmitterId()));
       *
       * return; }
       */

      end = System.currentTimeMillis();
      LOGGER.info(LOG_FMT_2, "External order create liquidityOrder time ", (end - start));
      start = end;

      LOGGER.info(LOG_FMT_4, "Update pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
      final boolean reserved = subscription.reserve(orderValue, bestQuoteInstrument);
      LOGGER.info(LOG_FMT_6, "Pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue, " reserved: ",
          reserved);

      if ("TEST".equalsIgnoreCase(Context.getEnvironment())) {
        // binance testnet api has issues. set success to all test orders.
        if (isSmallOrder) {
          //accept small orders even if the exchange rejects it.
          LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected from exchange but processed internally. clOrdId: ", order.getClOrdId(),
              " symbol: ", baseSymbol.toUpperCase(),
              "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ",
              order.getSide().name(), " quantity: ", 0, " price: ",
              xPrice, " quantity: ", xQuantity, " result: ", "failed from exchange, processed internally");
          order.setExecuted(true);
          // don't override the qty field of the original order
          // order.setQty(0, 0);
          // These values are expected from Cache
          order.setQuantityOrigLong(0L);
          order.setQuantityOrigScale((short) 0);
          order.setPrice2(0L, (short) 0);
          order.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
          order.setFeeAccumulatedQuantity(0);
          order.setAssetId(0);

          receiverToMatcherQueue.addGuaranteed(order);

          return order;

        } else {
          liquidityOrder.setResult(SUCCESS);
          liquidityOrder.setCumulativeAmount(xQuantity.doubleValue());
          liquidityOrder.setAveragePrice(xPrice.doubleValue());
          liquidityOrder.setFee(xPrice.doubleValue() * xQuantity.doubleValue() * 0.001D);
        }
      } else {
        xExchange.placeOrder(liquidityOrder, EXCHANGE_ORDER_FLAGS.get(exchange.toLowerCase()));
      }

      end = System.currentTimeMillis();
      LOGGER.info(LOG_FMT_2, "External order placeOrder time ", (end - start));
      start = end;

      LOGGER.info(LOG_FMT_3, "Liquidity order: ", liquidityOrder.toString(), " placed.");

      LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade sent. clOrdId: ", liquidityOrder.getClOrdId(), " symbol: ",
          baseSymbol.toUpperCase(), "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ", liquidityOrder.getSide().name(),
          " quantity: ", xQuantity, " price: ", xPrice, " quantity: ", xQuantity, " result: ", liquidityOrder.getResult());
      if (SUCCESS.equals(liquidityOrder.getResult())) {
        order.setExecuted(true);
        // don't override the qty
        order.setQuantityOrigLong(MbxMath.changeScale(liquidityOrder.getCumulativeAmount(), order.getQtyScale()));
        order.setQuantityOrigScale(order.getQtyScale());
        order.setPrice2(MbxMath.changeScale(liquidityOrder.getAveragePrice(), order.getPriceScale()), order.getPriceScale());
        order.setSymbol(instrument.toString());
        final Instrument fee = InstrumentCache.getBySymbol(bestQuoteSymbol);
        order.setFeeAccumulatedQuantity(MbxMath.changeScale(liquidityOrder.getFee(), fee.getQuantityScale()));
        order.setAssetId(fee.getId());
        // if (futuresEnabled) {
        // perpPositions.add(order.getSide() == Side.BUY ? liquidityOrder.getCumulativeAmount() : -liquidityOrder.getCumulativeAmount());
        // } else {
        // spotPositions.add(order.getSide() == Side.BUY ? liquidityOrder.getCumulativeAmount() : -liquidityOrder.getCumulativeAmount());
        // }
        // sb.append("\nAfter order: spotPositions: ").append(spotPositions.doubleValue()).append(", perpPositions:
        // ").append(perpPositions.doubleValue());
        // send back to matching thread
        // LOGGER.info(sb.toString());
        receiverToMatcherQueue.addGuaranteed(order);
        LOGGER.info(Constants.LOG_FMT_20, "Liquidity trade successful. clOrdId: ", liquidityOrder.getClOrdId(), " symbol: ",
            baseSymbol.toUpperCase(), "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ", liquidityOrder.getSide().name(),
            " quantity: ", liquidityOrder.getCumulativeAmount(), " averagePrice:", liquidityOrder.getAveragePrice(), " xPrice: ", xPrice,
            " xQuantity: ", xQuantity, " result: ", liquidityOrder.getResult());

        end = System.currentTimeMillis();
        LOGGER.info(LOG_FMT_2, "External order liquidityOrder done time ", (end - start));

        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);

        return order;
      } else {
        if (isSmallOrder) {
          //accept small orders even if the exchange rejects it.
          LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected from exchange but processed internally. clOrdId: ", order.getClOrdId(),
              " symbol: ", baseSymbol.toUpperCase(),
              "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ",
              order.getSide().name(), " quantity: ", 0, " price: ",
              xPrice, " quantity: ", xQuantity, " result: ", "failed from exchange, processed internally");
          order.setExecuted(true);
          // don't override the qty field of the original order
          // order.setQty(0, 0);
          // These values are expected from Cache
          order.setQuantityOrigLong(0L);
          order.setQuantityOrigScale((short) 0);
          order.setPrice2(0L, (short) 0);
          order.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
          order.setFeeAccumulatedQuantity(0);
          order.setAssetId(0);

          receiverToMatcherQueue.addGuaranteed(order);

        } else {
          LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected. clOrdId: ",
              liquidityOrder.getClOrdId(), " symbol: ",
              baseSymbol.toUpperCase(), "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled,
              " side: ", liquidityOrder.getSide().name(),
              " quantity: ", liquidityOrder.getCumulativeAmount(), " price: ", xPrice,
              " quantity: ", xQuantity, " result: ",
              liquidityOrder.getResult());
          matcherToPublisherQueue.addGuaranteed(
              BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(),
                  MsgType.ORDER_SINGLE,
                  Long.toString(order.getOrderId()),
                  BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                  ERROR_PLEASE_TRY_AGAIN_LATER,
                  order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                  order.getSecurityId(), order.getClOrdId(), order.getAccount()));
          order.setRejected(true);
        }

        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(), " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ", order.getClOrdId(), " value: ", orderValue);

        return order;
      }

    } catch (final ExchangeException ex) {
      if (isSmallOrder) {
        //accept small orders even if the exchange rejects it.
        LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected from exchange but processed internally. clOrdId: ", order.getClOrdId(),
            " symbol: ", baseSymbol.toUpperCase(),
            "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ",
            order.getSide().name(), " quantity: ", 0, " price: ",
            xPrice, " quantity: ", xQuantity, " result: ", "failed from exchange, processed internally");
        order.setExecuted(true);
        // don't override the qty field of the original order
        // order.setQty(0, 0);
        // These values are expected from Cache
        order.setQuantityOrigLong(0L);
        order.setQuantityOrigScale((short) 0);
        order.setPrice2(0L, (short) 0);
        order.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
        order.setFeeAccumulatedQuantity(0);
        order.setAssetId(0);

        receiverToMatcherQueue.addGuaranteed(order);

      } else {
        LOGGER.error("Exchange Exception, Liquidity trade failed. clOrdId: " + order.getClOrdId(),
            ex);
        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(),
            " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ",
            order.getClOrdId(), " value: ", orderValue);
        if (ex.getMessage().equals(PRICE_FILTER) | ex.getMessage().equals(LOT_SIZE)
            | ex.getMessage().equals(MIN_NOTIONAL)
            | ex.getMessage().equals(NOTIONAL)) {
          matcherToPublisherQueue.addGuaranteed(
              BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(),
                  MsgType.ORDER_SINGLE,
                  Long.toString(order.getOrderId()),
                  BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                  ERROR_PLEASE_TRY_AGAIN_LATER,
                  order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                  order.getSecurityId(), order.getClOrdId(), order.getAccount()));
          order.setRejected(true);
        } else { // override the error message with a custom message
          matcherToPublisherQueue.addGuaranteed(
              BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(),
                  MsgType.ORDER_SINGLE,
                  Long.toString(order.getOrderId()),
                  BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE,
                  ERROR_PLEASE_TRY_AGAIN_LATER,
                  order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                  order.getSecurityId(), order.getClOrdId(), order.getAccount()));
          order.setRejected(true);
        }
      }
      return order;
    } catch (final Exception e) {
      if (isSmallOrder) {
        //accept small orders even if the exchange rejects it.
        LOGGER.info(Constants.LOG_FMT_18, "Liquidity trade rejected from exchange but processed internally. clOrdId: ", order.getClOrdId(),
            " symbol: ", baseSymbol.toUpperCase(),
            "/", bestQuoteSymbol, " futuresEnabled: ", futuresEnabled, " side: ",
            order.getSide().name(), " quantity: ", 0, " price: ",
            xPrice, " quantity: ", xQuantity, " result: ", "failed from exchange, processed internally");
        order.setExecuted(true);
        // don't override the qty field of the original order
        // order.setQty(0, 0);
        // These values are expected from Cache
        order.setQuantityOrigLong(0L);
        order.setQuantityOrigScale((short) 0);
        order.setPrice2(0L, (short) 0);
        order.setSymbol(selectedExchangeSymbol.getSymbolData().getSymbol());
        order.setFeeAccumulatedQuantity(0);
        order.setAssetId(0);

        receiverToMatcherQueue.addGuaranteed(order);

      } else {
        LOGGER.info(LOG_FMT_4, "Release pending order value for clOrdId: ", order.getClOrdId(),
            " value: ", orderValue);
        subscription.release(orderValue, bestQuoteInstrument);
        LOGGER.info(LOG_FMT_4, "Release pending order value updated for clOrdId: ",
            order.getClOrdId(), " value: ", orderValue);
        LOGGER.error("Error, Liquidity trade failed. clOrdId: " + order.getClOrdId(), e);
        matcherToPublisherQueue.addGuaranteed(
            BusinessRejectMessage.createBusinessRejectWithClOrdIdStr(order.getSenderCompId(),
                MsgType.ORDER_SINGLE,
                Long.toString(order.getOrderId()),
                BusinessRejectReason.REJECTED_FROM_EXTERNAL_EXCHANGE, ERROR_PLEASE_TRY_AGAIN_LATER,
                order.getOrderId(), order.getSourceSeqNum(), order.getSecondaryOrderId(),
                order.getSecurityId(),
                order.getClOrdId(), order.getAccount()));
        order.setRejected(true);
      }
      return order;
    }
  }

  public static void addWsOrder(final WsOrderUpdate order) {
    WS_ORDER_CACHE.put(order.getClOrdId(), order);
  }

  public static WsOrderUpdate getWsOrder(final String clOrdId) {
    return WS_ORDER_CACHE.get(clOrdId);
  }

  private org.knowm.xchange.instrument.Instrument getInstrument(final String exchange, final XExchange xExchange,
      final CurrencyPair currencyPair, final boolean isFuture) {
    final String key = (exchange + "_" + currencyPair.toString() + "_" + (isFuture ? "1" : "0")).toLowerCase();
    org.knowm.xchange.instrument.Instrument instrument = ExternalInstrumentCache.getInstrument(key);
    if (instrument != null) {
      return instrument;
    } else {
      instrument = xExchange.getInstrument(currencyPair, isFuture);
      ExternalInstrumentCache.addInstrument(key, instrument);
      return instrument;
    }
  }

  private SymbolBalance selectBestExchange(final Map<String, Map<Boolean, SymbolBalance>> selectedExchanges, final Order order,
      final double orderQty, final double ordValue) {

    if (order.getSide() == Side.BUY) {
      // check available perps to close
      for (final String exchange : EXCHANGE_PREFERENCE) {
        // <futures, SymbolStatus>
        final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
        if (map != null) {
          final SymbolBalance selected = map.get(true);
          if (selected != null) {
            final ExchangeSubscription subscription = LiquiditySubscriptionCache.get(selected.getSymbolData().getExchange(), true);
            final String key = (selected.getSymbolData().getExchange() + "_" + true).toLowerCase();
            final XExchange xExchange = X_EXCHANGE_CACHE.get(key);
            final XExchange.Balance balance =
                ExternalExchangeHandler.getBalance(subscription, xExchange, selected.getSymbolData().getBase());
            double convertedOrderQty = orderQty;
            if (selected.getSymbolData().getMultiplierContract() > 0) {
              convertedOrderQty = orderQty / selected.getSymbolData().getMultiplierContract();
            }
            if (balance.getCoinBalance() < 0 && Math.abs(balance.getCoinBalance()) >= convertedOrderQty) {
              LOGGER.info(LOG_FMT_6, "Select best (short). exchange: ", exchange.toLowerCase(), " futuresEnabled: ", true, " selected: ",
                  selected);
              return selected;
            }
          }
        }
      }
    }

    for (final String exchange : EXCHANGE_PREFERENCE) {
      final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
      if (map != null) {
        SymbolBalance selected = map.get(false);// try spot first
        if (selected != null) {
          LOGGER.info(LOG_FMT_6, "Select best. exchange: ", exchange.toLowerCase(), " futuresEnabled: ", false, " selected: ", selected);
          return selected;
        }
      }
    }
    for (String exchange : EXCHANGE_PREFERENCE) {
      final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
      if (map != null) {
        SymbolBalance selected = map.get(true);// try futures next
        if (selected != null) {
          LOGGER.info(LOG_FMT_6, "Select preferred. exchange: ", exchange.toLowerCase(), " futuresEnabled: ", true, " selected: ",
              selected);
          return selected;
        }
      }
    }
    final StringBuilder sb = new StringBuilder();
    for (String exchange : EXCHANGE_PREFERENCE) {
      final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
      sb.append("\nFutures: ").append(map.get(true));
      sb.append("\nSpot:").append(map.get(false));
    }

    LOGGER.info(LOG_FMT_2, "Select preferred. nothing found: ", sb);
    return null;
  }

  private SymbolBalance selectBestExchangeNew(final Map<String, Map<Boolean, SymbolBalance>> selectedExchanges, final Order order,
      final double orderQty, final double ordValue) {

    if (order.getSide() == Side.BUY) {
      // check available perps to close
      for (final String exchange : EXCHANGE_PREFERENCE) {
        // <futures, SymbolStatus>
        final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
        if (map != null) {
          final SymbolBalance selected = map.get(true);
          if (selected != null) {
            final ExchangeSubscription subscription = selected.getSubscription();
            final double positionBalance =
                subscription.getPosition(selected.getSymbolData().getBase() + selected.getSymbolData().getQuote());
            double convertedOrderQty = orderQty;
            if (selected.getSymbolData().getMultiplierContract() > 0) {
              convertedOrderQty = orderQty / selected.getSymbolData().getMultiplierContract();
            }
            if (positionBalance < 0 && Math.abs(positionBalance) >= convertedOrderQty) {
              LOGGER.info(LOG_FMT_6, "Select best (short). exchange: ", exchange.toLowerCase(), " futuresEnabled: ", true, " selected: ",
                  selected);
              return selected;
            }
          }
        }
      }
    }

    for (final String exchange : EXCHANGE_PREFERENCE) {
      final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
      if (map != null) {
        SymbolBalance selected = map.get(false);// try spot first
        if (selected != null) {
          LOGGER.info(LOG_FMT_6, "Select best. exchange: ", exchange.toLowerCase(), " futuresEnabled: ", false, " selected: ", selected);
          return selected;
        }
      }
    }
    for (String exchange : EXCHANGE_PREFERENCE) {
      final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
      if (map != null) {
        SymbolBalance selected = map.get(true);// try futures next
        if (selected != null) {
          LOGGER.info(LOG_FMT_6, "Select preferred. exchange: ", exchange.toLowerCase(), " futuresEnabled: ", true, " selected: ",
              selected);
          return selected;
        }
      }
    }
    final StringBuilder sb = new StringBuilder();
    for (String exchange : EXCHANGE_PREFERENCE) {
      final Map<Boolean, SymbolBalance> map = selectedExchanges.get(exchange.toLowerCase());
      sb.append("\nFutures: ").append(map.get(true));
      sb.append("\nSpot:").append(map.get(false));
    }

    LOGGER.info(LOG_FMT_2, "Select preferred. nothing found: ", sb);
    return null;
  }

  private double getPerpPrice(final ExternalSymbol externalSymbol, final Order order) {
    int tardisExchangeId = LiquidityCache.getTardisExchangeId(externalSymbol.getExchange(), TARDIS_PERPS);
    if (tardisExchangeId > 0) {
      int tardisSymbolId = LiquidityCache.getTardisSymbolId(tardisExchangeId, TARDIS_PERPS,
          externalSymbol.getBase(), externalSymbol.getQuote());
      if (tardisSymbolId > 0) {
        final InstrumentPair pair = InstrumentCache.getPair(order.getSecurityId());
        final LiquidityOrderBook orderBook = (LiquidityOrderBook) pair.getOrderBook(); // always a liquidity orderbook
        final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Liquidity>> fullLiquidityMap = orderBook.getExchangeSymbolLiquidity();
        final int qtyScale = externalSymbol.getQtyScale();
        final double qtyMul = Math.pow(10, qtyScale);
        double rawQty = MbxMath.scaleDown(order.getQuantityOrigLong(), order.getQuantityOrigScale());
        final double contractMultiplier =
            externalSymbol.getMultiplierContract() > 0 ? externalSymbol.getMultiplierContract()
                : 1D;
        if (fullLiquidityMap != null) {
          final ConcurrentHashMap<Integer, Liquidity> exchangeLiquidity = fullLiquidityMap.get(
              tardisExchangeId);
          if (exchangeLiquidity != null) {
            Liquidity symbolLiquidity = exchangeLiquidity.get(tardisSymbolId);
            if (symbolLiquidity != null) {
              final long qtyUnits = (long) ((rawQty / contractMultiplier) * qtyMul);
              double tradeQtyDouble = qtyUnits / qtyMul;
              final double bps = CMCTop30Checker.getBPS(externalSymbol.getBase());
              final InstrumentPair stableCoinPair = InstrumentCache.getPairBySymbol(
                  externalSymbol.getQuote() + "/" + USD);

              final double fxRate = stableCoinPair.getUsdMark();
              final int priceMultiplier = LiquidityCache.getMaximumPriceMultiplier((symbolLiquidity.getBestAsk() + symbolLiquidity.getBestBid())/2D);
              if (order.getSide() == Side.BUY) {
                final LiquidityResponse.Depth[] asks = symbolLiquidity.getAsks();
                for (LiquidityResponse.Depth ask : asks) {
                  if (tradeQtyDouble <= ask.getCumQty()) {
                    final double p1 = ask.getPrice() * (1 + bps) * fxRate;
                    final double p = MbxMath.roundUp(p1, priceMultiplier);
                    LOGGER.info(LOG_FMT_8, "Liquidity Prep price: ", ask.getPrice(), " bps: ", bps, " fxRate: ", fxRate, " finalPrice: ", p);
                    return p;
                  }
                }
              } else {
                final LiquidityResponse.Depth[] bids = symbolLiquidity.getBids();
                for (LiquidityResponse.Depth bid : bids) {
                  if (tradeQtyDouble <= bid.getCumQty()) {
                    final double p1 = bid.getPrice() * (1 - bps) * fxRate;
                    final double p = MbxMath.roundDown(p1, priceMultiplier);
                    LOGGER.info(LOG_FMT_8, "Liquidity Prep price: ", bid.getPrice(), " bps: ", bps, " fxRate: ", fxRate, " finalPrice: ", p);
                    return p;
                  }
                }
              }
            }
          }
        }
      }
    }
    return 0;
  }

  /*public static int getMaximumPriceMultiplier(final double symbolPrice) {
    int multiplier = 0;
    double price = symbolPrice;
    if (price == 0) {
      return multiplier;
    }

    if (price >= 1) {
      multiplier = 100;
      return multiplier;
    }

    int exponent = 0;
    while (price < 100) {
      price *= 10;
      exponent++;
    }

    multiplier = (int) Math.pow(10, exponent + 1);

    return multiplier;
  }*/


}
