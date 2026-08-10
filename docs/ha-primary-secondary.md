# Matching Engine HA: Primary / Secondary (ZooKeeper)

This document describes how the Solfini matching engine runs in production as a primary/secondary pair (`me01` / `me02`), how state is rebuilt on startup, how automatic failover works via ZooKeeper, and what operations should do when a server fails or a new release is deployed.

**Audience:** developers and operations.  
**Scope:** production (`me01` / `me02`) with `CONTROLLER_TYPE=zookeeper`.

---

## 1. Overview

At any time there is at most **one primary** matching engine. A second instance runs as **secondary** (DR mode) and stays in sync with the primary by consuming the primary’s output.

| Role | Instance (typical) | Market status | Consumes | Publishes | Matching |
|------|--------------------|---------------|----------|-----------|----------|
| **Primary** | Higher ZK priority (e.g. `me01`) | `OPEN` | API input topic | Primary output topic | Live matching, risk, MD, liquidity |
| **Secondary** | Lower ZK priority (e.g. `me02`) | `DR_MODE` | Primary output topic | Secondary output topic | Rebuilds / applies state only — no live matching |

Leadership is decided by **ZooKeeper**: instances compete for an ephemeral primary lock. The winner becomes primary; the other runs as secondary. If the primary dies, its lock disappears and the secondary automatically acquires the lock and promotes itself.

Downstream consumers of engine topics do **not** need operational action on failover.

---

## 2. Production topology

| Item | Primary                                                       | Secondary |
|------|---------------------------------------------------------------|-----------|
| Instance ID | `me01`                                                        | `me02` |
| Role preference | Higher `CONTROLLER_ZOOKEEPER_PRIORITY`                        | Lower priority |
| Snap directory | `CHRONICLE_ENGINE_SNAP_DIRECTORY` (shared, e.g. `/data/snap`) | Same shared path |
| Controller | `CONTROLLER_TYPE=zookeeper`                                   | Same |

Both instances share:

- ZooKeeper (election / primary lock)
- Kafka (API input, primary output, secondary output, control topic)
- Snapshot filesystem (`CHRONICLE_ENGINE_SNAP_DIRECTORY`)

Relevant config keys (see each host’s `config.properties`):

| Property | Purpose                                                                                     |
|----------|---------------------------------------------------------------------------------------------|
| `INSTANCE_ID` | Unique instance name (`me01` / `me02`). Must be unique.                                     |
| `CONTROLLER_TYPE` | Must be `zookeeper` in production                                                           |
| `CONTROLLER_ZOOKEEPER_LOCATION` | ZooKeeper connect string                                                                    |
| `CONTROLLER_ZOOKEEPER_PRIORITY` | Election priority (higher wins when no primary exists)                                      |
| `CONTROLLER_ZOOKEEPER_SESSION_TIMEOUT` | ZK session timeout in seconds (default `120`). Ephemeral nodes disappear after session loss |
| `ME_KAFKA_TOPIC_PRIMARY` | Primary output (also secondary’s DR input)                                                  |
| `ME_KAFKA_TOPIC_SECONDARY` | Secondary output                                                                            |
| `API_KAFKA_TOPIC_IN` | Live order/admin input (primary only)                                                       |
| `LOAD_FROM_SNAP` | Snapshot id to load on start (Start script uses the latest available snap folder)           |
| `LOAD_FROM_SNAP_AND_REPLAY` | Typically `SELECTED_INPUT` — replay selected primary output after snap                      |
| `CHRONICLE_ENGINE_SNAP_DIRECTORY` | Where snapshots are written/read                                                            |

> Sample configs under `config/prod` in the repo may show other controller types used for test environments. **Production uses ZooKeeper election** (`CONTROLLER_TYPE=zookeeper`).

---

## 3. How ZooKeeper election works

Implemented by `ZooKeeperController`.

### ZooKeeper nodes (root: `/com.solfini.matchengine.automatic`)

| Path | Type | Purpose |
|------|------|---------|
| `/com.solfini.matchengine.automatic` | Persistent | Root |
| `.../instances` | Persistent | Parent for live instances |
| `.../instances/{INSTANCE_ID}` | **Ephemeral** | Instance liveness; data = priority |
| `.../primary` | **Ephemeral** | Primary lock; data = instance id of current primary |
| `.../last_primary` | Persistent | Last successful primary (split-brain guard) |

### Election rules

1. If `/primary` already exists → this instance is **not** primary.
2. Otherwise, among live instance nodes, the instance with the **highest priority** tries to create `/primary`.
3. Creating the ephemeral `/primary` node wins the race → that instance becomes primary.
4. Losers (or instances that see an existing primary) switch to **secondary**.

The controller loop retries roughly every ~900ms while not primary.

### What happens when primary dies

