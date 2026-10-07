# Review: delisted symbols implementation

- **Branch:** `internal/delisting_impl` (jasminhpatel/matching-engine)
- **Commit:** `8dc9d4ac` — "delisted symbols implementation" (on top of `cfff8a06`)
- **Scope:** 9 files, +657 lines
- **Build:** `mvn -o compile` passes
- **Review date:** 2026-10-08

## Summary

The structure is sound: each client polls its exchange every 10 minutes into a shared static
`DelistedSymbolCache`. A failed request returns `null`, so the cache keeps the last known state.
The Binance futures `!contractInfo` WebSocket push adds near-real-time updates. The issues below
are mostly about missed states, the REST and WebSocket paths disagreeing, and the cache not being
used yet.

## High

### 1. Nothing reads the cache, so the feature currently only logs

Outside `DelistedSymbolCache` itself, no code calls `isDelisted()`, `get()` or `getAll()`.
Order routing still sends orders to a delisted Binance or Bybit symbol.

- If logging only is the goal for this step, say so in the commit or PR.
- If the goal is to block delisted symbols, the important part is still missing: a check in the
  router / `LiquidityOrderBook`, plus a decision on what happens to open orders and positions.

### 2. Bybit futures: a perpetual drops out of the cache exactly when it starts delisting

`BybitRestClient.getFuturesDelistedSymbols()` makes two queries:

- the default `instruments-info?category=linear`, which returns **Trading only**
- `status=Closed`

Bybit also has a `Delivering` status for linear contracts. The public API accepts it as a filter
(it returns an empty list today, so the gap only shows during an actual delisting). When a
perpetual moves from Trading to Delivering, it appears in neither query. `refresh()` then removes
it and logs "No longer delisted or suspended" right as trading stops. It reappears only once it
reaches `Closed`.

**Fix:** also query `category=linear&status=Delivering`.

The comment `// Trading + PendingOpen` on the default query is also wrong: it returns Trading only.

### 3. Binance spot: one failed call throws away all the spot results

In `BinanceRestClient.getSpotDelistedSymbols()`, a failure of `/sapi/v1/spot/delist-schedule`
returns `null`. That happens with an IP-restricted key, a proxy mismatch, or a rate limit. The
`null` discards the HALT/BREAK results already fetched, and the spot segment never updates.

**Fix:** keep the HALT/BREAK list and only log the schedule failure.

That endpoint only needs the `X-MBX-APIKEY` header (security type MARKET_DATA), so the
timestamp/signature isn't needed.

## Medium

### 4. The REST refresh deletes entries the WebSocket just added

`DelistedSymbolCache.refresh()` removes every key in the segment that isn't in the REST snapshot.
A symbol added by a `!contractInfo` push is removed on the next poll if REST doesn't report it
yet. A later push adds it back, and each flip logs a warning.

### 5. The REST and WebSocket paths treat the same data differently

- **Binance `deliveryDate` check:**
  - The WebSocket code checks `deliveryDate > 0 && != PERPETUAL_NO_DELIVERY_DATE`.
  - The REST code checks only `!= PERPETUAL_NO_DELIVERY_DATE`, so a missing or zero
    `deliveryDate` counts as "delisting scheduled".
- **Logging:** `update()` logs "Trading stopped" when there is no previous entry; `refresh()` only
  logs it when there is one.
- **Duplication:** the change-detection and logging code is copied in `refresh()` and `update()`.
  Move it into one helper.

### 6. Bybit spot detection is lost on every restart

Spot detection compares the current pair list with the previous poll, kept in memory, so:

- Pairs delisted while the engine was down are never caught.
- The cache stays empty until something changes after startup.
- Upcoming spot delistings are never known in advance.

Consider Bybit's announcements endpoint (`/v5/announcements/index`, tag *Delistings*) for advance
dates, with the list comparison kept as a fallback.

### 7. Every restart repeats all the "Delisting scheduled" warnings

On the first load every future delisting counts as new and logs a warning. That's harmless but
noisy; consider a single summary line on the first load.

### 8. The `!contractInfo` subscribe has no error handling

The subscribe is sent on the private user-data socket (`wss://fstream.binance.com/ws/<listenKey>`).
It should work, and since it lives in `connect()` it is sent again on reconnect. But:

- Nothing checks the `{"result":null,"id":1}` reply, so a rejected subscribe would go unnoticed.
- Binance documents this stream as pushing on listing, settlement and bracket changes. It may not
  fire when a delisting is announced, so the 10-minute poll stays the main source. The comment
  should say this.

## Low / style

9. **Shared refresh thread:** all exchanges refresh on one thread. A slow Bybit fetch (up to 20
   pages, each with a proxy timeout) delays Binance's refresh.
10. **`detectedAt` set after insert:** it is set on the new object after `DELISTED.put(...)`, so
    other threads can briefly see it as "now". Copy it before the `put`.
11. **Hard-coded exchange names:** the clients hard-code `"BINANCE"` / `"BYBIT"`, while the cleanup
    prefix uses `subscription.getExchange()`. This works today because subscription names match and
    keys are lowercased, but the two can drift. Pass `subscription.getExchange()` through instead.
12. **Status values:** `DelistedSymbol.status` mixes Binance values (`BREAK`, `SETTLING`), Bybit
    values (`Closed`) and a made-up `REMOVED`. An enum (or a boolean `tradingDisabled` plus the
    raw status kept for display) would be easier for callers to use.
13. **Commit and tests:**
    - The commit subject doesn't follow the `feat:` / `fix:` convention.
    - There are no tests. The parsers (Binance `deliveryDate` / `contractType`, Bybit
      `Closed` / `Delivering` / `deliveryTime`) are easy to unit-test against saved JSON responses.

## Suggested order before merging

1. Decide whether this step is logging only or should block routing (#1).
2. Add the Bybit `Delivering` query (#2).
3. Make a schedule failure keep the HALT/BREAK list (#3).
4. Stop the REST refresh from removing WebSocket entries (#4) and make the two paths match (#5).
