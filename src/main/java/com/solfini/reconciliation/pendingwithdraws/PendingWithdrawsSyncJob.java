package com.solfini.reconciliation.pendingwithdraws;

import static com.solfini.common.Constants.ETHEREUM;
import static com.solfini.common.Constants.USDC;
import static com.solfini.common.Constants.USDT;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solfini.common.Context;
import com.solfini.db.DBManager;
import com.solfini.util.ApiUtils;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;
import com.solfini.util.blockchain.BlockchainSenderFactory;
import com.solfini.util.blockchain.gasstation.GasStationUtil;
import com.solfini.util.blockchain.model.GasFee;
import com.solfini.util.blockchain.model.WithdrawTransaction;
import com.solfini.util.blockchain.util.RpcUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

public class PendingWithdrawsSyncJob {
  private static final String SELECT_PENDING_WITHDRAW_REQUEST =
      "select id, sequence_number, insert_time, address, updateType, userId, securityid, symbol, balance, balance_scale, confirms, source, signature, updateBy, status, ip, assetId, tokenId, groupId, withdrawType, transactionHash, chain, bank,\"bankAccount\", \"swiftCode\", \"certificateName\", serials, memo, \"registryName\", \"accountNumber\" from WITHDRAW_REQUEST where status =? order by userId, symbol, address";
  private static final String SELECT_PENDING_WITHDRAW_REQUEST_USER_COUNT = "select count(distinct userId) from WITHDRAW_REQUEST where status = 1";
  private static final String UPDATE_WITHDRAW_REQUEST =
      "UPDATE WITHDRAW_REQUEST set balance=?, balance_scale=?, confirms=?, source=?, signature=?, updateBy=?, status=?, transactionhash=?, chain=?, serials=?, memo=? where id=? and userId=? and securityid=? and assetId=? and tokenId=? and groupId=?";

  private static HikariDataSource dataSource;

  static {
    final Properties overlay = new Properties();
    loadConfigurationFile(overlay);
    dataSource = init();
  }

  private static HikariDataSource init() {
    final String DB_URL = PropertyReader.getProperty("DB_URL", "jdbc:postgresql://localhost:6005/db?");
    final String DB_USER = PropertyReader.getProperty("DB_USER", "db");
    final String DB_PASSWORD = PropertyReader.getProperty("DB_PASSWORD", "Gd82haBv85");

    HikariConfig config = new HikariConfig();
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

  private Connection getConnection() throws SQLException {
    return this.dataSource.getConnection();
  }

  public static void main(String[] args) {
    final int fundManagerBatchSize = PropertyReader.getProperty("FUND_MANAGER_BATCH_SIZE", 500);

    final List<AddressBalance> pendingWithdraws = getPendingWithdraws();
    if (pendingWithdraws == null || pendingWithdraws.isEmpty()) {
      System.exit(0);
    }

    final List<Integer> userIds = new ArrayList<>();
    for (AddressBalance withdraw : pendingWithdraws) {
      if (!userIds.contains(withdraw.getUserId())) {
        userIds.add(withdraw.getUserId());
      }
    }
    final int userCount = userIds.size();

    try {
      final Web3j web3j = RpcUtil.createWeb3jConnection(ETHEREUM, null, false, false);
      BigDecimal ether = Convert.fromWei(new BigDecimal(fetchCurrentMaxFeePerGas(web3j)), Convert.Unit.ETHER);
      double ethPriceUSD = getEthPriceUSD();

      double totalTransactionFee = ethPriceUSD * ether.doubleValue() * ((int) Math.ceil(userCount / (double) fundManagerBatchSize));;
      System.out.println("Total Transaction Fee " + totalTransactionFee);
      if (totalTransactionFee < 0 || totalTransactionFee > 5.00) {
        System.exit(0);
      }

      final StringBuilder summary = new StringBuilder();
      boolean success = FundManagerSnapForPendingWithdrawsUpdater.update(args, summary, userIds);
      System.out.println(summary.toString());

      if (success) {
        for (AddressBalance withdraw : pendingWithdraws) {
          System.out.println(withdraw.getId() + " " + withdraw.getUserId()  + " " + withdraw.getSecurityId()  + " " + withdraw.getStatus());
//          String tokenAddress = null;
//          if (USDC.equalsIgnoreCase(withdraw.getSymbol())) {
//            tokenAddress = Context.getUsdcContract();
//          } else if (USDT.equalsIgnoreCase(withdraw.getSymbol())) {
//            tokenAddress = Context.getUsdtContract();
//          }
//
//          final WithdrawTransaction withdrawTransaction = new WithdrawTransaction();
//          withdrawTransaction.setAccountId(withdraw.getUserId());
//          withdrawTransaction.setInstrumentId(withdraw.getSecurityId());
//          withdrawTransaction.setToken(tokenAddress);
//          withdrawTransaction.setRecipient(withdraw.getAddress());
//          withdrawTransaction.setAmount(String.valueOf(withdraw.getBalanceLong()));
//          withdrawTransaction.setChainType(Context.getFundManagerChain());
//          withdrawTransaction.setContractAddress(Context.getFundManagerContractAddress());
//
//          boolean isWithdrawSuccess = Objects.requireNonNull(BlockchainSenderFactory.getSender(withdrawTransaction)).processTransaction();
//          if (isWithdrawSuccess) {
//            withdraw.setStatus(2);
//            withdraw.setTransactionHash(withdrawTransaction.getTransactionHash());
//            withdraw.setChain(withdrawTransaction.getChainType());
//            updateWithdrawRequest(withdraw);
//          } else {
//            withdraw.setStatus(7);// failed to process from blockchain
//            updateWithdrawRequest(withdraw);
//          }
          ApiUtils.sendPendingWithdrawWithVTokenRequest(withdraw.getId(),withdraw.getUserId(),"LIQUIDITY");
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    System.exit(0);
  }

  private static List<AddressBalance> getPendingWithdraws() {
    List<AddressBalance> pendingWithdraws = new ArrayList<>();

    AddressBalance addressBalance = null;
    try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(SELECT_PENDING_WITHDRAW_REQUEST);) {
      ps.setInt(1, 8);
      final ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        addressBalance = parseWithdrawRequest(rs);
        pendingWithdraws.add(addressBalance);
      }
      rs.close();
    } catch (Exception e) {
      //LOGGER.error(ERROR_LOG, e);
    }

    return pendingWithdraws;
  }

//  private static double getEthPriceUSD() throws Exception {
//    HttpClient client = HttpClient.newHttpClient();
//    HttpRequest req = HttpRequest.newBuilder()
//        .uri(URI.create("https://api.coingecko.com/api/v3/simple/price?ids=ethereum&vs_currencies=usd"))
//        .build();
//    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
//    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
//    return json.get("ethereum").getAsJsonObject().get("usd").getAsDouble();
//  }

  private static double getEthPriceUSD() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("https://api.coinbase.com/v2/prices/ETH-USD/spot"))
        .build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
    return json.getAsJsonObject("data").get("amount").getAsDouble();
  }

