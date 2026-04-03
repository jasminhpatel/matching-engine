package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.matchengine.LoggingThread;
import com.solfini.matchengine.liquidity.ExchangeSubscription;
import com.solfini.matchengine.liquidity.LiquiditySubscriptionCache;
import com.solfini.matchengine.liquidity.direct.aiGenerated.exchange.coinbase.CoinbaseUserDataListener;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.sbe.encoder.TimeInForce;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;

import java.io.IOException;
import java.util.Properties;

import static com.solfini.common.Constants.ADL_MAKER_ONLY;
import static com.solfini.matchengine.liquidity.direct.aiGenerated.CoinbaseFastClientTest.*;

public class CoinbaseMockTradeTest {

    final static String exchange = "COINBASE";
    private static final CustomLogger LOGGER = CustomLogger.getLogger(CoinbaseMockTradeTest.class);

    private static void init() throws IOException {

        final LoggingThread loggingThread = Context.getLoggingThread();
        new Thread(loggingThread, "loggingThread").start();
        Properties properties = new Properties();
        PoolSize.minimize(properties);
        properties.setProperty("EXECUTION_REPORT_POOL_QUEUE_CAPACITY", "100");
        properties.setProperty("EXECUTION_REPORT_POOL_START_CAPACITY", "50");
        properties.setProperty("INITIAL_USER_CACHE_SIZE", "128");
        PropertyReader.initialize(null, properties);

        final Instrument usd = new Instrument(1, "USD", "USD", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usd.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usd);

        final Instrument usdc = new Instrument(2, "USDC", "USDC", (short) 2, (short) 6, 1, 1000, 0,
                false, 1, Sector.NOT_DEFINED);
        usdc.setIndexFeedUsdMark(1);
        InstrumentCache.addInstrument(usdc);

        final User user = new User(100);
        UserCache.setTestUser(user);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        init();
        LiquiditySubscriptionCache.onLoad(getFutureAccount());

     //   LiquiditySubscriptionCache.startAllSubscriptions();

        Thread.sleep(1000);
        ExchangeSubscription subscription = LiquiditySubscriptionCache.get(exchange, true);
        CoinbaseUserDataListener userDataListener = new CoinbaseUserDataListener(
                getFutureAccount().getApiKey(), getFutureAccount().getApiSecret(), subscription);

        // Cache sample orders that will be referenced in WebSocket messages
        subscription.cacheNewOrder(getSampleOrder());
        subscription.cacheNewOrder(getSampleBtcOrder());

        LOGGER.info("========== COINBASE MOCK TRADE TEST ==========");

        // Test 2: Process position snapshot
        LOGGER.info(">>> Processing position snapshot...");
        userDataListener.onMessage(getPositionSnapshot());

      /*  // Test 3: Process order snapshot (open orders)
        LOGGER.info(">>> Processing order snapshot...");
        userDataListener.onMessage(getOrderSnapshot());

        // Test 4: Process partial fill
        LOGGER.info(">>> Processing partial fill...");
        userDataListener.onMessage(getOrderPartialFill());

        // Test 5: Process complete fill
        LOGGER.info(">>> Processing complete fill...");
        userDataListener.onMessage(getOrderUpdate());

        // Test 6: Process position update after fill
        LOGGER.info(">>> Processing position update...");
        userDataListener.onMessage(getPositionUpdate());

        // Test 7: Process order cancellation
        LOGGER.info(">>> Processing order cancellation...");
        userDataListener.onMessage(getOrderCancelled());

        // Test 8: Process rejected order
        LOGGER.info(">>> Processing rejected order...");
        userDataListener.onMessage(getOrderRejected());*/

        LOGGER.info("========== TEST COMPLETE ==========");

        printCache(subscription);
    }

    public static Order getSampleOrder() {
        Order order = new Order();
        order.setClOrdId("cb-order-2024011512345678");
        order.setOrderId(12345);
        order.setSymbol("XRP-PERP-INTX");
        order.setSide(Side.BUY);
        order.setType(Constants.BUY_LIMIT);
        order.setOrdType(OrdType.LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(24150, (short) 4); // $2.415
        order.setQty(100L, (short) 0); // 100 contracts
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);
        return order;
    }

