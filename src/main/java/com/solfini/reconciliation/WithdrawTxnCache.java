package com.solfini.reconciliation;

import com.solfini.db.DBManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class WithdrawTxnCache {
  //WTH_STATUS_HOLD_SNAP_DELAY = 8;
  private static final String SELECT = "select userid from (select distinct userId from address_state where status IN (8)) A order by userId asc;";

  public static List<Integer> loadFromDB() {
    final List<Integer> withdrawHeldUserIds = new ArrayList<>();
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT);
        final ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        withdrawHeldUserIds.add(rs.getInt(1));
      }

    } catch (final Exception e) {
      e.printStackTrace();
    }
    return withdrawHeldUserIds;
  }
}
