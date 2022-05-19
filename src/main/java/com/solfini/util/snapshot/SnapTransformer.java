package com.solfini.util.snapshot;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Date;
import java.util.GregorianCalendar;

import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.db.DBManager;
import com.solfini.instrument.Position;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.FeeType;
import com.solfini.internal.admin.schema.MakerTaker;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.ExpireContractMessage;
import com.solfini.matchengine.message.admin.FeeAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.util.snapshot.SnapConverter.Statistics;

public class SnapTransformer {
  private static final String SELECT_SECURITYDEFS =
      "select id,sequence_number,insert_time,updatetype,securityid,symbol,name,assettype,quotedid,baseid,pricescale,quantityscale,orderbookstrategy,preordercheckstrategy,settletype,maintmarginpercent,requiredmarginpercent,usdmark,status,estimatedusercount,estimatedvolatility,daysfeedisactive,estimatedvar,sortorder,tenure from %s order by id";

  private final HashMap<String, SecurityDefinitionAdminMessage> instruments = new HashMap<>();
  private final Properties config;

  public SnapTransformer(final Properties config) {
    this.config = config;

    // Load all instruments from DB
    loadFromDB();
    System.out.println("Number of instruments loaded from DB: " + instruments.size());
  }

  private void loadFromDB() {
    final String tableName = config.getProperty("INSTRUMENT_DEF_TABLE_NAME");
    if (tableName == null) {
      throw new RuntimeException("Invalid config: INSTRUMENT_DEF_TABLE_NAME");
    }

    System.out.println("Loading instruments from table: " + tableName);
    final String query = String.format(SELECT_SECURITYDEFS, tableName);

    try {
      final Connection connection = DBManager.getConnection();
      final PreparedStatement statement = connection.prepareStatement(query);

      final ResultSet rs = statement.executeQuery();

      while (rs.next()) {

        try {
          final SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage();
          // id
          message.setSequenceNumber(rs.getLong(2));
          // Inserttime
          message.setUpdateType(UpdateType.PUT);
          message.setSecurityId(rs.getInt(5));
          message.setSymbol(rs.getString(6));
          message.setName(rs.getString(7));
          message.setAssetType(AssetType.get(rs.getShort(8)));
          message.setQuotedId(rs.getInt(9));
          message.setBaseId(rs.getInt(10));
          message.setPriceScale(rs.getShort(11));
          message.setQuantityScale(rs.getShort(12));
          message.setOrderBookStrategy(rs.getInt(13));
          message.setPreOrderCheckStrategy(rs.getInt(14));
          message.setSettleType(rs.getInt(15));
          message.setMaintMarginBasisPoints(rs.getInt(16));
          message.setRequiredMarginBasisPoints(rs.getInt(17));
          message.setIndexFeedUsdMark(rs.getDouble(18));
          message.setMarketStatus(MarketStatus.get(rs.getShort(19)));
          message.setEstimatedUserCount(rs.getInt(20));
          message.setEstimatedVolatility(rs.getDouble(21));
          message.setDaysFeedIsActive(rs.getInt(22));
          message.setEstimatedVAR(rs.getDouble(23));
          // sortorder

          final String tenure = rs.getString(25);
          if (tenure != null) {
            switch (tenure) {
              case "5m":
                message.setExpireRollTimeMillis(ExpireContractMessage.FIVE_MINUTE);
                break;
              case "1D":
                message.setExpireRollTimeMillis(ExpireContractMessage.ONE_DAY);
                break;
              case "2D":
                message.setExpireRollTimeMillis(ExpireContractMessage.TWO_DAYS);
                break;
              case "1M":
                message.setExpireRollTimeMillis(ExpireContractMessage.ONE_MONTH);
                break;
              case "1Q":
                message.setExpireRollTimeMillis(ExpireContractMessage.ONE_QUARTER);
                break;
              case "1Y":
                message.setExpireRollTimeMillis(ExpireContractMessage.ONE_YEAR);
                break;
              default:
                message.setExpireRollTimeMillis(0);
            }
          }

          message.setRouteToDestination("ALL");
          message.setTextData("");

          instruments.put(message.getSymbol(), message);
        } catch (final SQLException e) {
          System.err.println(rs);
          e.printStackTrace();
        }
      }
    } catch (final SQLException e) {
      e.printStackTrace();
    }
  }

