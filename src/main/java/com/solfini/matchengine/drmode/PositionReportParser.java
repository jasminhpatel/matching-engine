package com.solfini.matchengine.drmode;

import java.util.List;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.common.OneToOneConcurrentArrayQueueCustom;
import com.solfini.instrument.Balance;
import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentCache;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.BalanceAdminMessage;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder;
import com.solfini.sbe.encoder.PositionReportDecoder.PositionsGroupDecoder;
import com.solfini.util.PropertyReader;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class PositionReportParser implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(PositionReportParser.class);
  private final int START_CAPACITY = Integer.parseInt(PropertyReader.getProperty("POSITION_REPORT_PARSER_START_CAPACITY", "131072"));
  private final OneToOneConcurrentArrayQueueCustom<BalanceAdminMessage> pool =
      new OneToOneConcurrentArrayQueueCustom<>(START_CAPACITY, "PositionReportParser");

  public PositionReportParser() {
    for (int i = 0; i < START_CAPACITY; ++i) {
      pool.offer(new BalanceAdminMessage(pool));
    }
  }

  private final BalanceAdminMessage getBalanceAdminMessage() {
    final BalanceAdminMessage message = pool.poll();
    if (message != null)
      return message;
    return new BalanceAdminMessage(pool);
  }

  public final Message parse(final MessageHeaderDecoder headerDecoder, final PositionReportDecoder positionReportDecoder) {
    final BalanceAdminMessage balanceAdminMessage = getBalanceAdminMessage();

    balanceAdminMessage.setUpdateType(UpdateType.PUT);
    final List<Balance> balanceList = balanceAdminMessage.getBalanceList();
    balanceList.clear();

    balanceAdminMessage.setAccount(positionReportDecoder.userId());
    for (PositionsGroupDecoder positionsDecoder : positionReportDecoder.positionsGroup()) {
      final int securityId = positionsDecoder.instrumentId();
      final long quantity = positionsDecoder.quantity();
      final short quantityScale = positionsDecoder.quantityScale();
      final Balance balance = balanceAdminMessage.getCachedBalance();
      balance.setAssetId(securityId);

      // normalize and set quantity
      long normalizedQuantity = quantity;
      final InstrumentPair pair = InstrumentCache.getPair(securityId);
      if (pair != null) {
        for (int i = 0; i < pair.getQuantityScale() - quantityScale; i++)
          normalizedQuantity *= 10;

        balance.setBalance(normalizedQuantity, pair.getQuantityScale());
      } else {
        final Instrument instrument = InstrumentCache.get(securityId);
        if (instrument != null) {
          for (int i = 0; i < instrument.getQuantityScale() - quantityScale; i++)
            normalizedQuantity *= 10;

          balance.setBalance(normalizedQuantity, instrument.getQuantityScale());
        }
      }
      balance.setBalance(quantity, quantityScale);
      balance.setUsdAvgCostBasis(StringUtil.toDouble(positionsDecoder.usdAvgCostBasis(), positionsDecoder.usdAvgCostBasisScale()));
      balance.setUsdCostBasis(StringUtil.toDouble(positionsDecoder.usdCostBasis(), positionsDecoder.usdCostBasisScale()));
      balance.setUsdValue(StringUtil.toDouble(positionsDecoder.usdValue(), positionsDecoder.usdValueScale()));
      balance.setUsdUnrealized(StringUtil.toDouble(positionsDecoder.usdUnrealized(), positionsDecoder.usdUnrealizedScale()));
      balance.setUsdRealized(StringUtil.toDouble(positionsDecoder.usdRealized(), positionsDecoder.usdRealizedScale()));
      balance.setQuotedUsdMark(StringUtil.toDouble(positionsDecoder.quotedUsdMark(), positionsDecoder.quotedUsdMarkScale()));
      balance.setSettleCoinUsdMark(StringUtil.toDouble(positionsDecoder.settleCoinUsdMark(), positionsDecoder.settleCoinUsdMarkScale()));
      balance.setSettleCoinUnrealized(
          StringUtil.toDouble(positionsDecoder.settleCoinUnrealized(), positionsDecoder.settleCoinUnrealizedScale()));
      balance.setSettleCoinRealized(StringUtil.toDouble(positionsDecoder.settleCoinRealized(), positionsDecoder.settleCoinRealizedScale()));

      balanceList.add(balance);
      for (PositionReportDecoder.PositionsGroupDecoder.PositionsAssetIdGroupDecoder positionsAssetIdGroupDecoder : positionsDecoder.positionsAssetIdGroup()) {
        positionsAssetIdGroupDecoder = positionsAssetIdGroupDecoder.next();
      }
    }

    return balanceAdminMessage;
  }

}
