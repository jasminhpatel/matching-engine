# Re-review: delisted symbols implementation (round 2)

- **Branch:** `internal/delisting_impl`
- **Reviewed:** the 3 commits after the first review (`55c5ee0f`):
  - `f915d6a2` delisting fixing
  - `320a068c` bybit delisting impl
  - `ea6c5c8a` mexc and bitget exchange implementation
- **Size:** 9 files, +495 / −87
- **Build:** `mvn -o compile` passes
- **Review date:** 2026-10-09
- **First review:** [delisting_impl-review.md](delisting_impl-review.md)

## Status of the round 1 findings

| # | Finding | Status | Notes |
|---|---------|--------|-------|
| 1 | Cache not read by anyone | **Fixed (differently)** | The cache now sets `ExternalSymbol.tradable=false` on the matching instruments, and `LiquidityOrderRouter` already skips instruments that aren't tradable. `isDelisted()` is still unused, which is fine. Open orders and positions on a delisted symbol are still not handled (see N5). |
| 2 | Bybit `Delivering` gap | **Fixed** | Added the `status=Delivering` query; Delivering and Closed perpetuals are treated as stopped. |
| 3 | Binance spot: schedule failure drops HALT/BREAK | **Fixed** | A failure or exception now keeps the HALT/BREAK pairs. Small leftover: the delist-schedule call is still signed; only the API key is needed. |
| 4 | REST refresh deletes WebSocket entries | **Fixed** | `PUSHED_AT` keeps a pushed entry for 60 minutes. Small leftover in N6. |
| 5 | REST and WebSocket paths disagree | **Fixed** | The `deliveryDate > 0` check is the same in both; logging is shared in `store()`. |
| 6 | Bybit spot detection lost on restart | **Fixed (partly)** | `ListedTracker` starts from the saved pairs, so delistings while the engine was down are caught. Announcements are logged, but upcoming spot dates still don't reach the cache. Acceptable. |
| 7 | Startup warning spam | **Fixed** | One summary line per segment on the first load. |
| 8 | `!contractInfo` subscribe unchecked | **Fixed** | Error reply is logged and the comment explains the 10-minute poll stays the main source. |
| 9 | One shared refresh thread | **Fixed** | One thread per segment. |
| 10 | `detectedAt` set after `put` | **Fixed** | |
| 11 | Hard-coded exchange names | **Fixed** | Uses `subscription.getExchange()` everywhere. |
| 12 | Mixed status values | **Fixed** | Added a `tradingDisabled` boolean set by each client; `status` is now for display only. |
| 13 | Commit format / tests | **Open** | Commit subjects still lack `feat:` / `fix:`. No tests, while the logic has grown (`ListedTracker`, `findExternalSymbols`, the grace period). |

## New implementation: Bitget and MEXC

Both use a shared `DelistedSymbolCache.ListedTracker`. A pair counts as delisted when it has a
stopped status, or when it was known before and has now disappeared from the exchange's list.
I checked the parsers against the live public endpoints today:

- **Bitget:**
  - `/api/v2/spot/public/symbols`: 3391 pairs (3384 `online`, 7 `halt`), and the `offTime`
    field exists.
  - `/api/v2/mix/market/contracts`: USDT has 816 contracts, all `normal`. USDC has 49, and its
    symbols are `BTCPERP` style. These match the saved `ExternalSymbol.symbol`, so the
    disappeared-pair comparison doesn't produce false removals today.
- **MEXC:** `/api/v3/exchangeInfo` returns 1907 pairs, all `status=1, tradeSideType=1`.
  `tradeSideType` comes back as a number; `asText()` handles that.

The structure is fine. Points below.

### Deribit

There is **no Deribit implementation** in these commits. `DeribitFastClient` and
`DeribitRestClient` are unchanged; `DeribitFastClient` only shows up in a search for "delist"
because `TradeListener` contains that string. Deribit falls back to the interface default (empty
list), so nothing is detected for it.

## New findings

### Medium

**N1. Partial restrictions block both sides.**
These are all treated as fully stopped, which sets `tradable=false` and blocks routing in both
directions:

- MEXC `tradeSideType` 2 (buy only) and 3 (sell only)
- Bitget futures `limit_open` (close only)

During a wind-down, close only / sell only is exactly the phase when we still need to exit
positions or hedges on that exchange. Decide whether the router should keep allowing the reducing
side. At minimum, keep these as a separate state from "trading stopped" so the decision can be
made later.

