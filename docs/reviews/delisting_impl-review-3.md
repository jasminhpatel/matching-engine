# Final review: delisted symbols implementation (round 3)

- **Branch:** `internal/delisting_impl`
- **Reviewed:** the 2 commits after round 2 (`11c6d52b`):
  - `8923a0aa` deribit added and some improvements
  - `ad95f249` delisting impl for remaining exchanges
- **Size:** 19 files, +1316 / −58
- **Build:** `mvn -o compile` passes
- **Review date:** 2026-10-12
- **Earlier reviews:** [round 1](delisting_impl-review.md), [round 2](delisting_impl-review-2.md)

## Verdict

The core cache is now in good shape:

- the race between sync and restore is fixed
- partial restrictions are separated from full stops
- reading the saved pairs at startup is retried
- a hung request can no longer block a refresh cycle

Every fast client now implements `getDelistedSymbols()`.

**Not ready to merge yet.** One confirmed bug blocks BTC/USD on Kraken (H1). The same kind of
problem can block every saved spot pair on several other exchanges, depending on what the DB holds
(H2). Both come from the first "no longer listed" check matching saved symbols against a different
symbol format.

## Exchange coverage

| Exchange | Started by `LiquiditySubscriptionCache` | Delisting implemented | Live data check (2026-10-11/12) |
|----------|----|----|----|
| Binance | yes | spot + futures (+ `!contractInfo` WS) | round 1 |
| Bybit | yes | spot + linear perps | round 1–2 |
| MEXC | yes | spot (client is spot only) | round 2: 1907 pairs, parser matches |
| Bitget | yes | spot + USDT/USDC perps | round 2: parser matches |
| Deribit | yes | spot + perps | OK: `state` present on all 214 futures / 57 spot; perpetual expiration = 32503708800000 (year 3000) as coded; 1 spot `locked` |
| Hyperliquid | yes | perps (main + commodities dex) + spot | OK: 234 perps, 56 `isDelisted` |
| Kraken | yes | spot (+ instrument WS) + futures | spot statuses OK (1365 online, 78 cancel_only, 17 post_only) — **but see H1**. Futures endpoint not reachable from here, not verified |
| Coinbase | yes | spot (+ status WS) + perps | spot OK (917 online, 8 delisted). Perps: all 131 are `STANDARD` + `trading_disabled=true` (region), correctly not treated as stopped |
| KuCoin | yes | spot + perps + announcements log | OK: 968 spot all `enableTrading`; 690 `FFWCSX` Open; announcements endpoint works |
| HTX | not yet (fast client to be wired later) | spot + USDT swaps (+ `contract_info` WS) | OK: spot 2172 pairs (598 online, 1568 offline, 4 suspend); swaps 385 (380 status 1, 5 status 3 with `delivery_time`); WS init / ack / ping checked. See the **HTX** section |

Default `LIQUIDITY_EXCHANGE_PREFERENCE` is `bybit,binance,mexc,deribit,hyperliquid`, so those
five matter most unless prod config adds others.

## Status of the round 2 findings

| # | Finding | Status |
|---|---------|--------|
| N1 | Partial restrictions block both sides | **Fixed.** New `restricted` flag for MEXC buy/sell only, Bitget `limit_open`, Kraken `post_only`/`reduce_only`, Coinbase/Kraken futures `postOnly`; restricted pairs stay routed (see M3) |
| N2 | Race between sync and restore | **Fixed.** Both now run inside `DISABLED.compute()` and re-check `DELISTED` |
| N3 | False removals on the first check | **Partly fixed.** Futures seed only from tradable rows that have a symbol; a first-check count is logged. **Spot is still exposed → H1, H2** |
| N4 | Saved pairs read during the async load | **Fixed.** Retried 5 times; a warning is logged if it still fails |
| N5 | Open orders / positions on stopped symbols | Open (fine if out of scope; say so in the PR) |
| N6 | REST overriding a newer push | **Fixed.** A recent "stopped" push wins over a REST "trading" state for the grace period |
| N7–N9 | Startup gap, daily-reload gap, announcements repeated on restart | Unchanged, low |
| N10 | Misleading Bybit comment | **Fixed** |
| 13 | Tests / commit format | Open: still no tests, commit subjects still lack `feat:` |