    public static Order getSampleBtcOrder() {
        Order order = new Order();
        order.setClOrdId("cb-btc-order-2024011587654321");
        order.setOrderId(12346);
        order.setSymbol("BTC-PERP-INTX");
        order.setSide(Side.SELL);
        order.setType(Constants.SELL_LIMIT);
        order.setOrdType(OrdType.LIMIT);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        order.setPrice(9850000, (short) 2); // $98,500.00
        order.setQty(5, (short) 2); // 0.05 BTC
        order.setUser(UserCache.getTestUser());
        order.setTargetStrategy(ADL_MAKER_ONLY);
        return order;
    }

    /**
     * Simulates initial order snapshot from Coinbase WebSocket user channel
     * Contains multiple open orders across different perpetual futures products
     */
    public static String getOrderSnapshot() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:30:00.123456Z","sequence_num":1,"events":[{"type":"snapshot","orders":[{"order_id":"f47ac10b-58cc-4372-a567-0e02b2c3d479","client_order_id":"cb-order-2024011512345678","cumulative_quantity":"0","leaves_quantity":"100","avg_price":"0","total_fees":"0","status":"OPEN","product_id":"XRP-PERP-INTX","product_type":"FUTURE","creation_time":"2024-01-15T14:25:30.456Z","order_side":"BUY","order_type":"LIMIT","limit_price":"2.415","time_in_force":"GTC","post_only":false},{"order_id":"550e8400-e29b-41d4-a716-446655440001","client_order_id":"cb-btc-order-2024011587654321","cumulative_quantity":"0","leaves_quantity":"0.05","avg_price":"0","total_fees":"0","status":"PENDING","product_id":"BTC-PERP-INTX","product_type":"FUTURE","creation_time":"2024-01-15T14:28:15.789Z","order_side":"SELL","order_type":"LIMIT","limit_price":"98500.00","time_in_force":"GTC","post_only":true}]}]}
            """;
    }

    /**
     * Simulates partial fill update for XRP order - 50 of 100 contracts filled
     */
    public static String getOrderPartialFill() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:32:45.678901Z","sequence_num":2,"events":[{"type":"update","orders":[{"order_id":"f47ac10b-58cc-4372-a567-0e02b2c3d479","client_order_id":"cb-order-2024011512345678","cumulative_quantity":"50","leaves_quantity":"50","avg_price":"2.4125","total_fees":"0.060312","status":"OPEN","product_id":"XRP-PERP-INTX","product_type":"FUTURE","creation_time":"2024-01-15T14:25:30.456Z","order_side":"BUY","order_type":"LIMIT","limit_price":"2.415","filled_value":"120.625"}]}]}
            """;
    }

    /**
     * Simulates complete fill update for XRP order - all 100 contracts filled
     */
    public static String getOrderUpdate() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:35:12.345678Z","sequence_num":3,"events":[{"type":"update","orders":[{"order_id":"f47ac10b-58cc-4372-a567-0e02b2c3d479","client_order_id":"cb-order-2024011512345678","cumulative_quantity":"100","leaves_quantity":"0","avg_price":"2.4138","total_fees":"0.120690","status":"FILLED","product_id":"XRP-PERP-INTX","product_type":"FUTURE","creation_time":"2024-01-15T14:25:30.456Z","order_side":"BUY","order_type":"LIMIT","limit_price":"2.415","filled_value":"241.38","completion_percentage":"100"}]}]}
            """;
    }

    /**
     * Simulates BTC order being cancelled
     */
    public static String getOrderCancelled() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:40:00.000000Z","sequence_num":4,"events":[{"type":"update","orders":[{"order_id":"550e8400-e29b-41d4-a716-446655440001","client_order_id":"cb-btc-order-2024011587654321","cumulative_quantity":"0","leaves_quantity":"0","avg_price":"0","total_fees":"0","status":"CANCELLED","product_id":"BTC-PERP-INTX","product_type":"FUTURE","creation_time":"2024-01-15T14:28:15.789Z","order_side":"SELL","order_type":"LIMIT","cancel_reason":"USER_REQUESTED","reject_reason":""}]}]}
            """;
    }

    /**
     * Simulates futures balance summary from Coinbase WebSocket
     * Includes CFM USD balance, buying power, and margin info
     */
    public static String getFuturesBalanceUpdate() {
        return """
            {"channel":"futures_balance_summary","client_id":"","timestamp":"2024-01-15T14:30:00.000000Z","sequence_num":1,"events":[{"type":"snapshot","fcm_balance_summary":{"futures_buying_power":"47523.45","total_usd_balance":"52841.67","cbi_usd_balance":"0","cfm_usd_balance":"52841.67","total_open_orders_hold_amount":"5318.22","unrealized_pnl":"1247.89","daily_realized_pnl":"389.45","initial_margin":"4125.50","available_margin":"48716.17","liquidation_threshold":"2062.75","liquidation_buffer_amount":"46653.42","liquidation_buffer_percentage":"95.5"}}]}
            """;
    }

    /**
     * Simulates position update with multiple perpetual futures positions
     * Includes long XRP, short ETH, and long SOL positions
     */
    public static String getPositionUpdate() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:35:15.000000Z","sequence_num":5,"events":[{"type":"update","positions":{"perpetual_futures_positions":[{"product_id":"XRP-PERP-INTX","portfolio_uuid":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","position_side":"Long","net_size":"100","buy_order_size":"0","sell_order_size":"0","im_contribution":"241.38","unrealized_pnl":"3.62","mark_price":"2.4174","liquidation_price":"1.8521","leverage":"10","entry_vwap":"2.4138"},{"product_id":"ETH-PERP-INTX","portfolio_uuid":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","position_side":"Short","net_size":"0.5","buy_order_size":"0","sell_order_size":"0","im_contribution":"1625.75","unrealized_pnl":"-45.25","mark_price":"3342.00","liquidation_price":"3856.50","leverage":"10","entry_vwap":"3251.50"},{"product_id":"SOL-PERP-INTX","portfolio_uuid":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","position_side":"Long","net_size":"25","buy_order_size":"10","sell_order_size":"0","im_contribution":"2258.37","unrealized_pnl":"289.52","mark_price":"192.50","liquidation_price":"142.15","leverage":"10","entry_vwap":"180.92"}],"expiring_futures_positions":[]}}]}
            """;
    }

    /**
     * Simulates position snapshot at connection time
     */
    public static String getPositionSnapshot() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:30:00.000000Z","sequence_num":0,"events":[{"type":"snapshot","positions":{"perpetual_futures_positions":[{"product_id":"ETH-PERP-INTX","portfolio_uuid":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","position_side":"Short","net_size":"0.5","buy_order_size":"0","sell_order_size":"0","im_contribution":"1612.50","unrealized_pnl":"-32.00","mark_price":"3315.00","liquidation_price":"3831.23","leverage":"10","entry_vwap":"3251.50"},{"product_id":"SOL-PERP-INTX","portfolio_uuid":"a1b2c3d4-e5f6-7890-abcd-ef1234567890","position_side":"Long","net_size":"25","buy_order_size":"0","sell_order_size":"0","im_contribution":"2245.00","unrealized_pnl":"276.00","mark_price":"190.80","liquidation_price":"141.02","leverage":"10","entry_vwap":"180.92"}],"expiring_futures_positions":[]}}]}
            """;
    }

    /**
     * Simulates a rejected order response
     */
    public static String getOrderRejected() {
        return """
            {"channel":"user","client_id":"","timestamp":"2024-01-15T14:45:00.000000Z","sequence_num":6,"events":[{"type":"update","orders":[{"order_id":"6ba7b810-9dad-11d1-80b4-00c04fd430c8","client_order_id":"cb-rejected-order-123","cumulative_quantity":"0","leaves_quantity":"0","avg_price":"0","total_fees":"0","status":"REJECTED","product_id":"BTC-PERP-INTX","product_type":"FUTURE","creation_time":"2024-01-15T14:44:59.123Z","order_side":"BUY","order_type":"LIMIT","reject_reason":"INSUFFICIENT_MARGIN","reject_message":"Order rejected: Insufficient margin available for this order size"}]}]}
            """;
    }
}
