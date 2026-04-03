package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.*;
import com.solfini.instrument.*;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.liquidity.*;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.kucoin.*;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.sbe.encoder.*;
import com.solfini.user.*;
import com.solfini.util.*;

import java.io.IOException;
import java.util.Properties;

public class KucoinMockTradeTest {

    final static String exchange = "KUCOIN";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(KucoinMockTradeTest.class);

    // Simple pass/fail counters for summary
    private static int passed = 0;
    private static int failed = 0;

    private static void check(final String testName, final boolean condition) {
        if (condition) {
            LOGGER.info("  PASS: " + testName);
            passed++;
        } else {
            LOGGER.error("  FAIL: " + testName);
            failed++;
        }
    }

    private static void section(final String name) {
        LOGGER.info("=== " + name + " ===");
    }

    private static void init() throws IOException {

        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();
        Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        PropertyReader.initialize(null, properties);

        final Instrument usdt = new Instrument(1, "USDT", "USDT", (short) 2, (short) 6, 1, 1000, 0, false, 1, Sector.NOT_DEFINED);
        usdt.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdt);

        UserCache.setTestUser(new User(100));
    }

    public static void main(String[] args) throws Exception {

        init();

        // ── Section 1: FutureUserDataListener – all message types (no network) ───────
        testFutureUserDataListenerMock();

        // ── Section 2: SpotUserDataListener – all message types (no network) ─────────
        testSpotUserDataListenerMock();

        // ── Summary ───────────────────────────────────────────────────────────────────
        LOGGER.info("====================================");
        LOGGER.info("TEST SUMMARY: passed=" + passed + "  failed=" + failed);
        if (failed > 0) {
            LOGGER.error("SOME TESTS FAILED");
        } else {
            LOGGER.info("ALL TESTS PASSED");
        }

        // ── Section 3: FutureTradeListener + live REST (requires env vars) ────────────
        // Kept below summary so mock tests always print results even if live section hangs.
        final String apiKey = System.getenv("KUCOIN_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.info("Skipping live REST/WS tests: KUCOIN_API_KEY not set");
            System.exit(0);
        }

        LiquiditySubscriptionCache.onLoad(getKuCoinFutureAccount());
        LiquiditySubscriptionCache.startAllSubscriptions();
        final ExchangeSubscription futureSubscription = LiquiditySubscriptionCache.get(exchange, true);
        final KucoinRestClient kuCoinRestClient = new KucoinRestClient(futureSubscription.getApiKey(), futureSubscription.getApiSecret(),
                futureSubscription, Context.getKuCoinSpotRest(), Context.getKuCoinFuturesRest());

        section("FutureTradeListener – order placement responses (live)");
        final KucoinFutureTradeListener futureTradeListener = new KucoinFutureTradeListener(futureSubscription, kuCoinRestClient);

        final Order createdOrder = getSampleOrder();
        futureSubscription.cacheNewOrder(createdOrder);
        ExecutionReportMessage executionReportMessage = ExecutionReportMessage.createExternalExecutionReport(
                createdOrder.getOrderId(), createdOrder.getUser(), 0, createdOrder.getSymbol(),
                0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, createdOrder.getSide(), 0);
        executionReportMessage.setClOrdId(createdOrder.getClOrdId());
        futureSubscription.updateExecutionReport(executionReportMessage);
        futureTradeListener.onMessage(getFutureTradeOrderAcceptedJson(createdOrder.getClOrdId()));
        LOGGER.info("After accept: order=" + futureSubscription.getOrder(createdOrder.getClOrdId()));

        final Order rejectOrder = getSampleOrderReject();
        futureSubscription.cacheNewOrder(rejectOrder);
        ExecutionReportMessage rejectEr = ExecutionReportMessage.createExternalExecutionReport(
                rejectOrder.getOrderId(), rejectOrder.getUser(), 0, rejectOrder.getSymbol(),
                0L, (short) 0, 0L, (short) 0, 0, 0, 0, 0, rejectOrder.getSide(), 0);
        rejectEr.setClOrdId(rejectOrder.getClOrdId());
        futureSubscription.updateExecutionReport(rejectEr);
        futureTradeListener.onMessage(getFutureTradeOrderRejectedJson(rejectOrder.getClOrdId(), "Insufficient margin"));
        LOGGER.info("After reject: order=" + futureSubscription.getOrder(rejectOrder.getClOrdId()));
    }

    /* =========================================================
       SECTION 2: FutureUserDataListener mock tests
       ========================================================= */

    public static void testFutureUserDataListenerMock() {
        section("FutureUserDataListener – mock message parsing");

        // Fresh subscription so state is isolated
        final ExchangeSubscription sub = getKuCoinFutureAccount();
        // Listener with no real WS (token/endpoint/supplier/outboundIp all null – onMessage doesn't use them)
        final KucoinFutureUserDataListener listener = new KucoinFutureUserDataListener(sub, null, null, null, null);

        // ── Balance message ───────────────────────────────────────────────────────────
        listener.onMessage(getFutureBalanceJson());
        final double balanceUsdt = sub.getBalance("USDT");
        check("Balance: walletBalance parsed (USDT > 0)", balanceUsdt > 0);
        check("Balance: walletBalance value ~371.39", Math.abs(balanceUsdt - 371.394298816) < 0.001);

        // ── Position message ──────────────────────────────────────────────────────────
        listener.onMessage(getFuturePositionJson());
        final double posXbt = sub.getPosition("XBTUSDTM");
        check("Position: currentQty parsed for XBTUSDTM", posXbt == 1.0);

        // ── Order open (no clientOid, keyed by orderId) ───────────────────────────────
        // getFutureOrderOpenMessage: orderId=247899236673269761, side=buy, size=1, price=91670
        final String futOrderKey = "247899236673269761";
        final Order futOrderOpen = makeFutureOrder(futOrderKey, "XBTUSDTM", Side.BUY, 91670L, (short) 0, 1L, (short) 0);
        sub.cacheNewOrder(futOrderOpen);
        seedExecutionReport(sub, futOrderOpen);

        listener.onMessage(getFutureOrderOpenMessage());
        final ExecutionReportMessage erOpen = sub.getExecutionReport(futOrderKey);
        check("Future open: ER exists", erOpen != null);
        if (erOpen != null) {
            check("Future open: OrdStatus=NEW", OrdStatus.NEW == erOpen.getOrdStatus());
            check("Future open: ExecType=NEW", ExecType.NEW == erOpen.getExecType());
            check("Future open: cumQty=0", erOpen.getCumQty() == 0);
        }

        // ── Order match (status=done → FILLED) ────────────────────────────────────────
        // getFutureOrderMatchMessage: orderId=247899236673269761, matchPrice=91670, matchSize=1, filledSize=1, tradeId=1794175373644, status=done
        listener.onMessage(getFutureOrderMatchMessage());
        final ExecutionReportMessage erMatch = sub.getExecutionReport(futOrderKey);
        check("Future match(done): ER exists", erMatch != null);
        if (erMatch != null) {
            check("Future match(done): OrdStatus=FILLED", OrdStatus.FILLED == erMatch.getOrdStatus());
            check("Future match(done): ExecType=TRADE", ExecType.TRADE == erMatch.getExecType());
            check("Future match(done): cumQty=1 (scale 0)", erMatch.getCumQty() == 1L);
            check("Future match(done): lastPx=91670 (scale 0)", erMatch.getLastPx() == 91670L);
            check("Future match(done): lastQty=1 (scale 0)", erMatch.getLastQty() == 1L);
            check("Future match(done): execId=1794175373644", erMatch.getExecId() == 1794175373644L);
        }

        // ── Order filled ──────────────────────────────────────────────────────────────
        // getFutureOrderFilledMessage: orderId=247899236673269761, filledSize=1
        listener.onMessage(getFutureOrderFilledMessage());
        final ExecutionReportMessage erFilled = sub.getExecutionReport(futOrderKey);
        check("Future filled: ER exists", erFilled != null);
        if (erFilled != null) {
            check("Future filled: OrdStatus=FILLED", OrdStatus.FILLED == erFilled.getOrdStatus());
            check("Future filled: ExecType=TRADE", ExecType.TRADE == erFilled.getExecType());
            check("Future filled: cumQty=1", erFilled.getCumQty() == 1L);
        }

        // ── Order update (partial cancel, keyed by clientOid) ────────────────────────
        // getFutureOrderUpdateMessage: clientOid=10496pp066R679264, orderId=228685469427204099, filledSize=0, type=update
        final String updateKey = "10496pp066R679264";
        final Order futOrderUpdate = makeFutureOrder(updateKey, "RUNEUSDTM", Side.BUY, 5029L, (short) 3, 19982L, (short) 0);
        sub.cacheNewOrder(futOrderUpdate);
        seedExecutionReport(sub, futOrderUpdate);

        listener.onMessage(getFutureOrderUpdateMessage());
        final ExecutionReportMessage erUpdate = sub.getExecutionReport(updateKey);
        check("Future update: ER exists", erUpdate != null);
        if (erUpdate != null) {
            check("Future update: OrdStatus=CANCELED", OrdStatus.CANCELED == erUpdate.getOrdStatus());
            check("Future update: ExecType=CANCELED", ExecType.CANCELED == erUpdate.getExecType());
            check("Future update: cumQty=0 (filledSize=0)", erUpdate.getCumQty() == 0L);
        }

        // ── Order canceled ────────────────────────────────────────────────────────────
        // getFutureOrderCanceledMessage: orderId=247901211536203776, canceledSize=1, filledSize=0
        final String cancelKey = "247901211536203776";
        final Order futOrderCancel = makeFutureOrder(cancelKey, "XBTUSDTM", Side.BUY, 90000L, (short) 0, 1L, (short) 0);
        sub.cacheNewOrder(futOrderCancel);
        seedExecutionReport(sub, futOrderCancel);

        listener.onMessage(getFutureOrderCanceledMessage());
        final ExecutionReportMessage erCancel = sub.getExecutionReport(cancelKey);
        check("Future canceled: ER exists", erCancel != null);
        if (erCancel != null) {
            check("Future canceled: OrdStatus=CANCELED", OrdStatus.CANCELED == erCancel.getOrdStatus());
            check("Future canceled: ExecType=CANCELED", ExecType.CANCELED == erCancel.getExecType());
            check("Future canceled: cumQty=0 (filledSize=0)", erCancel.getCumQty() == 0L);
        }

        // ── Liquid match (tradeType=liquid, status=match → PARTIALLY_FILLED) ──────────
        // getFutureOrderMatchLiquidMessage: orderId=440761625608192, matchPrice=85739.69, matchSize=1000, filledSize=1116, status=match
        final String liquidKey = "440761625608192";
        final Order futLiquid = makeFutureOrder(liquidKey, "XBTUSDTM", Side.SELL, 84603L, (short) 2, 3840L, (short) 0);
        sub.cacheNewOrder(futLiquid);
        seedExecutionReport(sub, futLiquid);

        listener.onMessage(getFutureOrderMatchLiquidMessage());
        final ExecutionReportMessage erLiquid = sub.getExecutionReport(liquidKey);
        check("Future liquid match: ER exists", erLiquid != null);
        if (erLiquid != null) {
            check("Future liquid match: OrdStatus=PARTIALLY_FILLED", OrdStatus.PARTIALLY_FILLED == erLiquid.getOrdStatus());
            check("Future liquid match: ExecType=TRADE", ExecType.TRADE == erLiquid.getExecType());
            check("Future liquid match: cumQty=1116 (scale 0)", erLiquid.getCumQty() == 1116L);
            // lastPx = changeScale(85739.69, 2) = 8573969
            check("Future liquid match: lastPx=8573969 (scale 2)", erLiquid.getLastPx() == 8573969L);
            check("Future liquid match: lastQty=1000 (scale 0)", erLiquid.getLastQty() == 1000L);
            check("Future liquid match: execId=1740800012709", erLiquid.getExecId() == 1740800012709L);
        }

        // ── ADL match (tradeType=adl, status=match → PARTIALLY_FILLED) ───────────────
        // getFutureOrderMatchAdlMessage: orderId=1961728417792, matchPrice=0.0000126, matchSize=100, filledSize=100, status=match
        final String adlKey = "1961728417792";
        final Order futAdl = makeFutureOrder(adlKey, "10PEPEUSDTM", Side.SELL, 0L, (short) 7, 100L, (short) 0);
        sub.cacheNewOrder(futAdl);
        seedExecutionReport(sub, futAdl);

        listener.onMessage(getFutureOrderMatchAdlMessage());
        final ExecutionReportMessage erAdlMatch = sub.getExecutionReport(adlKey);
        check("Future ADL match: ER exists", erAdlMatch != null);
        if (erAdlMatch != null) {
            check("Future ADL match: OrdStatus=PARTIALLY_FILLED (status=match)", OrdStatus.PARTIALLY_FILLED == erAdlMatch.getOrdStatus());
            check("Future ADL match: ExecType=TRADE", ExecType.TRADE == erAdlMatch.getExecType());
            check("Future ADL match: cumQty=100 (scale 0)", erAdlMatch.getCumQty() == 100L);
        }

        // ── ADL filled (tradeType=adl, type=filled → FILLED) ─────────────────────────
        // getFutureOrderFilledAdlMessage: orderId=1961728417792, filledSize=100, type=filled
        listener.onMessage(getFutureOrderFilledAdlMessage());
        final ExecutionReportMessage erAdlFilled = sub.getExecutionReport(adlKey);
        check("Future ADL filled: ER exists", erAdlFilled != null);
        if (erAdlFilled != null) {
            check("Future ADL filled: OrdStatus=FILLED", OrdStatus.FILLED == erAdlFilled.getOrdStatus());
            check("Future ADL filled: ExecType=TRADE", ExecType.TRADE == erAdlFilled.getExecType());
            check("Future ADL filled: cumQty=100", erAdlFilled.getCumQty() == 100L);
        }

        // ── Unknown type is silently ignored (no crash) ───────────────────────────────
        listener.onMessage("{\"topic\":\"/contractMarket/tradeOrders\",\"type\":\"message\",\"subject\":\"orderChange\",\"channelType\":\"private\",\"data\":{\"orderId\":\"999\",\"type\":\"unknown_type\"}}");
        check("Future unknown type: no exception", true);

        // ── welcome/ack are ignored ───────────────────────────────────────────────────
        listener.onMessage(getFutureTradeWelcomeJson());
        check("Future welcome: ignored without exception", true);
    }

    /* =========================================================
       SECTION 3: SpotUserDataListener mock tests
       ========================================================= */

    public static void testSpotUserDataListenerMock() {
        section("SpotUserDataListener – mock message parsing");

        final ExchangeSubscription sub = getKuCoinSpotAccount();
        final KucoinSpotUserDataListener listener = new KucoinSpotUserDataListener(sub, null, null, null, null);

        // ── Balance message ───────────────────────────────────────────────────────────
        // getSpotBalanceJson: currency=USDT, available=20.132773386762, total=21.133773386762
        listener.onMessage(getSpotBalanceJson());
        final double spotBalance = sub.getBalance("USDT");
        check("Spot balance: available parsed (USDT > 0)", spotBalance > 0);
        check("Spot balance: available value ~20.13", Math.abs(spotBalance - 20.132773386762) < 0.001);

        // ── Order A: received → match → filled (clientOid=5c52e11203aa677f33e493fc) ──
        // side=buy, symbol=BTC-USDT, market order
        // priceScale=1, qtyScale=5 → changeScale(0.00001, 5) = 1, changeScale(71171.9, 1) = 711719
        final String orderA = "5c52e11203aa677f33e493fc";
        final Order spotOrderA = makeSpotOrder(orderA, "BTCUSDT", Side.BUY, 0L, (short) 1, 1L, (short) 5);
        sub.cacheNewOrder(spotOrderA);
        seedExecutionReport(sub, spotOrderA);

        // received → NEW
        listener.onMessage(getSpotOrderReceivedMessage());
        final ExecutionReportMessage erReceived = sub.getExecutionReport(orderA);
        check("Spot received: ER exists", erReceived != null);
        if (erReceived != null) {
            check("Spot received: OrdStatus=NEW", OrdStatus.NEW == erReceived.getOrdStatus());
            check("Spot received: ExecType=NEW", ExecType.NEW == erReceived.getExecType());
            check("Spot received: cumQty=0", erReceived.getCumQty() == 0);
        }

        // match (status=match → PARTIALLY_FILLED): matchPrice=71171.9, matchSize=0.00001, filledSize=0.00001
        listener.onMessage(getSpotOrderMatchMessage());
        final ExecutionReportMessage erMatchA = sub.getExecutionReport(orderA);
        check("Spot match(match): ER exists", erMatchA != null);
        if (erMatchA != null) {
            check("Spot match(match): OrdStatus=PARTIALLY_FILLED", OrdStatus.PARTIALLY_FILLED == erMatchA.getOrdStatus());
            check("Spot match(match): ExecType=TRADE", ExecType.TRADE == erMatchA.getExecType());
            check("Spot match(match): cumQty=1 (0.00001*10^5)", erMatchA.getCumQty() == 1L);
            // matchPrice=71171.9, priceScale=1 → 711719
            check("Spot match(match): lastPx=711719 (scale 1)", erMatchA.getLastPx() == 711719L);
            check("Spot match(match): lastQty=1 (0.00001*10^5)", erMatchA.getLastQty() == 1L);
        }

        // filled → FILLED: filledSize=0.00001
        listener.onMessage(getSpotOrderFilledMessage());
        final ExecutionReportMessage erFilledA = sub.getExecutionReport(orderA);
        check("Spot filled: ER exists", erFilledA != null);
        if (erFilledA != null) {
            check("Spot filled: OrdStatus=FILLED", OrdStatus.FILLED == erFilledA.getOrdStatus());
            check("Spot filled: ExecType=TRADE", ExecType.TRADE == erFilledA.getExecType());
            check("Spot filled: cumQty=1", erFilledA.getCumQty() == 1L);
        }

        // ── Order B: open → update → canceled (clientOid=5c52e11203aa677f33e493fb) ──
        // side=buy, limit @50000, size=0.00001
        final String orderB = "5c52e11203aa677f33e493fb";
        final Order spotOrderB = makeSpotOrder(orderB, "BTCUSDT", Side.BUY, 500000L, (short) 1, 1L, (short) 5);
        sub.cacheNewOrder(spotOrderB);
        seedExecutionReport(sub, spotOrderB);

        // open → NEW: price=50000, size=0.00001
        listener.onMessage(getSpotOrderOpenMessage());
        final ExecutionReportMessage erOpen = sub.getExecutionReport(orderB);
        check("Spot open: ER exists", erOpen != null);
        if (erOpen != null) {
            check("Spot open: OrdStatus=NEW", OrdStatus.NEW == erOpen.getOrdStatus());
            check("Spot open: ExecType=NEW", ExecType.NEW == erOpen.getExecType());
            check("Spot open: cumQty=0", erOpen.getCumQty() == 0);
            // price=50000, priceScale=1 → changeScale(50000.0, 1) = 500000
            check("Spot open: price=500000 (scale 1)", erOpen.getPrice() == 500000L);
        }

        // update (partial cancel) → CANCELED: filledSize=0
        listener.onMessage(getSpotOrderUpdateMessage());
        final ExecutionReportMessage erUpdate = sub.getExecutionReport(orderB);
        check("Spot update: ER exists", erUpdate != null);
        if (erUpdate != null) {
            check("Spot update: OrdStatus=CANCELED", OrdStatus.CANCELED == erUpdate.getOrdStatus());
            check("Spot update: ExecType=CANCELED", ExecType.CANCELED == erUpdate.getExecType());
            check("Spot update: cumQty=0 (filledSize=0)", erUpdate.getCumQty() == 0L);
        }

        // canceled → CANCELED: filledSize=0
        listener.onMessage(getSpotOrderCanceledMessage());
        final ExecutionReportMessage erCancel = sub.getExecutionReport(orderB);
        check("Spot canceled: ER exists", erCancel != null);
        if (erCancel != null) {
            check("Spot canceled: OrdStatus=CANCELED", OrdStatus.CANCELED == erCancel.getOrdStatus());
            check("Spot canceled: ExecType=CANCELED", ExecType.CANCELED == erCancel.getExecType());
            check("Spot canceled: cumQty=0", erCancel.getCumQty() == 0L);
        }

        // ── welcome/ack are ignored ───────────────────────────────────────────────────
        listener.onMessage(getFutureTradeWelcomeJson());
        check("Spot welcome: ignored without exception", true);

        // ── Unknown order (not in cache) is logged and dropped ────────────────────────
        listener.onMessage("{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"channelType\":\"private\",\"data\":{\"clientOid\":\"nonexistent-order-id\",\"type\":\"open\"}}");
        check("Spot unknown clientOid: no exception", true);
    }

    /* =========================================================
       ORDER FACTORIES
       ========================================================= */

    /** Create a futures-style order. clOrdId must match the key the listener will look up (orderId or clientOid from WS msg). */
    private static Order makeFutureOrder(final String clOrdId, final String symbol,
                                         final Side side, final long price, final short priceScale,
                                         final long qty, final short qtyScale) {
        final Order order = new Order();
        order.setOrderId(Math.abs(clOrdId.hashCode()));
        order.setClOrdId(clOrdId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setOrdType(OrdType.LIMIT);
        order.setType(side == Side.BUY ? Constants.BUY_LIMIT : Constants.SELL_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(price, priceScale);
        order.setQty(qty, qtyScale);
        order.setPlatform("KUCOIN");
        order.setUser(UserCache.getTestUser());
        return order;
    }

    /** Create a spot-style order. */
    private static Order makeSpotOrder(final String clOrdId, final String symbol,
                                       final Side side, final long price, final short priceScale,
                                       final long qty, final short qtyScale) {
        return makeFutureOrder(clOrdId, symbol, side, price, priceScale, qty, qtyScale);
    }

    /** Pre-seed an ExecutionReport so getOrCreateExecutionReport finds one in the cache. */
    private static void seedExecutionReport(final ExchangeSubscription sub, final Order order) {
        final ExecutionReportMessage er = ExecutionReportMessage.createExternalExecutionReport(
                order.getOrderId(), order.getUser(), 0, order.getSymbol(),
                order.getPrice(), order.getPriceScale(), order.getQty(), order.getQtyScale(),
                0, 0, 0, 0, order.getSide(), 0);
        er.setClOrdId(order.getClOrdId());
        sub.updateExecutionReport(er);
    }

    /* =========================================================
       LEGACY ORDER CREATION (kept for FutureTradeListener tests)
       ========================================================= */

    public static Order getSampleOrder() {

        Order order = new Order();
        order.setOrderId(672);
        order.setClOrdId("5c52e11203aa677f33e493fc");
        order.setSymbol("ZORAUSDT");
        order.setSide(Side.SELL);
        order.setOrdType(OrdType.LIMIT);
        order.setType(Constants.SELL_LIMIT);
        order.setTimeInForce(TimeInForce.FILL_OR_KILL);
        order.setPrice(4628L, (short) 5);
        order.setQty(540L, (short) 5);
        order.setPlatform("KUCOIN");
        order.setUser(UserCache.getTestUser());

        return order;
    }

    /** Sample order used for reject path in FutureTradeListener mock. */
    public static Order getSampleOrderReject() {
        Order order = new Order();
        order.setOrderId(673);
        order.setClOrdId("1765893161879788749");
        order.setSymbol("XBTUSDT");
        order.setSide(Side.BUY);
        order.setOrdType(OrdType.LIMIT);
        order.setType(Constants.BUY_LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(95000L, (short) 2);
        order.setQty(1L, (short) 6);
        order.setPlatform("KUCOIN");
        order.setUser(UserCache.getTestUser());
        return order;
    }

    /* =========================================================
       SPOT ORDER LIFECYCLE MESSAGES
       ========================================================= */

    public static String getSpotBalanceJson() {
        return "{\"topic\":\"/account/balance\",\"type\":\"message\",\"subject\":\"account.balance\",\"id\":\"354689988084000\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"accountId\":\"548674591753\",\"currency\":\"USDT\",\"total\":\"21.133773386762\",\"available\":\"20.132773386762\",\"hold\":\"1.001\",\"time\":\"1730269283892\"}}";
    }

    public static String getFutureBalanceJson() {
        return "{\"topic\":\"/contractAccount/wallet\",\"type\":\"message\",\"subject\":\"walletBalance.change\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"equity\":\"387.224858816\",\"availableBalance\":\"285.652001096\",\"walletBalance\":\"371.394298816\",\"currency\":\"USDT\"}}";
    }

    public static String getFuturePositionJson() {
        return "{\"topic\":\"/contract/position:XBTUSDTM\",\"type\":\"message\",\"subject\":\"position.change\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"symbol\":\"XBTUSDTM\",\"currentQty\":1,\"avgEntryPrice\":91694.5,\"leverage\":4.96,\"marginMode\":\"ISOLATED\"}}";
    }

    public static String getOrderAcknowledgeJson() {
        return "{\"reqId\":\"1759432560816495533\",\"retCode\":0,\"retMsg\":\"OK\",\"op\":\"order.create\",\"data\":{\"orderId\":\"373b6927-71ff-4bde-a8a6-c3de21838f20\",\"orderLinkId\":\"1759432560816495533\"}}";
    }

    public static String getSpotOrderReceivedMessage() {
        return "{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"clientOid\":\"5c52e11203aa677f33e493fc\",\"orderId\":\"6720da3fa30a360007f5f832\",\"orderTime\":1730206271588,\"orderType\":\"market\",\"originSize\":\"0.00001\",\"side\":\"buy\",\"status\":\"new\",\"symbol\":\"BTC-USDT\",\"ts\":1730206271616000000,\"type\":\"received\"}}";
    }

    public static String getSpotOrderOpenMessage() {
        return "{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"canceledSize\":\"0\",\"clientOid\":\"5c52e11203aa677f33e493fb\",\"filledSize\":\"0\",\"orderId\":\"6720ecd9ec71f4000747731a\",\"orderTime\":1730211033305,\"orderType\":\"limit\",\"originSize\":\"0.00001\",\"price\":\"50000\",\"remainSize\":\"0.00001\",\"side\":\"buy\",\"size\":\"0.00001\",\"status\":\"open\",\"symbol\":\"BTC-USDT\",\"ts\":1730211033335000000,\"type\":\"open\"}}";
    }

    public static String getSpotOrderUpdateMessage() {
        return "{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"canceledSize\":\"0.00001\",\"clientOid\":\"5c52e11203aa677f33e493fb\",\"filledSize\":\"0\",\"oldSize\":\"0.00002\",\"orderId\":\"6720df7640e6fe0007b57696\",\"orderTime\":1730207606848,\"orderType\":\"limit\",\"originSize\":\"0.00002\",\"price\":\"50000\",\"remainSize\":\"0.00001\",\"side\":\"buy\",\"size\":\"0.00001\",\"status\":\"open\",\"symbol\":\"BTC-USDT\",\"ts\":1730207616617000000,\"type\":\"update\"}}";
    }

    public static String getSpotOrderMatchMessage() {
        return "{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"canceledSize\":\"0\",\"clientOid\":\"5c52e11203aa677f33e493fc\",\"feeType\":\"takerFee\",\"filledSize\":\"0.00001\",\"liquidity\":\"taker\",\"matchPrice\":\"71171.9\",\"matchSize\":\"0.00001\",\"orderId\":\"6720da3fa30a360007f5f832\",\"orderTime\":1730206271588,\"orderType\":\"market\",\"originSize\":\"0.00001\",\"remainSize\":\"0\",\"side\":\"buy\",\"size\":\"0.00001\",\"status\":\"match\",\"symbol\":\"BTC-USDT\",\"tradeId\":\"11116472408358913\",\"ts\":1730206271616000000,\"type\":\"match\"}}";
    }

    public static String getSpotOrderFilledMessage() {
        return "{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"canceledSize\":\"0\",\"clientOid\":\"5c52e11203aa677f33e493fc\",\"filledSize\":\"0.00001\",\"orderId\":\"6720da3fa30a360007f5f832\",\"orderTime\":1730206271588,\"orderType\":\"market\",\"originSize\":\"0.00001\",\"remainFunds\":\"0\",\"remainSize\":\"0\",\"side\":\"buy\",\"size\":\"0.00001\",\"status\":\"done\",\"symbol\":\"BTC-USDT\",\"ts\":1730206271616000000,\"type\":\"filled\"}}";
    }

    public static String getSpotOrderCanceledMessage() {
        return "{\"topic\":\"/spotMarket/tradeOrdersV2\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"canceledSize\":\"0.00002\",\"clientOid\":\"5c52e11203aa677f33e493fb\",\"filledSize\":\"0\",\"orderId\":\"6720df7640e6fe0007b57696\",\"orderTime\":1730207606848,\"orderType\":\"limit\",\"originSize\":\"0.00002\",\"price\":\"50000\",\"remainFunds\":\"0\",\"remainSize\":\"0\",\"side\":\"buy\",\"size\":\"0.00001\",\"status\":\"done\",\"symbol\":\"BTC-USDT\",\"ts\":1730207624559000000,\"type\":\"canceled\"}}";
    }

    /* =========================================================
       FUTURES ORDER LIFECYCLE MESSAGES
       ========================================================= */

    public static String getFutureOder() {
        return "{\"topic\":\"/contractMarket/tradeOrders\",\"type\":\"message\",\"subject\":\"orderChange\",\"channelType\":\"private\",\"data\":{\"clientOid\":\"1765893161879788749\",\"orderId\":\"672\",\"symbol\":\"ZORAUSDTM\",\"orderType\":\"limit\",\"side\":\"sell\",\"price\":\"0.04628\",\"size\":\"540\",\"filledSize\":\"540\",\"remainSize\":\"0\",\"status\":\"done\",\"type\":\"match\",\"orderTime\":1730206271588000000,\"ts\":1730206271616000000}}";
    }

    public static String getFutureOrderOpenMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders:XBTUSDTM\",\"type\":\"message\",\"subject\":\"symbolOrderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"symbol\":\"XBTUSDTM\",\"tradeType\":\"trade\",\"side\":\"buy\",\"canceledSize\":\"0\",\"orderId\":\"247899236673269761\",\"liquidity\":\"maker\",\"marginMode\":\"ISOLATED\",\"type\":\"open\",\"orderTime\":1731916985768138917,\"size\":\"1\",\"filledSize\":\"0\",\"price\":\"91670\",\"remainSize\":\"1\",\"status\":\"open\",\"ts\":1731916985789000000}}";
    }

    public static String getFutureOrderUpdateMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"669a61642857ca000186f626\",\"channelType\":\"private\",\"data\":{\"symbol\":\"RUNEUSDTM\",\"orderType\":\"limit\",\"tradeType\":\"trade\",\"side\":\"buy\",\"canceledSize\":\"1037\",\"orderId\":\"228685469427204099\",\"liquidity\":\"maker\",\"marginMode\":\"ISOLATED\",\"type\":\"update\",\"userId\":\"669a61642857ca000186f626\",\"oldSize\":\"19982\",\"orderTime\":1727336066682194084,\"size\":\"19982\",\"filledSize\":\"0\",\"price\":\"5.029\",\"remainSize\":\"11618\",\"clientOid\":\"10496pp066R679264\",\"status\":\"open\",\"ts\":1727336066766000000}}";
    }

    public static String getFutureOrderMatchMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders:XBTUSDTM\",\"type\":\"message\",\"subject\":\"symbolOrderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"symbol\":\"XBTUSDTM\",\"orderType\":\"limit\",\"tradeType\":\"trade\",\"side\":\"buy\",\"canceledSize\":\"0\",\"orderId\":\"247899236673269761\",\"liquidity\":\"maker\",\"marginMode\":\"ISOLATED\",\"type\":\"match\",\"feeType\":\"makerFee\",\"orderTime\":1731916985768138917,\"size\":\"1\",\"filledSize\":\"1\",\"price\":\"91670\",\"matchPrice\":\"91670\",\"matchSize\":\"1\",\"remainSize\":\"0\",\"tradeId\":\"1794175373644\",\"status\":\"done\",\"ts\":1731916996762000000}}";
    }

    public static String getFutureOrderMatchLiquidMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders:XBTUSDTM\",\"type\":\"message\",\"subject\":\"symbolOrderChange\",\"userId\":\"6356450****001cef524\",\"channelType\":\"private\",\"data\":{\"symbol\":\"XBTUSDTM\",\"orderType\":\"limit\",\"side\":\"sell\",\"canceledSize\":\"0\",\"orderId\":\"440761625608192\",\"liquidity\":\"taker\",\"marginMode\":\"ISOLATED\",\"type\":\"match\",\"feeType\":\"takerFee\",\"orderTime\":1743146786640000000,\"size\":\"3840\",\"filledSize\":\"1116\",\"price\":\"84603.44\",\"matchPrice\":\"85739.69\",\"matchSize\":\"1000\",\"remainSize\":\"2724\",\"tradeId\":\"1740800012709\",\"tradeType\":\"liquid\",\"status\":\"match\",\"ts\":1743146786746000000}}";
    }

    public static String getFutureOrderMatchAdlMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"665d1df19c51ab0001029a49\",\"channelType\":\"private\",\"data\":{\"symbol\":\"10PEPEUSDTM\",\"orderType\":\"limit\",\"side\":\"sell\",\"canceledSize\":\"0\",\"orderId\":\"1961728417792\",\"positionSide\":\"BOTH\",\"liquidity\":\"taker\",\"marginMode\":\"ISOLATED\",\"type\":\"match\",\"feeType\":\"takerFee\",\"orderTime\":1750839892050000000,\"size\":\"100\",\"filledSize\":\"100\",\"price\":\"0.0000126\",\"matchPrice\":\"0.0000126\",\"matchSize\":\"100\",\"remainSize\":\"0\",\"tradeId\":\"1750839397535\",\"tradeType\":\"adl\",\"status\":\"match\",\"ts\":1750839892050000000}}";
    }

    public static String getFutureOrderFilledMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders:XBTUSDTM\",\"type\":\"message\",\"subject\":\"symbolOrderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"symbol\":\"XBTUSDTM\",\"orderType\":\"limit\",\"tradeType\":\"trade\",\"side\":\"buy\",\"canceledSize\":\"0\",\"orderId\":\"247899236673269761\",\"marginMode\":\"ISOLATED\",\"type\":\"filled\",\"orderTime\":1731916985768138917,\"size\":\"1\",\"filledSize\":\"1\",\"price\":\"91670\",\"remainSize\":\"0\",\"status\":\"done\",\"ts\":1731916996762000000}}";
    }

    public static String getFutureOrderFilledAdlMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders\",\"type\":\"message\",\"subject\":\"orderChange\",\"userId\":\"665d1df19c51ab0001029a49\",\"channelType\":\"private\",\"data\":{\"symbol\":\"10PEPEUSDTM\",\"orderType\":\"limit\",\"side\":\"sell\",\"canceledSize\":\"0\",\"orderId\":\"1961728417792\",\"positionSide\":\"BOTH\",\"marginMode\":\"ISOLATED\",\"type\":\"filled\",\"orderTime\":1750839892050000000,\"size\":\"100\",\"filledSize\":\"100\",\"price\":\"0.0000126\",\"remainSize\":\"0\",\"tradeType\":\"adl\",\"status\":\"done\",\"ts\":1750839892050000000}}";
    }

    public static String getFutureOrderCanceledMessage() {
        return "{\"topic\":\"/contractMarket/tradeOrders:XBTUSDTM\",\"type\":\"message\",\"subject\":\"symbolOrderChange\",\"userId\":\"633559791e1cbc0001f319bc\",\"channelType\":\"private\",\"data\":{\"symbol\":\"XBTUSDTM\",\"orderType\":\"limit\",\"tradeType\":\"trade\",\"side\":\"buy\",\"canceledSize\":\"1\",\"orderId\":\"247901211536203776\",\"marginMode\":\"ISOLATED\",\"type\":\"canceled\",\"orderTime\":1731917456611809239,\"size\":\"1\",\"filledSize\":\"0\",\"price\":\"90000\",\"remainSize\":\"0\",\"status\":\"done\",\"ts\":1731917460806000000}}";
    }

    /** Mock WS response: welcome (type welcome). */
    public static String getFutureTradeWelcomeJson() {
        return "{\"type\":\"welcome\",\"connectId\":\"mock-connect-id\",\"connectionId\":\"mock-conn-id\"}";
    }

    /** Mock WS response: pong (type pong). */
    public static String getFutureTradePongJson() {
        return "{\"type\":\"pong\",\"id\":\"ping-1\"}";
    }

    /** Mock WS response: order accepted (code 200000). Id is clientOrderId. */
    public static String getFutureTradeOrderAcceptedJson(String clientOrderId) {
        return "{\"code\":\"200000\",\"data\":{\"orderId\":\"403004610618449920\",\"clientOid\":\"" + clientOrderId + "\"},\"id\":\"" + clientOrderId + "\",\"op\":\"futures.order\"}";
    }

    /** Mock WS response: order rejected (code != 200000). */
    public static String getFutureTradeOrderRejectedJson(String clientOrderId, String reason) {
        return "{\"code\":\"400113\",\"msg\":\"" + (reason != null ? reason.replace("\"", "\\\"") : "Rejected") + "\",\"id\":\"" + clientOrderId + "\",\"op\":\"futures.order\"}";
    }

    /** Legacy: example uta.order request shape (for reference). */
    public static String wsResponse() {
        return "{\n" +
                "    \"id\": \"759ad5add86b4b09b35145a4d6f49488\",\n" +
                "    \"op\": \"uta.order\",\n" +
                "    \"args\": {\n" +
                "        \"clientOid\": \"3862959039974fa19bdc5e02a7d436b2c6fa3973\",\n" +
                "        \"tradeType\": \"FUTURES\",\n" +
                "        \"symbol\": \"TRUMPUSDTM\",\n" +
                "        \"leverage\": \"1\",\n" +
                "        \"type\": \"limit\",\n" +
                "        \"side\": \"SELL\",\n" +
                "        \"price\": \"8.440\",\n" +
                "        \"size\": \"1\",\n" +
                "        \"sizeUnit\": \"QUOTECCY\",\n" +
                "        \"timeInForce\": \"GTC\"\n" +
                "    }\n" +
                "}";
    }

  /* =========================================================
     ACCOUNTS
     ========================================================= */

    private static ExchangeSubscription getKuCoinFutureAccount() {

        ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(2);
        subscription.setExchange("KUCOIN");
        subscription.setApiUser("kucoinFutureUser");
        subscription.setApiKey(System.getenv("KUCOIN_API_KEY"));
        subscription.setApiSecret(System.getenv("KUCOIN_API_SECRET"));
        subscription.setPassphrase(System.getenv("KUCOIN_API_PASSPHRASE"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(true);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy(null);

        LOGGER.info("KuCoin Futures Account Configuration Loaded");
        return subscription;
    }

    public static ExchangeSubscription getKuCoinSpotAccount() {

        ExchangeSubscription subscription = new ExchangeSubscription();
        subscription.setId(2);
        subscription.setExchange("KUCOIN");
        subscription.setApiUser("kucoinSpotUser");
        subscription.setApiKey(System.getenv("KUCOIN_API_KEY"));
        subscription.setApiSecret(System.getenv("KUCOIN_API_SECRET"));
        subscription.setPassphrase(System.getenv("KUCOIN_API_PASSPHRASE"));
        subscription.setStatus(1);
        subscription.setCreated(1700000000000L);
        subscription.setExpires(1709999999999L);
        subscription.setFuturesEnabled(false);
        subscription.setLeverage(false);
        subscription.setLastUsedProxy(null);

        LOGGER.info("KuCoin Spot Account Configuration Loaded");
        return subscription;
    }
}
