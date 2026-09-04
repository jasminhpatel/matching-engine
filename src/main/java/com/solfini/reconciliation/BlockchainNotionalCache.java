package com.solfini.reconciliation;

import com.solfini.db.DBManager;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockchainNotionalCache {
  private static final Logger LOGGER = LogManager.getLogger(BlockchainNotionalCache.class);
  private static final String SELECT = "SELECT userid,contractkey,notional,updated,snapshotid FROM blockchain_notional_state ORDER BY userId ASC;";
  private static final String UPSERT = """
      INSERT INTO blockchain_notional_state (userId, contractKey, notional, updated, snapshotId) 
      VALUES (?, ?, ?, ?, ?) 
      ON CONFLICT (userId, contractKey) 
      DO UPDATE SET 
        notional = EXCLUDED.notional, 
        updated = EXCLUDED.updated, 
        snapshotId = EXCLUDED.snapshotId;
      """;

  private static final Map<String, Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable>> USER_WITHDRAWABLE_MAP = new ConcurrentHashMap<>();

  public static void loadFromDB() {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery()) {
      parse(rs);

    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  public static Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> getUserWithdrawableMap(final String contractKey) {
    return USER_WITHDRAWABLE_MAP.computeIfAbsent(contractKey, v -> new ConcurrentHashMap<>());
  }

  public static void upsert(final WithdrawableAmountUpdateTransaction.UserWithdrawable withdrawable, final long updated, final long snapshotId) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(UPSERT)) {
      ps.setInt(1, withdrawable.getUserId());
      ps.setString(2, withdrawable.getContractKey());
      ps.setLong(3, withdrawable.getQuantity());
      ps.setLong(4, updated);
      ps.setLong(5, snapshotId);
      ps.executeUpdate();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  public static long getOrCreateIncrementalId(final long snapshotId, final String network, final String symbol) {
    // Table schema: blockchain_snap_mapping (id BIGSERIAL PK, snapshot_id BIGINT UNIQUE)
    // All 3 chains share the same id for a given snapshot folder via the conflict path.
    // ON CONFLICT DO NOTHING (no target) suppresses both the snapshot_id unique violation and any
    // PK conflict if the sequence drifts behind existing rows after a manual setval reset.
    final String insert = "INSERT INTO blockchain_snap_mapping (snapshot_id) VALUES (?) ON CONFLICT DO NOTHING";
    final String select = "SELECT id FROM blockchain_snap_mapping WHERE snapshot_id = ?";
    try (Connection conn = DBManager.getConnection()) {
      try (PreparedStatement ps = conn.prepareStatement(insert)) {
        ps.setLong(1, snapshotId);
        ps.executeUpdate();
      }
      try (PreparedStatement ps = conn.prepareStatement(select)) {
        ps.setLong(1, snapshotId);
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            final long id = rs.getLong("id");
            warnIfMappingHasGap(conn, id);
            return id;
          }
        }
      }
    } catch (final Exception e) {
      // MUST be loud: id BIGSERIAL becomes the on-chain snapshotId, and the contract only ever
      // accepts the next one sequentially (+1 from its last committed value — see
      // FundingContractV6.SnapshotIdGap). A silently-swallowed failure here (this used to be a
      // bare e.printStackTrace(), invisible unless someone happened to be watching stderr) can
      // leave the Postgres SERIAL sequence having consumed a value with no row to show for it —
      // Postgres never reuses a skipped sequence value, even on rollback — permanently bricking
      // every future publish for every composite until someone manually backfills the missing id
      // (see TSYS-176/177 incident 2026-09-04: happened from an ordinary SIGTERM landing between
      // the INSERT and commit, not even a normal exception path).
      LOGGER.error("Failed to get or create blockchain_snap_mapping row for real snapshotId="
          + snapshotId + " network=" + network + " symbol=" + symbol
          + " — a stuck/duplicate Postgres sequence value here can permanently gap every future "
          + "on-chain publish (SnapshotIdGap) until manually backfilled.", e);
    }
    return -1;
  }

  /**
   * id BIGSERIAL with zero deletions means a healthy table always has id == row count; any
   * mismatch means a sequence value was consumed with no committed row (see the exception
   * comment above) — loud now, while it's cheap to fix, instead of a cryptic on-chain
   * SnapshotIdGap revert days later once the gap is finally reached.
   */
  private static void warnIfMappingHasGap(final Connection conn, final long latestId) {
    try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM blockchain_snap_mapping")) {
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next() && rs.getLong(1) != latestId) {
          LOGGER.error("blockchain_snap_mapping has a gap: latest id=" + latestId + " but only "
              + rs.getLong(1) + " row(s) exist. A future on-chain publish WILL revert with "
              + "SnapshotIdGap once it reaches the missing id(s) — backfill the gap manually "
              + "before that happens (see BlockchainNotionalCache.getOrCreateIncrementalId).");
        }
      }
    } catch (final Exception e) {
      LOGGER.error("Failed to check blockchain_snap_mapping for gaps (latest id=" + latestId + ")", e);
    }
  }

  public static boolean confirmSnapMapping(final long snapshotId, final String network, final String symbol) {
    // No confirmed column in the table; verify the row exists (inserted by getOrCreateIncrementalId).
    final String sql = "SELECT id FROM blockchain_snap_mapping WHERE snapshot_id = ?";
    try (Connection conn = DBManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, snapshotId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    } catch (final Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public static long getSnapshotIdByMappingId(final long mappingId, final String network, final String symbol) {
    // id IS the mapping ID (BIGSERIAL PK), so a direct lookup by primary key is sufficient.
    final String sql = "SELECT snapshot_id FROM blockchain_snap_mapping WHERE id = ?";
    try (Connection conn = DBManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, mappingId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("snapshot_id");
        }
      }
    } catch (final SQLException e) {
      e.printStackTrace();
    }
    return -1;
  }

  private static void parse(final ResultSet rs) throws SQLException {
    while (rs.next()) {
      WithdrawableAmountUpdateTransaction.UserWithdrawable withdrawable =
          new WithdrawableAmountUpdateTransaction.UserWithdrawable(
              rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getLong(5));

      Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> contractBalances =
          USER_WITHDRAWABLE_MAP.computeIfAbsent(rs.getString(2), v -> new ConcurrentHashMap<>());
      contractBalances.put(rs.getInt(1), withdrawable);
    }
  }
}
