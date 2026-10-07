package com.solfini.matchengine.liquidity.direct.aiGenerated;

import com.solfini.matchengine.executionexchange.DelistedSymbol;
import com.solfini.matchengine.executionexchange.ExternalSymbol;
import com.solfini.matchengine.liquidity.Ticker;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import java.util.Collections;
import java.util.List;

public interface ExternalExchangeClient {

  void start();

  void stop();

  ExecutionReportMessage sendOrder(final Order order, final boolean futuresEnabled,
      final String bestQuoteSymbol, final String baseSymbol,
      final int priceScale, final int qtyScale, final double fxRate) throws Exception;

  boolean cancelOrder(final Order order, final boolean isSpotOrder);

  default List<ExternalSymbol> getExchangeInstrumentsFull() {
    throw new UnsupportedOperationException(
        "This exchange client does not support full instrument data");
  }

  /**
   * Returns the symbols of this client's segment (spot or futures) that are delisted, suspended,
   * or scheduled for delisting.
   *
   * @return the delisted symbols, an empty list when the exchange does not support it,
   *         or {@code null} when the exchange could not be queried
   */
  default List<DelistedSymbol> getDelistedSymbols() {
    return Collections.emptyList();
  }

  /**
   *
   * @param base e.g. BTC
   * @param quote e.g. USDC
   * @param instrumentType 1- Perps 2- Spot (should match Tardis definition
   * @return Ticker
   */
  default Ticker getTicker(final String base, final String quote, final int instrumentType) {
       throw new UnsupportedOperationException(
            "This exchange client does not support full ticker data");
  }

  /**
   * Returns the available balance for the specified asset.
   *
   * <p>The returned value represents the free/available balance,
   * excluding any amounts reserved for open orders or margin.
   *
   * @param asset the asset ticker to query (e.g. {@code "BTC"}, {@code "USDT"})
   * @return the available balance as a {@code double}
   * @throws UnsupportedOperationException if the exchange client does not support
   *                                        balance retrieval
   */
  default double getBalance(final String asset) {
    throw new UnsupportedOperationException(
        "This exchange client does not support get balance");
  }

  /**
   * Returns the current position size for the specified instrument.
   *
   * <p>A positive value indicates a long position; a negative value indicates
   * a short position. Returns {@code 0.0} if no position is currently open.
   *
   * @param symbol the instrument or market symbol to query (e.g. {@code "BTC-PERP"}, {@code "BTCUSDT"})
   * @return the current position size as a {@code double}
   * @throws UnsupportedOperationException if the exchange client does not support
   *                                        position retrieval
   */
  default double getPosition(final String symbol) {
    throw new UnsupportedOperationException(
        "This exchange client does not support get position");
  }

  /**
   * Retrieves the current state of an order by its client order ID or exchange order ID.
   *
   * <p>Implementations should prefer {@code clOrdId} for lookup where supported by the
   * exchange. {@code orderId} serves as a fallback for exchanges that index orders by
   * their own assigned ID rather than the client-assigned ID.
   *
   * <p>The returned {@link ExecutionReportMessage} contains the full order snapshot,
   * including status, filled quantity, remaining quantity, and average fill price.
   *
   * @param clOrdId the client-assigned order ID used when the order was submitted;
   *                may be {@code null} if only the exchange order ID is known
   * @param orderId the exchange-assigned order ID returned after order acknowledgement;
   *                may be {@code null} if only the client order ID is known
   * @return an {@link ExecutionReportMessage} representing the current state of the order
   * @throws UnsupportedOperationException if the exchange client does not support
   *                                        order retrieval
   * @throws IllegalArgumentException      if both {@code clOrdId} and {@code orderId}
   *                                        are {@code null}
   *
   */
  default ExecutionReportMessage getOrder(final String clOrdId, final String orderId) {
    throw new UnsupportedOperationException(
        "This exchange client does not support get order");
  }

}
