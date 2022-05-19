package com.solfini.matchengine.pricing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.solfini.common.CustomLogger;
import com.solfini.db.DBManager;


public class DeribitPair {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(DeribitPair.class);

  private static final String INSERT_PAIR =
      "INSERT INTO deribit_pair (instrumentId, type, timestamp, symbol, kind, quote_currency, base_currency, option_type,settlement_period, expiration_timestamp, creation_timestamp, is_active, contract_size, taker_commission, maker_commission, tick_size) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String SELECT_PAIR = "select id from deribit_pair where symbol=?";

  private int id;
  private final int instrumentId;
  private final int type;
  private final long timestamp;
  private final String symbol;
  private final String kind;
  private final String quote_currency;
  private final String base_currency;
  private final String option_type;
  private final String settlement_period;
  private final long expiration_timestamp;
  private final long creation_timestamp;
  private final boolean is_active;
  private final int contract_size;
  private final double taker_commission;
  private final double maker_commission;
  private final double tick_size;

  private DeribitLastTrade deribitLastTrade;

  public DeribitPair(final int instrumentId, final int type, final long timestamp, final String symbol, final String kind,
      final String quote_currency, final String base_currency, final String option_type, final String settlement_period,
      final long expiration_timestamp, final long creation_timestamp, final boolean is_active, final int contract_size,
      final double taker_commission, final double maker_commission, final double tick_size) {
    this.instrumentId = instrumentId;
    this.type = type;
    this.timestamp = timestamp;
    this.symbol = symbol;
    this.kind = kind;
    this.quote_currency = quote_currency;
    this.base_currency = base_currency;
    this.option_type = option_type;
    this.settlement_period = settlement_period;
    this.expiration_timestamp = expiration_timestamp;
    this.creation_timestamp = creation_timestamp;
    this.is_active = is_active;
    this.contract_size = contract_size;
    this.taker_commission = taker_commission;
    this.maker_commission = maker_commission;
    this.tick_size = tick_size;
    persist(timestamp);
  }

  public final int getId() {
    return id;
  }

  public final void setId(final int id) {
    this.id = id;
  }

  public final int getInstrumentId() {
    return instrumentId;
  }

  public final int getType() {
    return type;
  }

  public final long getTimestamp() {
    return timestamp;
  }

  public final String getSymbol() {
    return symbol;
  }

  public final String getKind() {
    return kind;
  }

  public final String getQuote_currency() {
    return quote_currency;
  }

  public final String getBase_currency() {
    return base_currency;
  }

  public final String getOption_type() {
    return option_type;
  }

  public final String getSettlement_period() {
    return settlement_period;
  }

  public final long getExpiration_timestamp() {
    return expiration_timestamp;
  }

  public final long getCreation_timestamp() {
    return creation_timestamp;
  }

  public final boolean isIs_active() {
    return is_active;
  }

  public final int getContract_size() {
    return contract_size;
  }

  public final double getTaker_commission() {
    return taker_commission;
  }

  public final double getMaker_commission() {
    return maker_commission;
  }

  public final double getTick_size() {
    return tick_size;
  }

  public final DeribitLastTrade getDeribitLastTrade() {
    return deribitLastTrade;
  }

  public final void setDeribitLastTrade(final DeribitLastTrade deribitLastTrade) {
    this.deribitLastTrade = deribitLastTrade;
  }

  public void persist(final long timestamp) {
    try (final Connection connection = DBManager.getConnection();
        final PreparedStatement statement1 = connection.prepareStatement(SELECT_PAIR);) {
      statement1.setString(1, symbol);
      final ResultSet rs = statement1.executeQuery();
      if (!rs.next()) {
        try (final PreparedStatement statement2 = connection.prepareStatement(INSERT_PAIR);) {
          statement2.setInt(1, instrumentId);
          statement2.setInt(2, type);
          statement2.setLong(3, timestamp);
          statement2.setString(4, symbol);
          statement2.setString(5, kind);
          statement2.setString(6, quote_currency);
          statement2.setString(7, base_currency);
          statement2.setString(8, option_type);
          statement2.setString(9, settlement_period);
          statement2.setLong(10, expiration_timestamp);
          statement2.setLong(11, creation_timestamp);
          statement2.setBoolean(12, is_active);
          statement2.setInt(13, contract_size);
          statement2.setDouble(14, taker_commission);
          statement2.setDouble(15, maker_commission);
          statement2.setDouble(16, tick_size);
          statement2.executeUpdate();

          final ResultSet rs2 = statement1.executeQuery();
          if (rs2.next()) {
            id = rs2.getInt(1);
          }
          rs2.close();
        } catch (Exception e) {
          LOGGER.error("error", e);
        }
      } else {
        id = rs.getInt(1);
      }
      rs.close();

    } catch (Exception e) {
      LOGGER.error("error", e);
    }
  }

}
