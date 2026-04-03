package com.solfini.reconciliation;

import com.solfini.db.DBManager;
import com.solfini.util.blockchain.model.WithdrawableAmountUpdateTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BlockchainNotionalCache {
  private static final String SELECT = "SELECT userid,notional,updated FROM blockchain_notional_state ORDER BY userId ASC;";
  private static final String UPSERT = "INSERT INTO blockchain_notional_state (userId,notional,updated,snapshotId) VALUES (?,?,?,?) ON CONFLICT (userId) DO UPDATE SET notional=EXCLUDED.notional,updated=EXCLUDED.updated,snapshotId=EXCLUDED.snapshotId;";

  private static final Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> USER_WITHDRAWABLE_MAP = new HashMap<>();

  public static void loadFromDB() {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery()) {
      parse(rs);

    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  public static Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> getUserWithdrawableMap() {
    return USER_WITHDRAWABLE_MAP;
  }

  public static void upsert(final WithdrawableAmountUpdateTransaction.UserWithdrawable withdrawable, final long updated, final long snapshotId) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(UPSERT)) {
      ps.setInt(1, withdrawable.getUserId());
      ps.setLong(2, withdrawable.getQuantity());
      ps.setLong(3, updated);
      ps.setLong(4, snapshotId);
      ps.executeUpdate();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private static void parse(final ResultSet rs) throws SQLException {
    while (rs.next()) {
      WithdrawableAmountUpdateTransaction.UserWithdrawable withdrawable = new WithdrawableAmountUpdateTransaction.UserWithdrawable(rs.getInt(1), rs.getLong(2));
      USER_WITHDRAWABLE_MAP.put(rs.getInt(1), withdrawable);
    }
  }
}
