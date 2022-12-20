package com.solfini.matchengine.model.risk;

import com.solfini.instrument.Balance;
import com.solfini.internal.admin.schema.TokenType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.risk.UserRiskCache;
import com.solfini.user.User;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.solfini.sbe.encoder.Side;
import java.util.concurrent.ConcurrentHashMap;

@RunWith(JUnitParamsRunner.class)
public class RiskCalculationTest extends RiskTest {

  private void assertMarginRatioRiskBucket(User user, int expectedRiskBucket) {
    for (int bucket = UserRiskCache.RISK_BUCKET_LEVERAGE_1; bucket <= UserRiskCache.RISK_BUCKET_LEVERAGE_10; bucket++) {
      ConcurrentHashMap<Integer, User> map = UserRiskCache.getIndex(pair.getId(), Side.BUY.ordinal(), bucket);
      if (map != null) {
        for (User counterpartyUser : map.values()) {
          if (counterpartyUser == null)
            continue;

          if (counterpartyUser == user) {
            Assert.assertEquals(expectedRiskBucket, bucket);
          }
        }
      }
    }
  }

  private void assertLeveragePNLRiskBucket(User user, int expectedRiskBucket) {
    for (int bucket = UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1; bucket <= UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_10; bucket++) {
      ConcurrentHashMap<Integer, User> map = UserRiskCache.getIndex(pair.getId(), Side.BUY.ordinal(), bucket);
      if (map != null) {
        for (User counterpartyUser : map.values()) {
          if (counterpartyUser == null)
            continue;

          if (counterpartyUser == user) {
            Assert.assertEquals(expectedRiskBucket, bucket);
          }
        }
      }
    }
  }

  private void assertNotionalValueRiskBucket(User user, int expectedRiskBucket) {
    for (int bucket = UserRiskCache.RISK_BUCKET_SIZE_1; bucket <= UserRiskCache.RISK_BUCKET_SIZE_5; bucket++) {
      ConcurrentHashMap<Integer, User> map = UserRiskCache.getIndex(pair.getId(), Side.BUY.ordinal(), bucket);
      if (map != null) {
        for (User counterpartyUser : map.values()) {
          if (counterpartyUser == null)
            continue;

          if (counterpartyUser == user) {
            Assert.assertEquals(expectedRiskBucket, bucket);
          }
        }
      }
    }
  }

  private void assertRiskBucketTransition(User user, int srcMarginRatioRB, int desMarginRatioRB, int srcLeverageRB, int desLeverageRB,
      int srcNotionalRB, int desNotionalRB) {
    assertMarginRatioRiskBucket(user, srcMarginRatioRB);
    assertLeveragePNLRiskBucket(user, srcLeverageRB);
    assertNotionalValueRiskBucket(user, srcNotionalRB);

    // As Re indexing and Update Risk executes in different thread, to simulate the behavior called twice
    UserRiskCache.reIndex(user);
    preOrderCheck.updateRisk(user, null);
    UserRiskCache.reIndex(user);
    preOrderCheck.updateRisk(user, null);

    assertMarginRatioRiskBucket(user, desMarginRatioRB);
    assertLeveragePNLRiskBucket(user, desLeverageRB);
    assertNotionalValueRiskBucket(user, desNotionalRB);
  }