**N2. A race can leave an instrument disabled for good.**
`syncTradable()` and `restoreTradable()` can run at the same time for the same key: the REST
refresh thread and the Binance futures WebSocket thread through `remove()`. One possible order:

1. The sync thread sees `isTradable()==true`.
2. The restore thread removes the `DISABLED` list (nothing to restore yet).
3. The sync thread sets `false` and adds the instrument to a fresh `DISABLED` list.

The key is no longer in `DELISTED`, so nothing ever restores it. The instrument stays not
tradable until a restart. It's rare, but the effect is silent. Fix: synchronize sync/restore per
key, or do both inside `DISABLED.compute(key, ...)`.

**N3. The first `ListedTracker` load is compared against the saved pairs (risk of false
removals).**
On the first call, `known` is every saved pair for that exchange segment. That set comes from
the DB (all rows ever saved) plus the async exchange load. A saved pair whose stored symbol
doesn't match the exchange's current symbol string is reported `REMOVED`, and every instance with
that symbol is disabled. Cases where that can happen:

- old rows with a null `symbol`: `exchangeSymbol()` then falls back to base+quote, which is wrong
  for Bitget USDC `…PERP`
- renamed or re-based tickers
- MEXC pairs that are not enabled for API trading, since `exchangeInfo` only lists API-tradable
  pairs

Today the formats match, so this is a guard, not a confirmed bug. Suggestions:

- On the first run, log the `REMOVED` count separately.
- Consider skipping the disable for pairs that were never tradable in our own data.

A related wording point: the first load will mark every pair delisted in the past as `REMOVED`
(the DB is never cleaned up). That's correct, and actually useful because it disables stale DB
rows, but the "trading stopped: N" count in the first summary will be large and the per-instrument
`Not tradable while delisted` INFO lines will be numerous.

**N4. Unsafe read while the instruments are still loading.**
`waitForSavedSymbols()` calls `ExternalInstrumentCache.getAvailableSymbols()`. That copies a
plain `HashSet` (`EXCHANGE_SYMBOLS`) while `loadFromExchange()` is still adding to it on another
thread, which can throw `ConcurrentModificationException`. The `catch (RuntimeException)` then
clears `saved` and continues with **0 saved pairs**. Delistings while the engine was down are
silently missed for that run; only the INFO "starts from 0 saved pairs" line shows it. Retry on
the exception instead of clearing, or make `EXCHANGE_SYMBOLS` concurrent.

### Low

- **N5. Open orders and positions:** delisting now blocks new routing, but open orders and
  positions on a symbol that stops trading are not cancelled, closed or alerted. If that is
  intentionally out of scope, note it in the PR.
- **N6. REST can override a newer WebSocket push:** when REST reports a symbol, its state wins
  even if a WebSocket push in the last minutes was newer. Example: the push says `SETTLING` while
  REST still shows `TRADING` with a delivery date. Routing could then be re-enabled for up to one
  10-minute cycle. Binance REST is usually up to date, so this is low.
- **N7. Startup gap:** routing state is in memory only. After a restart, delisted pairs stay
  tradable until the first refresh finishes. For tracker-based segments that can take up to
  120 s because of `waitForSavedSymbols()`.
- **N8. Gap after the daily reload:** the daily `loadFromExchange()` adds new `ExternalSymbol`
  instances to `BASE_TO_EXTERNAL_SYMBOLS` (it appends without removing duplicates, as before this
  change). New instances for a delisted pair stay tradable until the next 10-minute
  `syncTradable()`. Short gap, low.
- **N9. Bybit announcements on restart:** every restart logs the last 7 days of spot delisting
  announcements again (`lastAnnouncementTime` is in memory). Harmless.
- **N10. Misleading comment:** the Bybit comment "old renamed contracts come back as PendingOpen
  with a past deliveryTime" is on the Closed/Delivering loop. The comment and the `deliveryTime <= now` check
  should say which query actually returns those entries.

## Suggested before merge

1. Fix the race between sync and restore (N2).
2. Decide how partial restrictions are handled (N1).
3. Make the first `ListedTracker` load safe: retry instead of clearing on the exception (N4) and
   guard against false removals (N3).
4. Either implement Deribit or drop it from the scope / PR description.
5. Add a few unit tests:
   - `ListedTracker`: first load, shrink guard, pair listed again
   - `apply()`: grace period
   - `findExternalSymbols`: multiplier and `PERP` symbols
6. Use `feat:` commit subjects (squash on merge is fine).