1. Primary process/host dies or loses its ZooKeeper session.
2. Ephemeral nodes (`/primary` and the instance node) are deleted after session timeout.
3. Secondary sees no primary lock, acquires `/primary`, and calls `switchMode(PRIMARY)`.
4. Engine promotes: stops DR listening, switches publish topic, starts input/risk, etc. (see §6).

### What happens when the failed server comes back

1. The restarted instance registers its ephemeral instance node.
2. `/primary` is already held by the current primary → election fails.
3. The returning instance starts (or continues) as **secondary**: loads snap, replays, then follows primary output.

**Never run two processes with the same `INSTANCE_ID`.** A duplicate id causes the new process to exit.

### Split-brain protection

If a primary briefly loses ZooKeeper and another instance becomes primary in the meantime, the original primary **exits** rather than risk two writers. That is intentional.

---

## 4. Startup and state rebuild

Both primary and secondary **rebuild state before processing live traffic** (except cold start). Flow is driven by `MatchEngineStarter` → `ZooKeeperController` (mode) → `ControllerThread` (warm start / failover actions).

### 4.1 Common rebuild steps

1. Load the configured snapshot from `CHRONICLE_ENGINE_SNAP_DIRECTORY/{LOAD_FROM_SNAP}` (Chronicle queue; wait for `done` marker).
2. With `LOAD_FROM_SNAP_AND_REPLAY=OUTPUT`, replay messages from the **primary output** Kafka topic after the snapshot’s recorded offset, applying execution/position/admin state into the matcher.
3. Then enter steady-state for the elected role.

Snapshot id can also be overridden at start with `--snapshot <id>`.

### 4.2 Elected as primary (cold path from `NONE` → `PRIMARY`)

Typical warm start (not `--warm-start`):

1. Temporarily run rebuild under DR-style mode so live matching does not start mid-replay.
2. Restore from snapshot + replay primary output.
3. Switch to primary (`OPEN`), publish on the primary output topic continuing sequence from replay.
4. Start risk, market data, pricing, liquidity (if enabled), and **Kafka input** listener.
5. Log `>>> READY <<<` when live.

So even a brand-new primary always rebuilds from snap + remaining output before accepting input.

### 4.3 Elected as secondary (`NONE` → `SECONDARY`)

1. Initialize primary + DR object pools.
2. Publish to the **secondary** output topic.
3. Restore from snapshot + replay primary output (same shared snap store).
4. Start `KafkaDRFixListener` on the primary output topic from the catch-up offset (or wait-for-snap if no snap was loaded).
5. Stay in `DR_MODE`: apply primary’s published state; **do not** match live orders from the API input topic.
6. Log `>>> READY <<<`.

---

## 5. How secondary stays in sync

```
API / risk / matching (primary)
        │
        ▼
 ME_KAFKA_TOPIC_PRIMARY  ──────────────────────────────┐
        │                                              │
        ▼                                              ▼
 Primary publishers                          KafkaDRFixListener (secondary)
                                                      │
                                                      ▼
                                              Rebuild books / positions
                                                      │
                                                      ▼
                                         ME_KAFKA_TOPIC_SECONDARY
```

While secondary:

- Reads primary output (execution reports, position reports, relevant admin).
- Updates local books/state without matching.
- Optionally publishes a DR mirror stream on the secondary topic.

When primary is healthy, secondary is a hot standby ready to promote.

---

## 6. Failover sequence (automatic)

When ZooKeeper elects a secondary to primary, `ControllerThread` promotes as follows:

1. Publish a `FAILOVER` admin marker on the **primary output** topic (fence for the DR stream).
2. Stop the DR listener after draining through that offset; capture last output sequence.
3. Stop AssetGroup compaction listener (primary will own AssetGroups).
4. Switch mode secondary → primary (`DR_MODE` → `OPEN`, including `DR_TO_OPEN` book transition).
5. Switch publisher to the **primary** output topic, continuing sequence from the last DR sequence.
6. Start risk / MD / pricing / liquidity and the **API input** listener.
7. Log `>>> READY <<<` as the new primary.

No manual `mectrl --primary` is required for ZooKeeper production failover.

---

## 7. Snapshots

Snapshots persist full engine state (instruments, users, books, positions, ids, Kafka offsets) to Chronicle under `CHRONICLE_ENGINE_SNAP_DIRECTORY/{snapId}/`, completed when a `done` file appears.

**Production:** each engine host has a **cron job** that triggers snapshot creation. Snap path is whatever is configured in that host’s config (`CHRONICLE_ENGINE_SNAP_DIRECTORY`).

Manual trigger (if needed):

```bash
./scripts/mectrl.sh -c <config.properties> -i me01 --snapshot
# or target current primary:
./scripts/mectrl.sh -c <config.properties> -i primary --snapshot
```

After a snapshot completes, update / use the new snap id for subsequent restarts (`LOAD_FROM_SNAP` or `--snapshot`).

---

## 8. Operations runbooks

### 8.1 Primary server goes down

**Expected automatic behavior**

