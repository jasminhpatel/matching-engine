package com.solfini.matchengine.pricing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;

public class DeribitLastTrade {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitLastTrade.class);

  private static final String INSERT_TRADE =
      "INSERT INTO deribit_last_trade (pairId, instrumentId, timestamp, symbol, direction, trade_id, trade_seq, tick_direction,price, index_price, amount, iv) VALUES (?, ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?)";
  private static final String SELECT_TRADE = "select id from deribit_last_trade where symbol=? and trade_seq=?";


  private int id;
  private int pairId;
  private int instrumentId;
  private long timestamp;
  private String symbol;
  private String direction;
  private String trade_id;
  private long trade_seq;
  private int tick_direction;
  private double price;
  private double index_price;
  private double amount;
  private double iv;

  public DeribitLastTrade(final int pairId, final int instrumentId, final int type, final long timestamp, final String symbol,
      final String direction, final String trade_id, final long trade_seq, final int tick_direction, final double price,
      final double index_price, final double amount, final double iv) {
    this.pairId = pairId;
    this.instrumentId = instrumentId;
    this.timestamp = timestamp;
    this.symbol = symbol;
    this.direction = direction;
    this.trade_id = trade_id;
    this.trade_seq = trade_seq;
    this.tick_direction = tick_direction;
    this.price = price;
    this.index_price = index_price;
    this.amount = amount;
    this.iv = iv;
  }

  public void set(final int pairId, final int instrumentId, final int type, final long timestamp, final String symbol,
      final String direction, final String trade_id, final long trade_seq, final int tick_direction, final double price,
      final double index_price, final double amount, final double iv) {
    this.pairId = pairId;
    this.instrumentId = instrumentId;
    this.timestamp = timestamp;
    this.symbol = symbol;
    this.direction = direction;
    this.trade_id = trade_id;
    this.trade_seq = trade_seq;
    this.tick_direction = tick_direction;
    this.price = price;
    this.index_price = index_price;
    this.amount = amount;
    this.iv = iv;
  }

  public final int getPairId() {
    return pairId;
  }

  public final int getInstrumentId() {
    return instrumentId;
  }

  public final long getTimestamp() {
    return timestamp;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final String getDirection() {
    return direction;
  }

  public final String getTrade_id() {
    return trade_id;
  }

  public final long getTrade_seq() {
    return trade_seq;
  }

  public final int getTick_direction() {
    return tick_direction;
  }

  public final double getPrice() {
    return price;
  }

  public final double getIndex_price() {
    return index_price;
  }

  public final double getAmount() {
    return amount;
  }

  public final double getIv() {
    return iv;
  }

  public void persist(final long timestamp) {
    try (final Connection connection = DBManager.getConnection();
        final PreparedStatement statement1 = connection.prepareStatement(SELECT_TRADE);) {
      statement1.setString(1, symbol);
      statement1.setLong(2, trade_seq);
      final ResultSet rs = statement1.executeQuery();
      if (!rs.next()) {
        try (final PreparedStatement statement2 = connection.prepareStatement(INSERT_TRADE);) {
          statement2.setInt(1, pairId);
          statement2.setInt(2, instrumentId);
          statement2.setLong(3, timestamp);
          statement2.setString(4, symbol);
          statement2.setString(5, direction);
          statement2.setString(6, trade_id);
          statement2.setLong(7, trade_seq);
          statement2.setInt(8, tick_direction);
          statement2.setDouble(9, price);
          statement2.setDouble(10, index_price);
          statement2.setDouble(11, amount);
          statement2.setDouble(12, iv);
          statement2.executeUpdate();

          final ResultSet rs2 = statement1.executeQuery();
          if (rs2.next()) {
            id = rs2.getInt(1);
          }
          rs2.close();
        } catch (Exception e) {
          LOGGER.error("error", e);
        }
      }
      rs.close();

    } catch (Exception e) {
      LOGGER.error("error", e);
    }
  }
}