  private Object[] updateRiskTestValues() {
    return new Object[] {
        // Notional Bucket 1 - 2
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 200, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_2},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 100_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_3},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 250_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_4},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 500_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_3, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_4},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 700_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_4, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_3, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_4},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 800_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_5, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_3, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_4},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_000_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_6, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_4, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_100_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_7, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_4, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_170_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_8, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_4, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_240_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_9, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_5, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_280_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_5, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_500_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_6, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_800_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_7, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 3_000_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_8, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 4_000_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_9, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 5_000_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_10, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},
        new Object[] {10000, 10000, 10000, 0.01, 0.01, 0.01, 1_000_000_000, UserRiskCache.RISK_BUCKET_LEVERAGE_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_1, UserRiskCache.RISK_BUCKET_LEVERAGE_10, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_1,
            UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_2, UserRiskCache.RISK_BUCKET_LEVERAGE_AND_PNL_10, UserRiskCache.RISK_BUCKET_SIZE_1,
            UserRiskCache.RISK_BUCKET_SIZE_1, UserRiskCache.RISK_BUCKET_SIZE_5},};
  }

  @Test
  @Parameters(method = "updateRiskTestValues")
  public void updateRiskTest(long btcPosition, long usdtPosition, long btcusdtPosition, double btcMark, double usdtMark,
      double contractMark, long balanceUpdateQty, int initialMarginRatioBucket, int afterRiskUpdateOneMarginRatioBucket,
      int afterRiskUpdateTwoMarginRatioBucket, int initialLeveragePNLBucket, int afterRiskUpdateOneLeveragePNLBucket,
      int afterRiskUpdateTwoLeveragePNLBucket, int initialNotionalBucket, int afterRiskUpdateOneNotionalBucket,
      int afterRiskUpdateTWONotionalBucket) throws Exception {

    double instrumentQtyScaleFactor = 0.01;
    setIndexFeedUsdMark(btcMark, usdtMark, contractMark);

    User user = nextUser(new Balance(BTC, btcPosition, 0, 0, 0, null,0, TokenType.ERC20), new Balance(USDT, usdtPosition, 0, 0, 0, null,0, TokenType.ERC20),
        new Balance(BTC_USDT_F, btcusdtPosition, 0, 0, 0, null,0, TokenType.ERC20));
    // TODO Add control user at various buckets

    assertPositions(user, (long) (btcPosition / instrumentQtyScaleFactor), (long) (usdtPosition / instrumentQtyScaleFactor),
        (long) (btcusdtPosition / instrumentQtyScaleFactor));

    Assert.assertEquals(0.00, user.getPositionArr()[BTC].getUsdValue(), 2);
    Assert.assertEquals(0.00, user.getPositionArr()[USDT].getUsdValue(), 2);
    Assert.assertEquals(0.00, user.getPositionArr()[BTC_USDT_F].getUsdValue(), 2);

    Assert.assertEquals(0, user.getPositionArr()[USDT].getUsdUnrealized(), 2);
    Assert.assertEquals(0, user.getPositionArr()[BTC].getUsdUnrealized(), 2);
    Assert.assertEquals(0, user.getPositionArr()[BTC_USDT_F].getUsdUnrealized(), 2);

    Assert.assertEquals(0.00, user.getUsdValue(), 2);
    Assert.assertEquals(0.00, user.getUsdUnrealized(), 2);
    Assert.assertEquals(0.00, user.getUsdNotionalPositionValue(), 2);
    Assert.assertEquals(0.00, user.getUsdMarginMaintValue(), 2);
    Assert.assertEquals(0.00, user.getUsdMarginRequiredValue(), 2);

    assertRiskBucketTransition(user, initialMarginRatioBucket, afterRiskUpdateOneMarginRatioBucket, initialLeveragePNLBucket,
        afterRiskUpdateOneLeveragePNLBucket, initialNotionalBucket, afterRiskUpdateOneNotionalBucket);;

    Assert.assertEquals(usdtPosition * usdtMark, user.getPositionArr()[USDT].getUsdValue(), 2);
    Assert.assertEquals(btcPosition * btcMark, user.getPositionArr()[BTC].getUsdValue(), 2);
    Assert.assertEquals(btcusdtPosition * contractMark * usdtMark, user.getPositionArr()[BTC_USDT_F].getUsdValue(), 2);

    Assert.assertEquals(0, user.getPositionArr()[USDT].getUsdUnrealized(), 2);
    Assert.assertEquals(0, user.getPositionArr()[BTC].getUsdUnrealized(), 2);
    Assert.assertEquals(btcusdtPosition * contractMark * usdtMark, user.getPositionArr()[BTC_USDT_F].getUsdUnrealized(), 2);

    Assert.assertEquals((usdtPosition * usdtMark) + (btcPosition * btcMark) + (btcusdtPosition * contractMark * usdtMark),
        user.getUsdValue(), 2);
    Assert.assertEquals(btcusdtPosition * contractMark * usdtMark, user.getUsdUnrealized(), 2);
    Assert.assertEquals(Math.abs(btcusdtPosition * contractMark), user.getUsdNotionalPositionValue(), 2);

    // TODO confirm whether margin values validated from other test
    // Assert.assertEquals(((500_000) * 250 * .0001 ) + ((4950 * 20000 * 0.01 * 1 - 500_000) * 250 * .0001 ) * 2 ,
    // user.getUsdMarginMaintValue(), 2);
    // Assert.assertEquals(((500_000) * 500 * .0001 ) + ((4950 * 20000 * 0.01 * 1 - 500_000) * 500 * .0001 ) * 2 ,
    // user.getUsdMarginRequiredValue(), 2);

    updateBalance(user, UpdateType.PATCH, new Balance(BTC_USDT_F, 0, 0, balanceUpdateQty, 0, null,0, TokenType.ERC20));
    expectMessage("BalanceAdminMessage", "userId=" + user.getId() + ", updateType=PATCH, assetId=" + BTC_USDT_F + ", balance="
        + (btcusdtPosition + balanceUpdateQty) + ", balance_change=" + balanceUpdateQty);

    assertNotionalValueRiskBucket(user, afterRiskUpdateOneNotionalBucket);
    assertNotionalValueRiskBucket(user, afterRiskUpdateOneNotionalBucket);

    assertRiskBucketTransition(user, afterRiskUpdateOneMarginRatioBucket, afterRiskUpdateTwoMarginRatioBucket,
        afterRiskUpdateOneLeveragePNLBucket, afterRiskUpdateTwoLeveragePNLBucket, afterRiskUpdateOneNotionalBucket,
        afterRiskUpdateTWONotionalBucket);

    Assert.assertEquals(usdtPosition * usdtMark, user.getPositionArr()[USDT].getUsdValue(), 2);
    Assert.assertEquals(btcPosition * btcMark, user.getPositionArr()[BTC].getUsdValue(), 2);
    Assert.assertEquals((btcusdtPosition + balanceUpdateQty) * contractMark * usdtMark, user.getPositionArr()[BTC_USDT_F].getUsdValue(), 2);

    Assert.assertEquals(0, user.getPositionArr()[USDT].getUsdUnrealized(), 2);
    Assert.assertEquals(0, user.getPositionArr()[BTC].getUsdUnrealized(), 2);
    Assert.assertEquals((btcusdtPosition + balanceUpdateQty) * contractMark * usdtMark, user.getPositionArr()[BTC_USDT_F].getUsdValue(), 2);

    Assert.assertEquals(
        (usdtPosition * usdtMark) + (btcPosition * btcMark) + ((btcusdtPosition + balanceUpdateQty) * contractMark * usdtMark),
        user.getUsdValue(), 2);
    Assert.assertEquals((btcusdtPosition + balanceUpdateQty) * contractMark * usdtMark, user.getUsdUnrealized(), 2);
    Assert.assertEquals(Math.abs((btcusdtPosition + balanceUpdateQty) * contractMark), user.getUsdNotionalPositionValue(), 2);

    // TODO confirm whether margin values validated from other test
    // Assert.assertEquals(((500_000) * 250 * .0001 ) + ((4950 * 40000 * 0.01 * 1 - 500_000) * 250 * .0001 ) * 2 ,
    // user.getUsdMarginMaintValue(), 2);
    // Assert.assertEquals(((500_000) * 500 * .0001 ) + ((4950 * 40000 * 0.01 * 1 - 500_000) * 500 * .0001 ) * 2 ,
    // user.getUsdMarginRequiredValue(), 2);

    assertPositions(user, (long) (btcPosition / instrumentQtyScaleFactor), (long) (usdtPosition / instrumentQtyScaleFactor),
        (long) ((btcusdtPosition + balanceUpdateQty) / instrumentQtyScaleFactor));

  }
}