  private static BigInteger fetchCurrentMaxFeePerGas(final Web3j web3j) throws Exception {
    final GasFee gasFee = GasStationUtil.fetchGasFees(web3j);
    if (gasFee != null) {
      System.out.println("MaxFeePerGas " + gasFee.getMaxFeePerGas());
      return gasFee.getMaxFeePerGas();
    }
    return new BigInteger("-1");
  }

  private static boolean loadConfigurationFile(final Properties overlay) {
    try {
      String configFile = "./config.properties";

      File file = new File(configFile);
      if (file.exists() && file.isDirectory()) {
        file = new File(configFile + "/config.properties");
      }

      final InputStream stream = new FileInputStream(file);
      PropertyReader.initialize(stream, overlay);

      return true;
    } catch (final Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static AddressBalance parseWithdrawRequest(final ResultSet rs) throws SQLException {
    AddressBalance addressBalance = new AddressBalance();
    addressBalance.setId(rs.getInt(1));
    addressBalance.setTimestamp(rs.getString(3));
    addressBalance.setAddress(rs.getString(4));
    addressBalance.setUserId(rs.getInt(6));
    addressBalance.setSecurityId(rs.getInt(7));
    addressBalance.setSymbol(rs.getString(8));
    addressBalance.setBalanceLong(rs.getLong(9));
    addressBalance.setBalance_scale(rs.getInt(10));
    addressBalance.setConfirms(rs.getInt(11));
    addressBalance.setSource(rs.getString(12));
    addressBalance.setStatus(rs.getInt(15));
    addressBalance.setIp(rs.getString(16));
    addressBalance.setAssetId(rs.getLong(17));
    addressBalance.setTokenId(rs.getLong(18));
    addressBalance.setGroupId(rs.getLong(19));
    addressBalance.setType(rs.getInt(20));
    addressBalance.setTransactionHash(rs.getString(21));
    addressBalance.setChain(rs.getString(22));
    addressBalance.setCertificateName(rs.getString(26));
    addressBalance.setSerials(rs.getString(27));
    addressBalance.setMemo(rs.getString(28));
    addressBalance.setRegistryName(rs.getString(29));
    addressBalance.setAccountNumber(rs.getString(30));

    return addressBalance;
  }

  public static final void updateWithdrawRequest(final AddressBalance addressBalance) {
    final String timestamp = StringUtil.getCurrentDateYYYMMDDHHMMSSsss();
    try (Connection conn = DBManager.getConnection(); PreparedStatement ps = conn.prepareStatement(UPDATE_WITHDRAW_REQUEST);) {
      ps.setLong(1, addressBalance.getBalanceLong());
      ps.setInt(2, addressBalance.getBalance_scale());
      ps.setInt(3, addressBalance.getConfirms());
      ps.setString(4, addressBalance.getSource());
      ps.setString(5, addressBalance.getSignature());
      ps.setString(6, addressBalance.getUpdateBy());
      ps.setInt(7, addressBalance.getStatus());
      ps.setString(8, addressBalance.getTransactionHash());
      ps.setString(9, addressBalance.getChain());
      ps.setString(10, addressBalance.getSerials());
      ps.setString(11, addressBalance.getMemo());

      ps.setLong(12, addressBalance.getId());
      ps.setLong(13, addressBalance.getUserId());
      ps.setInt(14, addressBalance.getSecurityId());
      ps.setLong(15, addressBalance.getAssetId());
      ps.setLong(16, addressBalance.getTokenId());
      ps.setLong(17, addressBalance.getGroupId());

      ps.executeUpdate();

    } catch (Exception e) {
      //LOGGER.error(ERROR_LOG, e);
    }
  }
}