## New findings

### High

**H1. Kraken: BTC/USD (and 19 other pairs) is falsely marked REMOVED and blocked on every
restart. Confirmed.**

- `XKrakenExchange` saves the spot symbol as **`altname`**, with the pair key in `prompt`.
- `exchangeSymbol()` uses `symbol` first, so the saved key is the altname, e.g. `XBTUSD`.
- `KrakenRestClient.getSpotDelistedSymbols()` fills `listed` with the **pair key** (`XXBTZUSD`)
  and the **normalized base+quote** (`BTCUSD`). It never uses the altname.

Live AssetPairs today has 20 pairs whose altname is neither of those. Three of them have USD
quotes: `XBTUSD`, `EURUSD`, `GBPUSD`.

What happens on the first check:

1. The saved `XBTUSD` row (tradable, has a symbol) goes into `known`.
2. `XBTUSD` isn't in `listed`, so it is reported `REMOVED`.
3. `findExternalSymbols` matches the BTC/USD instrument by `XBTUSD` and sets it not tradable.
4. `removeAll(listed.keySet())` never clears it, so it stays blocked until restart, and the same
   thing happens again on every restart.

It also fails the other way: a `cancel_only` legacy pair is stored under its key and base+quote,
neither of which matches the saved altname, so it is **never** blocked.

**Fix:** key Kraken spot by `altname`, in `listed`, `spotStates()` and `pairKeyByBaseQuote`. Or
make `findExternalSymbols` / the tracker also match on `prompt`.

**H2. Spot seeding can block every saved pair on exchanges whose rows only come from the DB.
Needs a DB check.**

`ListedTracker` seeds futures only from rows that have a symbol, but seeds **spot from any
tradable row**. A row with a null `symbol` falls back to base+quote (`BTCUSDT`). If the exchange's
live symbol has a different format, the seeded symbol is never in `listed`. Every such pair is
then reported `REMOVED` and **disabled**, because `findExternalSymbols` matches the same fallback.
The live formats are:

- Coinbase `BTC-USD`
- KuCoin `BTC-USDT`
- HTX `btcusdt`: lowercase. Fine for rows saved by `HtxRestClient.getExchangeInstrumentsFull()`, which keeps HTX's
  spelling, but a null or upper-case saved symbol won't match, because the tracker's map lookup is case-sensitive
- Hyperliquid spot: token-name based

Coinbase, KuCoin, Hyperliquid and HTX are not in `ExternalExchangeUtil.EXCHANGES`, so
their rows are never refreshed by `loadFromExchange()`. Whatever is in `external_instrument_state`
for them is what the first check compares against.

Check the DB before merging (read-only):

```sql
SELECT exchange, isfutures, count(*) AS total_rows,
       count(*) FILTER (WHERE symbol IS NULL OR symbol = '') AS no_symbol,
       min(symbol) AS sample_symbol
FROM external_instrument_state
WHERE tradable = true
GROUP BY exchange, isfutures
ORDER BY exchange, isfutures;
```

**Fix, whatever the DB shows:** treat spot the same way as futures, and only seed rows that have a
symbol. Also compare symbols case-insensitively / normalized in `ListedTracker`. Alternatively,
skip disabling a `REMOVED` pair when its base+quote is still listed under another spelling.

### Medium

**M1. `refreshWithTimeout` leaks one thread per hung request.**
The timeout stops *waiting*, but the worker keeps running. It isn't cancelled
(`future.cancel(true)` is never called), and per the code's own comment `HttpUtils` has no timeout.
`REFRESH_WORKERS` is an unbounded cached pool, so each cycle that hangs leaves one more thread
blocked for good. Also, an abandoned refresh still changes `ListedTracker` state even though its
result is dropped.

