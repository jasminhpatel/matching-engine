package com.solfini.db;

import com.solfini.util.PropertyReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author Chris Mack
 *
 */
public class ExternalDBManager {
  private static final String EXTERNAL_DB_URL = PropertyReader.getProperty("EXTERNAL_DB_URL", "jdbc:mysql://localhost:3306/db");
  private static final String EXTERNAL_DB_USER = PropertyReader.getProperty("EXTERNAL_DB_USER", "mysql");
  private static final String EXTERNAL_DB_PASSWORD = PropertyReader.getProperty("EXTERNAL_DB_PASSWORD", "password");
  private static HikariDataSource dataSource = init();

  private ExternalDBManager() {}

  private static final HikariDataSource init() {
    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl(EXTERNAL_DB_URL);
    config.setUsername(EXTERNAL_DB_USER);
    config.setPassword(EXTERNAL_DB_PASSWORD);
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
