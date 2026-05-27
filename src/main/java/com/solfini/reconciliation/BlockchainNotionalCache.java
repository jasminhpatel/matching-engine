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

public class BlockchainNotionalCache {
  private static final String SELECT = "SELECT userid,contractkey,notional,updated FROM blockchain_notional_state ORDER BY userId ASC;";
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

  private static void parse(final ResultSet rs) throws SQLException {
    while (rs.next()) {
      WithdrawableAmountUpdateTransaction.UserWithdrawable withdrawable =
          new WithdrawableAmountUpdateTransaction.UserWithdrawable(
              rs.getInt(1), rs.getString(2), rs.getLong(3));

      Map<Integer, WithdrawableAmountUpdateTransaction.UserWithdrawable> contractBalances =
          USER_WITHDRAWABLE_MAP.computeIfAbsent(rs.getString(2), v -> new ConcurrentHashMap<>());
      contractBalances.put(rs.getInt(1), withdrawable);
    }
  }
}
