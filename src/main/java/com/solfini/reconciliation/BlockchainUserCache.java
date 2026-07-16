package com.solfini.reconciliation;

import com.solfini.db.DBManager;
import com.solfini.util.blockchain.model.BlockchainUser;

import com.solfini.util.blockchain.model.EngineUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockchainUserCache {
  private static final String SELECT_BLOCKCHAIN_USERS = "SELECT id, address, createdAt, network, contractType FROM blockchain_user_state order by id asc;";
  private static final String SELECT_ENGINE_USERS = "SELECT id, username FROM user_state order by id asc;";
  private static final String INSERT = """
      INSERT INTO blockchain_user_state (id, address, createdAt, network, contractType)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (id, network, contractType) DO NOTHING;
      """;

  private static final Map<String, BlockchainUser> BLOCKCHAIN_USER_MAP = new ConcurrentHashMap<>();
  private static final Map<Integer, EngineUser> ENGINE_USER_MAP = new ConcurrentHashMap<>();

  public static void loadFromDb() {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_BLOCKCHAIN_USERS);
        final ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        final int userId = rs.getInt(1);
        final String address = rs.getString(2);
        final long createdAt = rs.getLong(3);
        final String network = rs.getString(4);
        final String contractType = rs.getString(5);
        final BlockchainUser user = new BlockchainUser(userId, address, createdAt, network, contractType);
        BLOCKCHAIN_USER_MAP.put(cacheKey(network, contractType, userId), user);
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }

    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(SELECT_ENGINE_USERS);
        final ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        final int userId = rs.getInt(1);
        final String address = rs.getString(2);
        final EngineUser user = new EngineUser(userId, address);
        ENGINE_USER_MAP.put(userId, user);
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  public static BlockchainUser getBlockchainUser(final int userId, final String network, final String contractType) {
    return BLOCKCHAIN_USER_MAP.get(cacheKey(network, contractType, userId));
  }

  public static EngineUser getEngineUser(final int userId) {
    return ENGINE_USER_MAP.get(userId);
  }

  public static void addUser(final BlockchainUser user) {
    try (final Connection conn = DBManager.getConnection();
        final PreparedStatement ps = conn.prepareStatement(INSERT)) {
      ps.setInt(1, user.getUserId());
      ps.setString(2, user.getAddress());
      ps.setLong(3, user.getCreatedAt());
      ps.setString(4, user.getNetwork());
      ps.setString(5, user.getContractType());
      ps.executeUpdate();
    } catch (final Exception e) {
      e.printStackTrace();
    }
    BLOCKCHAIN_USER_MAP.put(cacheKey(user.getNetwork(), user.getContractType(), user.getUserId()), user);
  }

  private static String cacheKey(final String network, final String contractType, final int userId) {
    return network + "_" + contractType + "_" + userId;
  }
}