**Fix:** add real HTTP connect/read timeouts, call `future.cancel(true)` on timeout, and use a
bounded pool.

**M2. HTX is not started yet.**
`HtxFastClient` is not constructed anywhere yet (no `"HTX"` branch in
`LiquiditySubscriptionCache.startAllSubscriptions()`). The plan is to wire it up later, so the
delisting code is reviewed on its own below; it starts working once the fast client is
started.

**M3. Restricted pairs are routed on both sides.**
`restricted` only stops the instrument from being disabled. `LiquidityOrderRouter` doesn't check
which side is allowed, so orders on the blocked side (a buy on a sell-only MEXC pair, an opening
order on Bitget `limit_open` or Kraken `reduce_only`) go out and get rejected by the exchange.
Acceptable for now, but a side check in the router (via `DelistedSymbolCache.get(...)`) would avoid
the rejects.

### Low

- **L1. The Coinbase futures status stream does nothing.** I tested the public `status` channel
  with `BTC-PERP-INTX`, `ETH-PERP-INTX` and `BTC-USD`; the snapshot only contained the SPOT product.
  The perps client keeps a connection open for no updates. Only start it for spot.
- **L2. The new streams skip the network setup the other listeners use.** The Coinbase, Kraken and
  HTX streams use `java.net.http` directly. They skip `Context.getOutboundIp()` and the proxy
  settings the Netty listeners use, and build a new `HttpClient` on every reconnect. Check that prod
  egress allows it.
- **L3. One bad Kraken futures date fails the whole refresh.**
  `Instant.parse(lastTradingTime)` has no try/catch inside the loop, so one bad value fails the
  whole Kraken futures refresh.
- **L4. Kraken `limit_only` is treated as trading.** If the router ever sends market orders there,
  they will be rejected.
- **L5. Copy trade isn't covered.** `DelistedSymbolCache.start()` is only called from fast
  clients started by liquidity subscriptions. A copy-trade-only deployment gets no delisting
  refresh at all.
- **L6. Still no tests.** Some comments read as if they were written for tests
  ("test / caller limit reached"), but no test files are in the diff. The pure parsers
  (`toState`, `toFuturesState`, `spotStates`, `toDelistedSymbols`, `ListedTracker`) are easy to
  cover with saved JSON.

## HTX delisting implementation

Reviewed on its own, because the fast client will be wired up later (M2). Files:
`HtxRestClient` (`getSpotDelistedSymbols`, `getFuturesDelistedSymbols`, `toDelistedSymbols`,
`toSpotState`, `toFuturesState`) and `HtxFastClient` (`getDelistedSymbols`, the `contract_info`
stream).

### Checked against live data (2026-10-12)

- **Spot `GET api.huobi.pro/v1/common/symbols`:** works.
  - 2172 pairs: 598 `online`, 1568 `offline`, 4 `suspend`, 2 `offline` + `api-trading=disabled`.
  - The fields the code reads (`symbol`, `base-currency`, `quote-currency`, `state`,
    `api-trading`) are all present. Symbols are lowercase (`bonkusdt`).
- **Swaps `GET api.hbdm.com/linear-swap-api/v1/swap_contract_info`:** works.
  - 385 contracts, all `business_type=swap`: 380 `contract_status=1` with
    `delivery_time=""`, and 5 `contract_status=3` (suspended) with a `delivery_time`.
  - Example: `WBD-USDT`, delivery 2026-10-12 08:00 UTC.
  - `asLong(0)` on `""` gives 0, so "no date" is handled.
