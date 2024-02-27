package com.solfini.db;

import java.sql.Connection;
import java.sql.SQLException;
import com.solfini.util.PropertyReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 *
 * @author Chris Mack
 *
 */
public class DBManager {
  private static final String DB_URL = PropertyReader.getProperty("DB_URL", "jdbc:postgresql://127.0.0.1:5432/MyDB");
  private static final String DB_USER = PropertyReader.getProperty("DB_USER", "rohanw");
  private static final String DB_PASSWORD = PropertyReader.getProperty("DB_PASSWORD", "password");
  private static HikariDataSource dataSource = init();

  private DBManager() {}

  private static final HikariDataSource init() {
    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl(DB_URL);
    config.setUsername(DB_USER);
    config.setPassword(DB_PASSWORD);
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("useServerPrepStmts", "true");
    config.addDataSourceProperty("useLocalSessionState", "true");
    config.addDataSourceProperty("useLocalTransactionState", "true");
    config.addDataSourceProperty("rewriteBatchedStatements", "true");
    config.addDataSourceProperty("cacheResultSetMetadata", "true");
    config.addDataSourceProperty("cacheServerConfiguration", "true");
    config.addDataSourceProperty("elideSetAutoCommits", "true");
    config.addDataSourceProperty("maintainTimeStats", "false");
    config.addDataSourceProperty("serverTimezone", "UTC");
    config.addDataSourceProperty("useSSL", "false");

    return new HikariDataSource(config);
  }

  public static final Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

}