  public ArrayList<Message> transform(final ArrayList<Message> messages) {
    // All transformed instruments
    final HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments = new HashMap<>();

    // Instrument id mapping changes
    final HashMap<Integer, Integer> instrumentChanges = new HashMap<>();
    final HashSet<Integer> instrumentRemovals = new HashSet<>();

    // 1. Add all database read instruments. If the instrument is already in the
    // snap, overwrite params.
    for (final Message m : messages) {
      if (m instanceof SecurityDefinitionAdminMessage) {
        final SecurityDefinitionAdminMessage fromSnap = (SecurityDefinitionAdminMessage) m;

        // Ignore entries that were not in db. Save the ids for later transforms.
        final SecurityDefinitionAdminMessage fromDb = instruments.get(fromSnap.getSymbol());
        if (fromDb == null) {
          instrumentRemovals.add(fromSnap.getSecurityId());
          continue;
        }

        // If the instrument id is changed, save the mapping for later transforms.
        if (fromSnap.getSecurityId() != fromDb.getSecurityId()) {
          instrumentChanges.put(fromSnap.getSecurityId(), fromDb.getSecurityId());
        }

        // Update fields from DB
        fromSnap.setSequenceNumber(fromDb.getSequenceNumber());
        fromSnap.setSecurityId(fromDb.getSecurityId());
        fromSnap.setName(fromDb.getName());
        fromSnap.setAssetType(fromDb.getAssetType());
        fromSnap.setQuotedId(fromDb.getQuotedId());
        fromSnap.setBaseId(fromDb.getBaseId());
        fromSnap.setPriceScale(fromDb.getPriceScale());
        fromSnap.setPriceScale(fromDb.getPriceScale());
        fromSnap.setQuantityScale(fromDb.getQuantityScale());
        fromSnap.setOrderBookStrategy(fromDb.getOrderBookStrategy());
        fromSnap.setPreOrderCheckStrategy(fromDb.getPreOrderCheckStrategy());
        fromSnap.setSettleType(fromDb.getSettleType());
        fromSnap.setExpireRollTimeMillis(fromDb.getExpireRollTimeMillis());

        outgoingInstruments.put(fromSnap.getSymbol(), fromSnap);
      }
    }

    // Add instruments that were not in the snap
    for (final SecurityDefinitionAdminMessage m : instruments.values()) {
      if (!outgoingInstruments.containsKey(m.getSymbol())) {
        outgoingInstruments.put(m.getSymbol(), m);
      }
    }

    // 2. Transform all instruments to array list; sorted by id. The snapshot starts
    // with all instruments.
    final ArrayList<Message> modified = new ArrayList<>();
    modified.addAll(outgoingInstruments.values());
    Collections.sort(modified, (final Message left, final Message right) -> {
      final SecurityDefinitionAdminMessage l = (SecurityDefinitionAdminMessage) left;
      final SecurityDefinitionAdminMessage r = (SecurityDefinitionAdminMessage) right;

      return l.getSecurityId() - r.getSecurityId();
    });

    // 3.1 Set array size for instruments
    setupOrderbookArraySize(outgoingInstruments);

    // 3.2 Set additional parameters for assets
    setupAssetParameters(outgoingInstruments);

    // 3.3 Set additional parameters for perpetuals
    setupPerpetualsParameters(outgoingInstruments);

    // 3.4 Set additional parameters for dated futures
    setupFuturesParameters(outgoingInstruments);

    // 3.5 Set additional parameters for options
    setupOptionsParameters(outgoingInstruments);

    // 3.6 Set additional parameters for auctions
    setupAuctionsParameters(outgoingInstruments);

    // 4. Generate Fees for all instruments.
    modified.addAll(generateFees(outgoingInstruments));

    // 5. Add rest of the messages transformed
    for (final Message m : messages) {

      // Ignore security defs and fees.
      if (m.getMessageType() == MessageType.SECURITY_DEFINITION
          || m.getMessageType() == MessageType.FEE_ADMIN) {
        continue;
      }

      if (m.getMessageType() == MessageType.NEW_ORDER) {
        // Transform order security id if changed
        final Order order = (Order) (m);

        // Check if the instrument is deleted. If so, remove the order
        if (instrumentRemovals.contains(order.getSecurityId())) {
          System.out
              .println("Removing Order: [" + order.getOrderId() + "]" + order.getSecurityId());
          continue;
        }

        // Check if the security id is changed.
        if (instrumentChanges.containsKey(order.getSecurityId())) {
          System.out.println("Changing Order instrument: [" + order.getOrderId() + "]"
              + order.getSecurityId() + "->" + instrumentChanges.get(order.getSecurityId()));
          order.setSecurityId(instrumentChanges.get(order.getSecurityId()));
        }
      }

      if (m.getMessageType() == MessageType.BALANCE_ADMIN) {
        // Transform positions
        final BalanceAdminMessage balance = (BalanceAdminMessage) (m);
        final Position positions[] = balance.getPositionArr();
        for (int i = 0; i < positions.length; ++i) {
          final Position pos = positions[i];
          if (pos == null) {
            continue;
          }

          // Check if the instrument is deleted.
          if (instrumentRemovals.contains(pos.getInstrumentId())) {
            System.out.println(
                "Removing Position: [" + balance.getUserId() + "]" + pos.getInstrumentId());
            positions[i] = null;
          }

          if (instrumentChanges.containsKey(pos.getInstrumentId())) {
            System.out.println("Changing Position: [" + balance.getUserId() + "]"
                + pos.getInstrumentId() + "->" + instrumentChanges.get(pos.getInstrumentId()));
            pos.setInstrumentId(instrumentChanges.get(pos.getInstrumentId()));
          }
        }
      }

      modified.add(m);
    }

    printStats(modified);
    return modified;
  }

