package com.solfini.matchengine;

import java.util.Random;
import com.solfini.common.Constants;
import com.solfini.instrument.Balance;
import com.solfini.instrument.InstrumentCache;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.SecurityDefinitionAdminMessage;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.user.User;
import com.solfini.user.UserCache;

public class RiskPerformanceTest implements Constants {
  protected static final int USDT = 1;
  protected static final int ETH = 2;
  protected static final int BTC = 3;
  protected static final int ETH_USDT_F = 9;
  protected static final int BTC_USDT_F = 12;

  private static SecurityDefinitionAdminMessage createInstrumentDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int priceScale, final int quantityScale) {

    SecurityDefinitionAdminMessage message = new SecurityDefinitionAdminMessage();
    message.setUpdateType(updateType);
    message.setAssetType(AssetType.ASSET);
    message.setSymbol(symbol);
    message.setName(symbol);
    message.setSecurityId(securityId);
    message.setQuantityScale((short) quantityScale);
    message.setPriceScale((short) priceScale);
    message.setOrderBookStrategy(ARRAY_ORDER_BOOK);
    message.setPreOrderCheckStrategy(CASH_PREORDER_CHECK);

    return message;
  }

  private static SecurityDefinitionAdminMessage createInstrumentPairDefinition(final int securityId, final UpdateType updateType,
      final String symbol, final int baseId, final int quotedId, final int priceScale, final int quantityScale) {

    SecurityDefinitionAdminMessage message = createInstrumentDefinition(securityId, updateType, symbol, priceScale, quantityScale);
    message.setAssetType(AssetType.PAIR);
    message.setBaseId(baseId);
    message.setQuotedId(quotedId);

    return message;
  }

  private static User createUser(final int userId, final Balance... balances) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    if (null != balances) {
      for (final Balance balance : balances) {
        message.addBalance(balance);
      }
    }

    UserCache.add(message);

    return UserCache.get(userId);
  }

  public static void main(String[] args) {
    System.out.println("Creating instruments");
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(USDT, UpdateType.PUT, "USDT", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentDefinition(BTC, UpdateType.PUT, "BTC", 2, 2));
    InstrumentCache.updateSecurityDefinition(createInstrumentPairDefinition(BTC_USDT_F, UpdateType.PATCH, "BTC/USDT[F]", BTC, USDT, 2, 2));

    System.out.println("Creating users");
    final Random random = new Random();
    for (int i = 1; i <= 1_000_000; i++) {
      createUser(100 + i, new Balance(USDT, random.nextInt(10_000), 2, 0, 0, null),
          new Balance(BTC_USDT_F, random.nextInt(10_000), 2, 0, 0, null));
      if (i % 100_000 == 0) {
        System.out.println("  ... " + i);
      }
    }

    System.out.println("Computing risk");
    final double[] usdMarkPricesToSet = new double[Math.max(InstrumentCache.getPairCapacity(), 128)];
    while (true) {
      long start = System.currentTimeMillis();
      for (int i = 0; i < 1000; i++) {
        UserCache.processRisk(usdMarkPricesToSet);
      }

      long elapsed = System.currentTimeMillis() - start;
      System.out.println(elapsed + " ms, " + (elapsed / 1000) + " ms/cycle, " + ((1000.0 * 1000 * 1000000) / elapsed) + " computations/s");
    }
  }
}