- **WebSocket `wss://api.hbdm.com/linear-swap-notification`, topic `public.*.contract_info`:**
  works as the code expects.
  - It sends an `init` snapshot of all contracts (~141 KB, gzip) and the ack
    `{"op":"sub",...,"err-code":0}`. The ack is not treated as an error, which is correct.
  - It then pings every few seconds; the `ping`→`pong` text replace keeps the connection open.
  - Item fields match `toFuturesState` (`contract_code`, `contract_status`, `delivery_time`,
    `business_type`).

### What's good

- REST and the stream share one classifier (`toFuturesState`), so they can't disagree.
- Dated futures are skipped but still counted as listed, so their saved rows aren't marked removed.
- The stream only applies contracts the latest REST check lists, which ignores the old hidden
  contracts in the snapshot.
- A "trading again" push doesn't remove a `REMOVED` entry.
- A failed REST request returns `null`, so the last known state is kept.

### Findings

**HX1 (Medium). `stop()` doesn't stop the contract_info stream.**
`runContractInfoStream(Long.MAX_VALUE)` blocks on `closed.get(...)` until HTX closes the socket.
`stop()` only sets `contractInfoRunning = false`, which is checked *after* that call returns. The
socket keeps running and keeps writing to `DelistedSymbolCache` after the client is stopped. If the
client is started again, a second stream runs alongside the first.

Fix:
- Keep the `WebSocket` in a field.
- Call `abort()` on it in `stop()`, and interrupt the thread.

The Kraken instrument stream has the same pattern. Coinbase's ends within an hour because it
reconnects hourly.

**HX2 (Low). The init snapshot can be lost at startup.**
`DelistedSymbolCache.start()` and `startContractInfoStream()` start together. If the `init`
snapshot arrives before the first REST check finishes, `listedContracts` is still empty and the
whole snapshot is skipped. It isn't sent again until a reconnect, which can be days later. REST
covers the same data within 10 minutes, so the effect is small. Fix: start the stream after the
first successful refresh, or keep the snapshot and apply it once `listedContracts` is filled.

**HX3 (Low). Spot: 1568 long-offline pairs are stored as stopped on every refresh.**
`/v1/common/symbols` still returns pairs delisted years ago with `state=offline`. Every refresh
stores about 1570 stopped entries and runs `findExternalSymbols` for each. The first-load summary
will say `trading stopped: ~1574`. That's harmless and correct, but noisy and wasted work. Consider
storing an `offline` pair only when we have a saved instrument for it.

**HX4 (Low). Spot: no advance delisting date.**
The spot endpoint has no delisting time, so a spot delisting is only seen once `state` changes.
Bybit and KuCoin log delisting announcements; HTX could do the same. Optional.

**HX5 (Low). Same seeding caveat as H2.**
Rows saved by `getExchangeInstrumentsFull()` keep HTX's spelling (spot `btcusdt`, swaps
`BTC-USDT`), so they match. A row with a null or upper-case symbol would be falsely reported
`REMOVED`. The H2 fix (only seed rows that have a symbol, compare case-insensitively) covers this.

**HX6 (Low). Legacy endpoint.**
`/v1/common/symbols` is HTX's older symbols endpoint (the newer one is
`/v2/settings/common/symbols`). It works today; keep an eye on it if HTX retires v1.

**Notes, no change needed:**
- Status 2 (pending listing) counts as trading.
- Suspended contracts with a past `delivery_time` (e.g. `CYBER-USDT`, suspended since
  2026-01-08) are correctly stopped.
- `pre-online` spot pairs count as new listings.

## Before merge

1. Fix Kraken by keying on `altname` (H1).
2. Run the SQL above, and limit spot seeding to rows that have a symbol, compared
   case-insensitively (H2).
3. Add HTTP timeouts and cancellation for refresh workers (M1).
4. HTX: fix HX1 (stop() leaves the stream running) before the fast client is wired up.
5. Add parser and `ListedTracker` tests, at least for Kraken, Coinbase and Hyperliquid, using saved
   responses.
6. Use `feat:` commit subjects (squash on merge is fine).