  private void setupAssetParameters(
      HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {
      // Assets
      if (!instrument.getSymbol().contains("[") && !instrument.getSymbol().contains("/")) {
        // Cross collateral
        if (instrument.getSymbol().equals("USDC")) {
          instrument.setCollateralMarginPercentDiscount(0);
        } else {
          instrument.setCollateralMarginPercentDiscount(10000);
        }
      }
    }
  }

  private void setupPerpetualsParameters(
      HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {
      // Perpetuals
      if (instrument.getSymbol().toUpperCase().contains("[F]")) {
        // Margin calcs
        instrument.setMarginCurveId(5);
        instrument.setMaintMarginBasisPoints(40);
        instrument.setRequiredMarginBasisPoints(80);
      }
    }
  }

  private void setupFuturesParameters(
      HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {
      // Dated futures
      if (instrument.getSymbol().toUpperCase().contains("[DF]")) {
        // Margin curve
        instrument.setMarginCurveId(5);
        // Underlying id
        final String underlyingSymbol = getUnderlyingSymbol(instrument.getSymbol());
        instrument.setUnderlyerId(outgoingInstruments.get(underlyingSymbol).getSecurityId());

        // Expire time
        try {
          instrument.setExpireTimeMillis(getFutureExpireTimeMillis(instrument.getSymbol()));
          if (instrument.getSymbol().contains("Now")) {
            instrument.setExpireRollTimeMillis(ExpireContractMessage.FIVE_MINUTE);
          } else {
            instrument.setExpireRollTimeMillis(ExpireContractMessage.ONE_YEAR);
          }
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private void setupOptionsParameters(
      HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {
      // Options
      if (instrument.getSymbol().toUpperCase().contains("[C]") || instrument.getSymbol().toUpperCase().contains("[P]")) {
        // Margin curve
        instrument.setMarginCurveId(5);

        // Underlying id
        final String underlyingSymbol = getUnderlyingSymbol(instrument.getSymbol());
        instrument.setUnderlyerId(outgoingInstruments.get(underlyingSymbol).getSecurityId());

        // Strike price
        instrument.setStrikePrice(getStrikePrice(instrument.getSymbol(), instrument.getPriceScale()));

        // Expire time
        try {
            instrument.setExpireTimeMillis(getOptionExpireTimeMillis(instrument.getSymbol()));
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  private void setupAuctionsParameters(
    HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {

      // Auctions
      if (instrument.getSymbol().toUpperCase().contains("[A]")) {
        instrument.setAuctionStartTimeHrGMT(instrument.getAuctionStartTimeHrGMT() == 0 ? 2 : instrument.getAuctionStartTimeHrGMT());
        instrument.setAuctionDurationTime(instrument.getAuctionDurationTime() == 0 ? 36_000_000 : instrument.getAuctionDurationTime());
        instrument.setAuctionFixingAttempts(instrument.getAuctionFixingAttempts() == 0 ? 3 : instrument.getAuctionFixingAttempts());
        instrument.setAuctionFixingWaitTime(instrument.getAuctionFixingWaitTime() == 0 ? 60_000 : instrument.getAuctionFixingWaitTime());
      }
    }
  }

  private ArrayList<FeeAdminMessage> generateFees(
      final HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    ArrayList<FeeAdminMessage> fees = new ArrayList<>();

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {

      ArrayList<Integer> tiers = getFeeTiers(instrument);

      for (int i = 0; i < tiers.size() / 2; ++i) {
        final FeeAdminMessage maker = new FeeAdminMessage();
        maker.setFeeInstrumentId(instrument.getQuotedId());
        maker.setFee(tiers.get(i));
        maker.setAssetId(instrument.getSecurityId());
        maker.setMakerTaker(MakerTaker.MAKER);
        maker.setUpdateType(UpdateType.PUT);
        maker.setFeeType(FeeType.PERCENT);
        maker.setTier(i);
        fees.add(maker);
      }

      for (int i = tiers.size() / 2; i < tiers.size(); ++i) {
        final FeeAdminMessage taker = new FeeAdminMessage();
        taker.setFeeInstrumentId(instrument.getQuotedId());
        taker.setFee(tiers.get(i));
        taker.setAssetId(instrument.getSecurityId());
        taker.setMakerTaker(MakerTaker.TAKER);
        taker.setUpdateType(UpdateType.PUT);
        taker.setFeeType(FeeType.PERCENT);
        taker.setTier(i - tiers.size() / 2);
        fees.add(taker);
      }
    }

    return fees;
  }

  private void setupOrderbookArraySize(
      HashMap<String, SecurityDefinitionAdminMessage> outgoingInstruments) {

    final int BTC = outgoingInstruments.get("BTC").getSecurityId();
    final int USD = outgoingInstruments.get("USD").getSecurityId();
    final int USDC = outgoingInstruments.get("USDC").getSecurityId();

    for (SecurityDefinitionAdminMessage instrument : outgoingInstruments.values()) {

      // Only change pairs
      if (!instrument.getSymbol().contains("/")) {
        continue;
      }

      // All BTC/X
      if (instrument.getSymbol().startsWith("BTC/")) {
        // If option, 4M. Everything else 16M
        if (instrument.getSymbol().contains("[C]") || instrument.getSymbol().contains("[P]")) {
          instrument.setArrSize(4_194_304);
        } else {
          instrument.setArrSize(16_777_216);
        }

        continue;
      }

      // All ETH/X
      if (instrument.getSymbol().startsWith("ETH/")) {
        if (instrument.getSymbol().equals("ETH/BTC")) {
          instrument.setArrSize(16_777_216);
        } else {
          instrument.setArrSize(2_097_152);
        }

        continue;
      }

      // Quoted in BTC
      if (instrument.getQuotedId() == BTC) {
        instrument.setArrSize(16_777_216);
        continue;
      }

      // Quoted in altcoins and 6 level precision
      if (instrument.getQuotedId() != BTC && instrument.getQuotedId() != USD
          && instrument.getQuotedId() != USDC) {
        instrument.setArrSize(16_777_216);
        continue;
      }

      // Equities
      instrument.setArrSize(1_048_576);
    }
  }

  static String getUnderlyingSymbol(final String symbol) {
    return symbol.substring(0, symbol.indexOf("[")) + "C";
  }

  static int getStrikePrice(final String symbol, final int priceScale) {
    int strikePrice = Integer.parseInt(symbol.substring(symbol.lastIndexOf("_") + 1));
    for (int i = 0; i < priceScale; i++) {
      strikePrice *= 10;
    }

    return strikePrice;
  }

  static long getFutureExpireTimeMillis(final String symbol) throws ParseException {
    // BTC/USD[DF]Now
    if (symbol.contains("Now")) {
      return System.currentTimeMillis();
    }

    // BTC/USD[DF]Jun26
    final String expireDateStr = symbol.substring(symbol.indexOf("]") + 1);

    final SimpleDateFormat dateFormat = new SimpleDateFormat("MMMdd");
    final Date date = dateFormat.parse(expireDateStr);
    final Calendar dateCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    dateCal.setTime(date);

    // Set year
    dateCal.set(Calendar.YEAR, Year.now().getValue());

    // Add one day so it becomes midnight
    dateCal.add(Calendar.DATE, 1);

    // Check if the date is a past date and move to the next year if so
    final Calendar todayCal = Calendar.getInstance();
    if (dateCal.getTime().before(todayCal.getTime())) {
      dateCal.add(Calendar.YEAR, 1);
    }

    return dateCal.getTimeInMillis();
  }

  static long getOptionExpireTimeMillis(final String symbol) throws ParseException {
    // BTC/USD[C]Now25_4000
    // BTC/USD[P]Now25_4000
    if (symbol.contains("Now")) {
      return System.currentTimeMillis();
    }

    // BTC/USD[C]Sep25_4000
    // BTC/USD[P]Sep25_4000
    final String expireDateStr = symbol.substring(
      symbol.indexOf("]") + 1,
      symbol.lastIndexOf("_")
    );

    final SimpleDateFormat dateFormat = new SimpleDateFormat("MMMdd");
    final Date date = dateFormat.parse(expireDateStr);
    final Calendar dateCal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    dateCal.setTime(date);

    // Set year
    dateCal.set(Calendar.YEAR, Year.now().getValue());

    // Add one day so it becomes midnight
    dateCal.add(Calendar.DATE, 1);

    // Check if the date is a past date and move to the next year if so
    final Calendar todayCal = Calendar.getInstance();
    if (dateCal.getTime().before(todayCal.getTime())) {
      dateCal.add(Calendar.YEAR, 1);
    }

    return dateCal.getTimeInMillis();
  }

  private ArrayList<Integer> getFeeTiers(SecurityDefinitionAdminMessage instrument) {
    // Detect the instrument type based on the symbol

    ArrayList<Integer> fees = new ArrayList<>();

    String configPrefix;
    if (instrument.getSymbol().contains("[F]")) {
      configPrefix = "FUTURE";
    } else if (instrument.getSymbol().contains("[DF]")) {
      configPrefix = "SETTLED_FUTURE";
    } else if (instrument.getSymbol().contains("[C]")) {
      configPrefix = "CALL_OPTION";
    } else if (instrument.getSymbol().contains("[P]")) {
      configPrefix = "PUT_OPTION";
    } else if (instrument.getSymbol().contains("[A]") || instrument.getSymbol().contains("/")) {
      configPrefix = "SPOT";
    } else {
      return fees;
    }

    final String tiersString = config.getProperty(configPrefix + "_TIER_COUNT");
    if (tiersString == null) {
      throw new RuntimeException("Invalid config value: " + configPrefix + "_TIER_COUNT");
    }

    final int tiers = Integer.parseInt(tiersString);
    for (int i = 0; i < tiers; ++i) {
      final String valueString = config.getProperty(configPrefix + "_TIER_MAKER_" + i);
      if (valueString == null) {
        throw new RuntimeException("Invalid config value: " + configPrefix + "_TIER_MAKER_" + i);
      }

      final int makerFee = Integer.parseInt(valueString);
      fees.add(makerFee);
    }
    for (int i = 0; i < tiers; ++i) {
      final String valueString = config.getProperty(configPrefix + "_TIER_TAKER_" + i);
      if (valueString == null) {
        throw new RuntimeException("Invalid config value: " + configPrefix + "_TIER_TAKER_" + i);
      }

      final int makerFee = Integer.parseInt(valueString);
      fees.add(makerFee);
    }

    return fees;
  }

  private static void printStats(final ArrayList<Message> modified) {
    final Statistics statistics = new SnapConverter.Statistics();

    for (final Message m : modified) {
      statistics.count(m.getMessageType().name());
    }
    statistics.print(true);
  }
}
