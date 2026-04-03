package com.solfini.matchengine.liquidity.direct.aiGenerated;


public final class OrderStatus {
    public final String orderIdStr;
    public final String symbol;
    public final String status; // NEW / PARTIALLY_FILLED / FILLED / CANCELED / ...
    public final double filledQty;
    public final double lastFillQty;
    public final double lastFillPrice;

    public OrderStatus(final String oid, final String symbol, final String status, final double filled, final double lastQty,
                       final double lastPx) {
        this.orderIdStr = oid;
        this.symbol = symbol;
        this.status = status;
        this.filledQty = filled;
        this.lastFillQty = lastQty;
        this.lastFillPrice = lastPx;
    }
}
