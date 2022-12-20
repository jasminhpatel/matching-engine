package com.solfini.integration;

import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.common.MessageType;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.matchengine.message.admin.GlobalStateAdminMessage;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.message.internal.MassCancelOrder;
import com.solfini.matchengine.message.internal.Order;
import com.solfini.sbe.encoder.OrdType;
import com.solfini.sbe.encoder.Side;
import com.solfini.user.UserCache;
import com.solfini.util.PropertyReader;
import org.apache.commons.cli.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import uk.co.real_logic.artio.fields.DecimalFloat;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegrationTest {

  protected Publisher publisher;
  protected Listener listener;
  protected TopicListener primary;
  protected TopicListener secondary;

  protected boolean validatePrimaryOnly = true;
  protected static final AtomicInteger currentOrderID = new AtomicInteger();

  protected static UserAdminMessage userAdminMessage(final int userId, final String username, final String password, final int firmId,
      final int feeTier, final boolean lmm) {
    return new UserAdminMessage() {
      {
        setUserId(userId);
        setUsername(username);
        setPassword(password);
        setFirmId(firmId);
        setFeeTier(feeTier);
        setLmm(lmm);
        setUpdateType(UpdateType.PUT);
      }
    };
  }

  protected static BalanceAdminMessage balanceAdminMessage(final int userId, final int feeTier, final int txType, final int txId,
      final Balance... balances) {
    return new BalanceAdminMessage() {
      {
        setUserId(userId);
        setFeeTier(feeTier);
        setTxType(txType);
        setTxId(txId);
        for (final Balance balance : balances) {
          addBalance(balance);
        }
      }
    };
  }

  protected static BalanceAdminMessage balanceAdminMessage(final int userId, final int feeTier, final int txType, final int txId,
      List<Balance> balances) {
    return new BalanceAdminMessage() {
      {
        setUserId(userId);
        setFeeTier(feeTier);
        setTxType(txType);
        setTxId(txId);
        for (final Balance balance : balances) {
          addBalance(balance);
        }
      }
    };
  }

  protected static SecurityDefinitionAdminMessage securityDefinitionAdminMessage(final int securityID, final AssetType assetType,
      final String symbol, String name, int quantityScale, int priceScale, int orderBookStrategy, int preOrderCheck, int quotedID,
      int baseID) {
    return new SecurityDefinitionAdminMessage() {
      {
        setSecurityId(securityID);
        setAssetType(assetType);
        setSymbol(symbol);
        setName(name);
        setQuantityScale((short) quantityScale);
        setPriceScale((short) priceScale);
        setOrderBookStrategy(orderBookStrategy);
        setPreOrderCheckStrategy(preOrderCheck);
        if (!assetType.equals(AssetType.ASSET)) {
          setBaseId(baseID);
          setQuotedId(quotedID);
        }
        setUpdateType(UpdateType.PUT);
      }
    };
  }

  protected static GlobalStateAdminMessage globalStateAdminMessage(int liquidationMode) {
    return new GlobalStateAdminMessage() {
      {
        setLiquidationMode(liquidationMode);
      }
    };
  }

  protected static Order order(int orderId, int orderSecurityId, DecimalFloat price, DecimalFloat qty, Side side, long account,
      String clOrdId) {
    int orderIdAdj = (orderId == 0) ? currentOrderID.incrementAndGet() : orderId;
    return new Order() {
      {
        setOrderId(orderIdAdj);
        setAccount((int) account);
        setUser(UserCache.get((int) account));
        setClOrdId(clOrdId);
        setOrdType(OrdType.LIMIT);
        setSecurityId(orderSecurityId);
        setSide(side);
        setPrice(price.value(), (short) price.scale());
        setQty(qty.value(), (short) qty.scale());
        setQuantityLong(qty.value() * 100);
        setPriceInt((int) price.value());
        if (side == Side.SELL)
          setType(Constants.SELL_LIMIT);
        else if (side == Side.BUY)
          setType(Constants.BUY_LIMIT);
      }
    };
  }

  protected static MassCancelOrder massCancelOrder(int userId, String senderCompId) {
    return new MassCancelOrder() {
      {
        setUser(user);
        setSenderCompId(senderCompId);
      }
    };
  }

  int SECURITY_ID_QUOTED = 1;
  int SECURITY_ID_QUOTE = 12;
  int SECURITY_ID_PAIR = 13;
  int USER_ID_ONE = 100;
  int USER_ID_TWO = 101;
  int USER_ID_THREE = 102;
  int USER_ID_FOUR = 103;
  int USER_ID_FIVE = 104;
  int USER_ID_SIX = 105;
  int USER_ID_SEVEN = 106;
  int USER_ID_EIGHT = 107;

  @Rule
  public TestName name = new TestName();

  @Rule
  public Timeout globalTimeout = Timeout.seconds(200);

  protected void setup() throws Exception {}

  ;

  @Before
  public final void before() {
    File file = new File(getClass().getClassLoader().getResource("integration.properties").getFile());
    try {
      PropertyReader.initialize(new FileInputStream(file), null);
    } catch (Exception e) {
      Assert.fail("Config loading failed. Error:" + e);
    }

    Log.info("RUNNING: " + name.getMethodName());
    publisher = new Publisher(PropertyReader.getProperty("API_KAFKA_TOPIC_IN", ""));
    listener = new Listener();
    primary = listener.listen(PropertyReader.getProperty("ME_KAFKA_TOPIC_PRIMARY", ""));
    secondary = listener.listen(PropertyReader.getProperty("ME_KAFKA_TOPIC_SECONDARY", ""));

    try {
      setup();
    } catch (Exception e) {
      Assert.fail("Exception thrown while setup(). error: " + e);
    }
  }


  public void createNewUserWithBalance(final int userId, final String username, final String password, final int firmId, final int feeTier,
      final boolean lmm, final List<Balance> balances) throws Exception {
    publisher.send(userAdminMessage(userId, username, password, firmId, feeTier, lmm));

    Message message = primary.expectMessage(MessageType.USER_ADMIN);

    Log.debug("XX createNewUserWithBalance Response: " + message.toString());
    UserCache.add((UserAdminMessage) message);
    // "UserAdminMessage [senderCompId=null, connectionId=0, triggerTimeMillis=0, userType=0, updateType=POST, " +
    // "userId=100, username=user100, password=123user100, firmId=0, feeTier=1, requestStatus=null, status=0, accountType=0, lmm=false,
    // routeToDestination=, useDiscountFeesCoin=false, balanceList=[]]"));
    // if(! validatePrimaryOnly) {
    // secondary.expect("UserAdminMessage [senderCompId=null, connectionId=0, triggerTimeMillis=0, userType=0, updateType=POST, " +
    // "userId=100, username=user100, password=123user100, firmId=0, feeTier=1, requestStatus=null, status=0, accountType=0, lmm=false,
    // routeToDestination=, useDiscountFeesCoin=false, balanceList=[]]");
    // }
    publisher.send(balanceAdminMessage(userId, feeTier, 1, 1, balances));
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    // primary.expect("BalanceAdminMessage [senderCompId=EXEC, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, "
    // + "userId="+userId+", firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance "
    // + "[assetId=11, balance=5000.00, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0,
    // usdValue=0.0,"
    // + " usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0],
    // Balance "
    // + "[assetId=12, balance=5000.00, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0,
    // usdValue=0.0, "
    // + "usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0],
    // Balance "
    // + "[assetId=13, balance=5000.00, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0,
    // usdValue=0.0, "
    // + "usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0,
    // settleCoinRealized=0.0]]]");
    if (!validatePrimaryOnly) {
      // secondary.expectMessage(MessageType.USER_ADMIN); //TODO validate the behavior
      // secondary.expectMessage(MessageType.BALANCE_ADMIN);
    }
  }

  public void overrideBalance(final int userId, final int feeTier, final int txType, final int txId, List<Balance> balances)
      throws Exception {

    publisher.send(balanceAdminMessage(userId, feeTier, txType, txId, balances));
    primary.expectMessage(MessageType.BALANCE_ADMIN);
    // primary.expect("BalanceAdminMessage [senderCompId=EXEC, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, "
    // + "userId="+userId+", firmId=0, txType=0, txId=0, feeTier=0, requestStatus=null, routeToDestination=null, balanceList=[Balance "
    // + "[assetId=11, balance=500.00, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0,
    // usdValue=0.0,"
    // + " usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0],
    // Balance "
    // + "[assetId=12, balance=500.00, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0,
    // usdValue=0.0, "
    // + "usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0, settleCoinRealized=0.0],
    // Balance "
    // + "[assetId=13, balance=500.00, balance_change=0, eventType=0, orderId=0, execId=0, usdCostBasis=0.0, usdAvgCostBasis=0.0,
    // usdValue=0.0, "
    // + "usdUnrealized=0.0, usdRealized=0.0, quotedUsdMark=0.0, settleCoinUsdMark=0.0, settleCoinUnrealized=0.0,
    // settleCoinRealized=0.0]]]");
  }

  public void createInstrument(final int securityID, final AssetType assetType, final String symbol, String name, int quantityScale,
      int priceScale, int orderBookStrategy, int preOrderCheck, int quotedID, int baseID) throws Exception {
    SecurityDefinitionAdminMessage securityDefinitionAdminMessage = securityDefinitionAdminMessage(securityID, assetType, symbol, name,
        quantityScale, priceScale, orderBookStrategy, preOrderCheck, quotedID, baseID);
    publisher.send(securityDefinitionAdminMessage);
    int maintMargin = securityDefinitionAdminMessage.getMaintMarginBasisPoints();
    int requiredMargin = securityDefinitionAdminMessage.getRequiredMarginBasisPoints();

    InstrumentCache.updateSecurityDefinition(securityDefinitionAdminMessage);
    primary.expectMessage("SecurityDefinitionAdminMessage",
        "updateType=PUT, securityId=" + securityID + ", symbol=" + symbol + ", name=" + name + ", " + "assetType=" + assetType
            + ", triggerTimeMillis=0, baseId=" + baseID + ", quotedId=" + quotedID + ", priceScale=" + priceScale + ", quantityScale="
            + quantityScale + ", base=null, quoted=null, orderBookStrategy=" + orderBookStrategy + ", " + "preOrderCheckStrategy="
            + preOrderCheck + ", estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, "
            + "maintMarginPercent=" + maintMargin + ", requiredMarginPercent=" + requiredMargin + ", minQty=0, maxQty=0, maxPrice=0");

    if (!validatePrimaryOnly) {
      secondary.expectMessage("SecurityDefinitionAdminMessage", "updateType=PUT, securityId=" + securityID + ", symbol=" + symbol
          + ", name=" + name + ", " + "assetType=" + assetType + ", triggerTimeMillis=0, baseId=" + baseID + ", quotedId=" + quotedID
          + ", priceScale=" + priceScale + ", quantityScale=" + quantityScale + ", base=null, quoted=null, orderBookStrategy="
          + orderBookStrategy + ", " + "preOrderCheckStrategy=" + preOrderCheck
          + ", estimatedUserCount=0, daysFeedIsActive=0, estimatedVolatility=0.0, estimatedVAR=0.0, settleType=0, " + "maintMarginPercent="
          + maintMargin + ", requiredMarginPercent=" + requiredMargin
          + ", minQty=0, maxQty=0, maxPrice=0, supportOrderType=0, marginCurveId=0, commissionType=0, indexFeedUsdMark=0.0, collateralMarginPercentDiscount=0");

    }
  }

  public List<Balance> createBalanceList(long quotedBalance, long quoteBalance, long pairBalance) {
    List<Balance> balanceList = new ArrayList<>();
    balanceList.add(new Balance(SECURITY_ID_QUOTED, quotedBalance, 0, 0, 0, null,0, TokenType.ERC20));
    balanceList.add(new Balance(SECURITY_ID_QUOTE, quoteBalance, 0, 0, 0, null,0, TokenType.ERC20));
    balanceList.add(new Balance(SECURITY_ID_PAIR, pairBalance, 0, 0, 0, null,0, TokenType.ERC20));
    return balanceList;
  }

  public void drainAll() throws Exception {
    Message message = primary.receive();
    int count = 0;
    while (message != null) {
      System.out.println("Count: " + ++count + "Message Drained: " + message);
      message = primary.receive();
    }
  }

  public void drainAll(TopicListener topicListener) throws Exception {
    Message message = topicListener.receive();
    int count = 0;
    while (message != null) {
      System.out.println("Count: " + ++count + "Message Drained: " + message);
      message = topicListener.receive();
    }
  }

  public void createNewUser(final int userId, final String username, final String password, final int firmId, final int feeTier,
      final boolean lmm) throws Exception {
    UserAdminMessage message = userAdminMessage(userId, username, password, firmId, feeTier, lmm);
    UserCache.add(message);

    publisher.send(message);
    primary.expectMessage("UserAdminMessage",
        "senderCompId=null, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, " + "userId=" + userId + ", username="
            + username + ", firmId=" + firmId + ", feeTier=" + feeTier + ", status=0, accountType=0, lmm=" + lmm
            + ", useDiscountFeesCoin=false");
    if (!validatePrimaryOnly) {
      secondary.expectMessage("UserAdminMessage",
          "senderCompId=null, connectionId=0, triggerTimeMillis=0, userType=0, updateType=PUT, " + "userId=" + userId + ", username="
              + username + ", firmId=" + firmId + ", feeTier=" + feeTier + ", status=0, accountType=0, lmm=" + lmm
              + ", useDiscountFeesCoin=false");
    }
  }


  public static void main(String[] args) {
    try {
      Options options = new Options();
      options.addOption(Option.builder("h").longOpt("help").desc("show help").required(false).build());
      options.addOption(Option.builder("c").longOpt("conf").desc("configuration file").hasArg().argName("file").required().build());
      options.addOption(Option.builder("d").longOpt("debug").desc("debug mode").type(boolean.class).build());

      for (final String arg : args) {
        if (arg.equals("-h") || arg.equals("--help")) {
          final HelpFormatter formatter = new HelpFormatter();
          formatter.printHelp("IntegrationTest", options);
          System.out.println();
          return;
        }
      }

      CommandLine cmd;
      try {
        CommandLineParser parser = new DefaultParser();
        cmd = parser.parse(options, args);
      } catch (Exception e) {
        System.err.println(e.getMessage());
        System.err.println("Run with --help option for usage information");
        return;
      }

      // Enable debug mode
      if (cmd.hasOption("d")) {
        Log.enableDebug();
      }

      // Load config
      Properties properties = new Properties();

      // Disable auto commit for kafka consumer
      properties.setProperty("KAFKA.CONSUMER.enable.auto.commit", "false");

      PropertyReader.initialize(new FileInputStream(new File(cmd.getOptionValue("c"))), properties);

      Result result = JUnitCore.runClasses(Computer.serial(), CommonTests.class, AutoLiquidationTest.class,
          AutoLiquidationWithCounterPartyTest.class, AutoLiquidationWithInsuranceFundTest.class, AutoLiquidationWithOpenOrdersTest.class);
      // Result result = JUnitCore.runClasses(Computer.serial(), CommonTests.class);
      for (final Failure failure : result.getFailures()) {
        System.out.println(failure.getTrace());
      }
      System.out.println(result.wasSuccessful());
    } catch (Exception e) {
      System.err.println("ERROR: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
