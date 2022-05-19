package com.solfini.util.affiliate;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.solfini.common.Constants;
import com.solfini.db.DBManager;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AffiliateProgram implements Constants {

  private static final Logger log = LogManager.getLogger(AffiliateProgram.class);
  private static final Map<Integer, Record> records = new HashMap<>();
  private static final Map<String, Record> referrers = new HashMap<>();

  private static boolean dryrun = false;

  private static void loadUsers() throws ParseException, SQLException {
    log.info("Loading all users");
    final String query =
        "SELECT id, username, insert_time, feetier, referral_code, referred_by_code, minimumfeetier, upgradefeetier, lmm, isindividual FROM user_state";
    try (final Connection connection = DBManager.getConnection();
        final PreparedStatement statement = connection.prepareStatement(query);
        final ResultSet rs = statement.executeQuery();) {
      final DateFormat format = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");
      while (rs.next()) {
        final Record record = new Record(rs.getInt(1), rs.getString(2), format.parse(rs.getString(3)), rs.getInt(4), rs.getString(5),
            rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10));

        records.put(record.getUserId(), record);
        if ((record.getReferralCode() != null) && !record.getReferralCode().isEmpty()) {
          referrers.put(record.getReferralCode(), record);
        }
      }

      log.info("{} users loaded", records.size());
    } catch (final Exception e) {
      log.error(e);
      throw e;
    }
  }

  private static void loadUserStats() throws SQLException {
    log.info("Loading user stats for the last month");
    final String query = "SELECT userid, effectivevolume FROM user_stat_log " + "WHERE timestamp > current_date - interval '1' month";
    try (final Connection connection = DBManager.getConnection();
        final PreparedStatement statement = connection.prepareStatement(query);
        final ResultSet rs = statement.executeQuery();) {
      long count = 0;
      while (rs.next()) {
        final int userId = rs.getInt(1);
        final Record record = records.get(userId);
        count++;
        if (count % 1_000_000 == 0) {
          log.info("{} user stat reports processed", count);
        }

        record.addVolume(rs.getDouble(2));
      }
      log.info("{} user stat reports processed", count);
    } catch (Exception e) {
      log.error(e);
      throw e;
    }
  }

  private static void updateFeeTiers() throws ExecutionException, InterruptedException, SQLException {
    log.info("Updating fee tiers");
    final KafkaUpdatePublisher publisher = new KafkaUpdatePublisher();
    final String sql = "UPDATE user_state SET feetier=? WHERE id=?";
    long count = 0;

    try (final Connection connection = DBManager.getConnection(); final PreparedStatement statement = connection.prepareStatement(sql);) {
      connection.setAutoCommit(false);
      for (final Record record : records.values()) {
        if (record.getFeeTier() != record.getNewFeeTier()) {
          count++;
          log.info("FEETIERCHANGE userId={} currentFeeTier={} newFeeTier={}", record.getUserId(), record.getFeeTier(),
              record.getNewFeeTier());

          if (!dryrun) {
            publisher.sendFeeTierUpdate(record.getUserId(), record.getNewFeeTier());
            statement.setInt(1, record.getNewFeeTier());
            statement.setInt(2, record.getUserId());
            statement.addBatch();
          }
        }
      }

      log.info("{} users updated", count);
      if (!dryrun) {
        publisher.flush();
        statement.executeBatch();
        connection.commit();
      }
    } catch (Exception e) {
      log.error(e);
      throw e;
    }
  }

  private static long scale(final long value, final int scale1, final int scale2) {
    long result = value;
    for (int i = 0; i < Math.abs(scale1 - scale2); i++) {
      if (scale1 > scale2)
        result /= 10;
      else if (scale2 > scale1)
        result *= 10;
    }
    return result;
  }

  private static void processFeeDiscounts(final int debitAccountId, final boolean onlyIndividuals)
      throws ExecutionException, InterruptedException, SQLException {
    log.info("Processing fee discounts");
    final int referrerPercentage = StringUtil.toInt(PropertyReader.getProperty("REFERRER_PERCENTAGE", "30"));
    final int referrerUserPercentage = StringUtil.toInt(PropertyReader.getProperty("REFERRER_USER_PERCENTAGE", "5"));
    final int referrerUserDaysBonus = StringUtil.toInt(PropertyReader.getProperty("REFERRER_USER_DAYS_BONUS", "180"));

    final String sql1 = "SELECT id, timestamp, userid, feeinstrumentid, feescale, feeamount FROM user_stat_fee_log WHERE processed=false";
    final String sql2 =
        "UPDATE user_stat_fee_log SET processed=true, " + "grantuserid=?, granttimestamp=?, grantamount=?, grantkafkaoffset=?, "
            + "discountuserid=?, discounttimestamp=?, discountamount=?, discountkafkaoffset=? WHERE id=?";

    final long debit[] = new long[Record.MAX_INSTRUMENTS];

    try (final Connection connection = DBManager.getConnection();
        final PreparedStatement statement1 = connection.prepareStatement(sql1);
        final PreparedStatement statement2 = connection.prepareStatement(sql2);
        final ResultSet rs = statement1.executeQuery();) {
      final KafkaUpdatePublisher publisher = new KafkaUpdatePublisher();

      long count = 0;
      while (rs.next()) {
        final int id = rs.getInt(1);
        final Timestamp timestamp = rs.getTimestamp(2);
        final int userId = rs.getInt(3);
        final int feeInstrumentId = rs.getInt(4);
        final short scale = rs.getShort(5);
        final long amount = rs.getLong(6);
        final Record record = records.get(userId);
        count++;

        boolean granted = false;
        final Record referrer = referrers.get(record.getReferredByCode());
        if ((referrer != null) && (!onlyIndividuals || (onlyIndividuals && referrer.isIndividual()))) {
          final long grant = (-referrerPercentage * amount) / 100;
          debit[feeInstrumentId] -= scale(grant, scale, 6);
          log.info("GRANT userId={}, instrument={}, fee={}, grant={}, scale={}", referrer.getUserId(), feeInstrumentId, amount, grant,
              scale);

          granted = true;
          if (!dryrun) {
            statement2.setInt(1, referrer.getUserId());
            statement2.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            statement2.setLong(3, grant);
            if (grant > 0) {
              statement2.setLong(4, publisher.sendBalanceAdjustment(referrer.getUserId(), feeInstrumentId, grant, scale));
            } else {
              statement2.setNull(4, Types.BIGINT);
            }
          }
        }

        boolean discounted = false;
        final long elapsed = TimeUnit.DAYS.convert(timestamp.getTime() - record.getStartDate().getTime(), TimeUnit.MILLISECONDS);
        if ((elapsed <= referrerUserDaysBonus) && (referrer != null)
            && (!onlyIndividuals || (onlyIndividuals && record.isIndividual()))) {
          final long discount = (-referrerUserPercentage * amount) / 100;
          debit[feeInstrumentId] -= scale(discount, scale, 6);
          log.info("DISCOUNT userId={}, instrument={}, fee={}, discount={}, scale={}", userId, feeInstrumentId, amount, discount, scale);

          discounted = true;
          if (!dryrun) {
            statement2.setInt(5, userId);
            statement2.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            statement2.setLong(7, discount);
            if (discount > 0) {
              statement2.setLong(8, publisher.sendBalanceAdjustment(userId, feeInstrumentId, discount, scale));
            } else {
              statement2.setNull(8, Types.BIGINT);
            }
          }
        }

        if (!dryrun) {
          if (!granted) {
            statement2.setNull(1, Types.INTEGER);
            statement2.setNull(2, Types.TIMESTAMP);
            statement2.setNull(3, Types.BIGINT);
            statement2.setNull(4, Types.BIGINT);
          }
          if (!discounted) {
            statement2.setNull(5, Types.INTEGER);
            statement2.setNull(6, Types.TIMESTAMP);
            statement2.setNull(7, Types.BIGINT);
            statement2.setNull(8, Types.BIGINT);
          }
          statement2.setInt(9, id);
          statement2.executeUpdate();
        }
      }

      if (debitAccountId != -1) {
        for (int i = 0; i < debit.length; i++) {
          if (debit[i] != 0) {
            final long offset = publisher.sendBalanceAdjustment(debitAccountId, i, debit[i], (short) 6);
            log.info("DEBIT accountId={}, instrument={}, amount={}, scale={}, offset={}", debitAccountId, i, debit[i], 6, offset);
          }
        }
      }

      log.info("{} fee records processed", count);
      if (!dryrun) {
        publisher.flush();
      }
    } catch (Exception e) {
      log.error(e);
      throw e;
    }
  }

  public static void main(String[] args) {
    final Options options = new Options();
    options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
    options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
    options.addOption(Option.builder("d").longOpt("dry-run").desc("dry run mode").required(false).build());
    options.addOption(Option.builder().longOpt("fee-tiers").desc("update fee tiers").required(false).build());
    options.addOption(Option.builder().longOpt("fee-rebates").desc("process fee rebates").required(false).build());
    options.addOption(Option.builder().longOpt("debit-account").desc("rebate debit account").hasArg().argName("account").required(false).build());
    options.addOption(Option.builder().longOpt("only-individuals").desc("only consider individuals for fee rebates").required(false).build());

    for (String arg : args) {
      if (arg.equals("-h") || arg.equals("--help")) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("AffiliateProgram", options);
        System.out.println();
        return;
      }
    }

    try {
      final CommandLineParser parser = new DefaultParser();
      final CommandLine cmd = parser.parse(options, args);

      File file = new File(cmd.getOptionValue("c"));
      if (file.exists() && file.isDirectory()) {
        file = new File(cmd.getOptionValue("c") + "/config.properties");
      }

      if (cmd.hasOption("d")) {
        dryrun = true;
        log.info("Dry run mode enabled");
      }

      PropertyReader.initialize(new FileInputStream(file), null);
      loadUsers();
      loadUserStats();

      if (cmd.hasOption("fee-tiers")) {
        updateFeeTiers();
      }

      if (cmd.hasOption("fee-rebates")) {
        processFeeDiscounts(Integer.parseInt(cmd.getOptionValue("debit-account", "-1")), cmd.hasOption("only-individuals"));
      }

    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      System.err.println("Run with --help option for usage information");
      System.exit(1);
    }

    System.exit(0);
  }
}