1. ZooKeeper session expires → primary lock released.
2. `me02` (secondary) acquires the lock and promotes to primary.
3. Trading continues on the new primary after promote completes (`>>> READY <<<`).

**Ops actions**

1. Confirm secondary logs show primary lock acquired and `>>> READY <<<`.
2. Investigate / repair the failed host.
3. When bringing the failed host back:
   - Ensure it starts with a **recent** `LOAD_FROM_SNAP` (or `--snapshot`) and `LOAD_FROM_SNAP_AND_REPLAY=SELECTED_INPUT`.
   - Start the process; with ZooKeeper it will **lose** the primary election and run as **secondary**.
4. Confirm both instances are up: one primary, one secondary.
5. Optional: after failover is stable, take a fresh snapshot from the new primary for a clean restart point.

**Do not** start a second primary manually. Let ZooKeeper elect.

### 8.2 Secondary server goes down

**Expected behavior**

- Primary keeps running; no failover.

**Ops actions**

1. Repair / restart the secondary host.
2. Start with recent snap + output replay so it catches up via DR listener.
3. Confirm `>>> READY <<<` and that it remains secondary while primary holds the lock.

### 8.3 Rolling release deploy

Preferred pattern: **rolling** — keep one engine serving while upgrading the other.

**High-level steps**

1. **Upgrade secondary first**
   - Gracefully stop secondary (`mectrl --shutdown` or host stop script).
   - Deploy new JARs / config on the secondary host.
   - Start secondary with current snap + replay.
   - Wait for `>>> READY <<<` and confirm it is following primary output.

2. **Fail over to upgraded secondary**
   - Gracefully shut down the old primary.
   - Wait for ZooKeeper failover: upgraded secondary acquires primary lock and promotes.
   - Confirm new primary `>>> READY <<<` and healthy processing.

3. **Upgrade the old primary and return it as secondary**
   - Deploy new release on the old primary host.
   - Start it; it should become **secondary** (lock held by new primary).
   - Confirm snap load + DR catch-up and `>>> READY <<<`.

4. **Optional failback**
   - If you want the preferred host (`me01`, higher priority) to be primary again: shut down current primary so `me01` can win election (or stop both briefly only if you accept downtime — not required for rolling). Prefer a controlled shutdown of the temporary primary once `me01` secondary is caught up.

**Checks before/after**

- Unique `INSTANCE_ID` per host.
- Shared snap directory reachable; latest usable snap id known.
- ZooKeeper and Kafka reachable from both hosts.
- Only one primary after each step.

### 8.4 Useful commands

```bash
# Start (from deployment root)
./scripts/mestart.sh -c config/prod/primary/config.properties
./scripts/mestart.sh -c config/prod/secondary/config.properties

# Start with explicit snapshot
./scripts/mestart.sh -c <config> --snapshot <snapId>

# Graceful shutdown
./scripts/mectrl.sh -c <config> -i me01 --shutdown
./scripts/mectrl.sh -c <config> -i me02 --shutdown

# Status (details in instance log)
./scripts/mectrl.sh -c <config> -i me01 --status

# Snapshot
./scripts/mectrl.sh -c <config> -i primary --snapshot
```

Look for `>>> READY <<<` in logs after start or promote.

---

## 9. Developer notes (class map)

| Concern | Main classes |
|---------|----------------|
| Process start | `MatchEngineStarter` |
| ZooKeeper election | `ZooKeeperController` |
| Controller wiring | `ControllerFactory` (`CONTROLLER_TYPE=zookeeper`) |
| Warm start / failover actions | `ControllerThread` |
| Mode effect on matcher | `ModeControlMessage` |
| Snap load + Kafka output replay | `SnapLoader` |
| Secondary live sync | `KafkaDRFixListener`, `DecoderThread` |
| Snap write path | `MessagePublisher` / `SnapUtil` + `TradeStateAdminMessage` (`RESTATE`) |
| Ops CLI | `MatchingEngineController` via `scripts/mectrl.sh` |

Control flow for mode changes:

`ZooKeeperController.switchMode` → `ModeControlMessage` on control queue → `ControllerThread` (listeners / snap / publish topic) → matcher (`Context` mode + `MarketStatus`).

---

## 10. Quick FAQ

**Q: Does primary skip rebuild if it won the lock?**  
No. Primary still loads snapshot and replays remaining primary-output messages before going live.

**Q: Does failover need a manual promote command?**  
Not with `CONTROLLER_TYPE=zookeeper`. Secondary promotes after acquiring the primary lock.

**Q: What should ops do for Kafka consumers / API on failover?**  
Nothing special for downstream consumers; they continue on the shared topics.

**Q: Where are snapshots?**  
Under `CHRONICLE_ENGINE_SNAP_DIRECTORY` from config (production typically shared storage such as `/efs/snap`). Cron creates them regularly; `mectrl --snapshot` can force one.
