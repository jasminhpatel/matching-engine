package com.solfini.instrument;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.solfini.common.Appendable;
import com.solfini.common.Constants;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.pool.UserOpenOrdersByPairMatchThreadObjectPool;
import com.solfini.user.User;
import com.solfini.user.UserOpenOrdersByPair;

/**
 *
 * @author Chris Mack
 *
 */
public class Position implements Appendable, Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(Position.class);
  public static final int DEFAULT_COST_BASIS_SCALE = 6;
  public static final int DEFAULT_COST_BASIS_SCALE_MULT = 1_000_000;
  private int instrumentId;
  private long quantity;
  private long availableQuantity;
  private boolean isTouched; // was this position ever not 0?

  private AssetType assetType = AssetType.NULL_VAL;
  private double usdCostBasis;

  private long usdAvgCostBasis;
  private short usdAvgCostBasisScale = DEFAULT_COST_BASIS_SCALE; // defualt to 6
  private int usdAvgCostBasisScaleMultiplier = DEFAULT_COST_BASIS_SCALE_MULT; // defualt to 6


  private double usdValue;
  private double usdUnrealized;
  private double usdRealized;
  private double quotedUsdMark;
  private double settleCoinUsdMark;
  private double settleCoinUnrealized;
  private double settleCoinRealized;
  private UserOpenOrdersByPair userOpenOrdersByPair;
  private int bankruptPriceInt;
  private boolean markAsReturned = false;

  public Position() {
    // default constructor
  }

  public Position(final int instrumentId) {
    this.instrumentId = instrumentId;
  }

  // copy sets the position from the source and user
  // must be called from the matching thread
  // called frequently to copy set execution reports
  public static final Position set(final Position position, final User user, final Position source) {
    position.instrumentId = source.instrumentId;
    position.quantity = source.quantity;
    position.availableQuantity = source.availableQuantity;
    position.assetType = source.assetType;
    position.usdCostBasis = source.usdCostBasis;
    position.usdAvgCostBasis = source.usdAvgCostBasis;
    position.usdAvgCostBasisScale = source.usdAvgCostBasisScale;
    position.usdAvgCostBasisScaleMultiplier = source.usdAvgCostBasisScaleMultiplier;
    position.usdValue = source.usdValue;
    position.usdUnrealized = source.usdUnrealized;
    position.usdRealized = source.usdRealized;
    position.quotedUsdMark = source.quotedUsdMark;
    position.settleCoinUsdMark = source.settleCoinUsdMark;
    position.settleCoinUnrealized = source.settleCoinUnrealized;
    position.settleCoinRealized = source.settleCoinRealized;
    position.bankruptPriceInt = source.bankruptPriceInt;
    position.isTouched = source.isTouched;

    try {
      if (source.assetType != AssetType.ASSET) {
        final InstrumentPair instrumentPair = InstrumentCache.getPair(source.instrumentId);
        if (instrumentPair != null) {
          if (position.userOpenOrdersByPair == null) {
            position.userOpenOrdersByPair = UserOpenOrdersByPairMatchThreadObjectPool.get();
          }
          position.userOpenOrdersByPair.set(user, instrumentPair);
        }
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return position;
  }

  // copy sets the position from the source and user
  // called less frequently when there is a fill or position change
  public static final Position set(final Position position, final User user, final int instrumentId, final long quantity,
      final long availableQuantity) {
    position.instrumentId = instrumentId;
    position.quantity = quantity;
    position.availableQuantity = availableQuantity;
    position.isTouched = true; // change in position

    if (quantity != 0 && user != null)
      user.updateMaxActivePositionIndexHint(instrumentId);

    try {
      final InstrumentPair instrumentPair = InstrumentCache.getPair(instrumentId);
      if (instrumentPair != null) {
        position.assetType = instrumentPair.getAssetType(); // was pair
        if (position.userOpenOrdersByPair == null) {
          position.userOpenOrdersByPair = UserOpenOrdersByPairMatchThreadObjectPool.get();
        }
        position.userOpenOrdersByPair.set(user, instrumentPair);
      } else
        position.assetType = AssetType.ASSET;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return position;
  }

  public void clear() {
    this.instrumentId = 0;
    this.quantity = 0;
    this.availableQuantity = 0;
    this.assetType = AssetType.NULL_VAL;
    this.usdCostBasis = 0;
    this.usdAvgCostBasis = 0;
    this.usdAvgCostBasisScale = DEFAULT_COST_BASIS_SCALE;
    this.usdAvgCostBasisScaleMultiplier = DEFAULT_COST_BASIS_SCALE_MULT;
    this.usdValue = 0;
    this.usdUnrealized = 0;
    this.usdRealized = 0;
    this.quotedUsdMark = 0;
    this.settleCoinUsdMark = 0;
    this.settleCoinUnrealized = 0;
    this.settleCoinRealized = 0;
    this.bankruptPriceInt = 0;
    this.isTouched = false;

    if (userOpenOrdersByPair != null) {
      userOpenOrdersByPair.clear();
    }
  }

  public final boolean isTouched() {
    return isTouched;
  }

  public final void touched() {
    this.isTouched = true;
  }

  public final long getQuantity() {
    return quantity;
  }

  public final void setQuantity(final long quantity) {
    this.quantity = quantity;
  }

  public final void addQuantity(final long quantity) {
    this.quantity += quantity;
  }

  public final long getAvailableQuantity() {
    return availableQuantity;
  }

  public final void setAvailableQuantity(final long availableQuantity) {
    this.availableQuantity = availableQuantity;
  }

  public final int getInstrumentId() {
    return instrumentId;
  }

  public final void setInstrumentId(int id) {
    instrumentId = id;
  }

  public final void addAvailableQuantity(final long quantity) {
    this.availableQuantity += quantity;
  }

  public final boolean subtractAvailableQuantity(final long quantity) {
    if (availableQuantity >= quantity) {
      this.availableQuantity -= quantity;
      return true;
    }
    return false;
  }

  public final double getUsdCostBasis() {
    return usdCostBasis;
  }

  public final void setUsdCostBasis(final double usdCostBasis) {
    this.usdCostBasis = usdCostBasis;
  }

  public final void addUsdCostBasis(final double usdCostBasisChange) {
    this.usdCostBasis += usdCostBasisChange;
  }

  public final double getUsdValue() {
    return usdValue;
  }

  public final void setUsdValue(final long usdValue) {
    this.usdValue = usdValue;
  }

  public final double getUsdUnrealized() {
    return usdUnrealized;
  }

  public final void setUsdUnrealized(final long usdUnrealized) {
    this.usdUnrealized = usdUnrealized;
  }

  public final AssetType getAssetType() {
    return assetType;
  }

  public final void setAssetType(final AssetType assetType) {
    this.assetType = assetType;
  }

  public final long getUsdAvgCostBasis() {
    return usdAvgCostBasis;
  }

  public final void setUsdAvgCostBasis(final long usdAvgCostBasis) {
    this.usdAvgCostBasis = usdAvgCostBasis;
  }

  public final short getUsdAvgCostBasisScale() {
    return usdAvgCostBasisScale;
  }

  public final int getUsdAvgCostBasisScaleMultiplier() {
    return usdAvgCostBasisScaleMultiplier;
  }

  public final void setUsdAvgCostBasisScale(final short usdAvgCostBasisScale) {
    this.usdAvgCostBasisScale = usdAvgCostBasisScale;
  }

  public final double getUsdAvgCostBasisDouble() {
    double d = usdAvgCostBasis;
    for (int i = 0; i < usdAvgCostBasisScale; i++)
      d = d * .1;
    return d;
  }

  public final void setUsdAvgCostBasisDouble(final double usdAvgCostBasis) {
    double d = usdAvgCostBasis;
    for (int i = 0; i < usdAvgCostBasisScale; i++)
      d = d * 10;

    this.usdAvgCostBasis = (long) d;
  }

  public final double getUsdRealizedDouble() {
    return usdRealized;
  }

  public final void setUsdRealized(final double usdRealized) {
    this.usdRealized = usdRealized;
  }

  public final void addUsdRealized(final double usdRealized) {
    this.usdRealized += usdRealized;
  }

  public final void setUsdValue(final double usdValue) {
    this.usdValue = usdValue;
  }

  public final void setUsdUnrealized(final double usdUnrealized) {
    this.usdUnrealized = usdUnrealized;
  }

  public final void addUsdUnrealized(final double usdUnrealized) {
    this.usdUnrealized += usdUnrealized;
  }

  public final double getSettleCoinUnrealized() {
    return settleCoinUnrealized;
  }

  public final void setSettleCoinUnrealized(final double settleCoinUnrealized) {
    this.settleCoinUnrealized = settleCoinUnrealized;
  }

  public final double getSettleCoinRealized() {
    return settleCoinRealized;
  }

  public final void setSettleCoinRealized(final double settleCoinRealized) {
    this.settleCoinRealized = settleCoinRealized;
  }

  public final void addSettleCoinRealized(final double settleCoinRealized) {
    this.settleCoinRealized += settleCoinRealized;
  }

  public final double getQuotedUsdMark() {
    return quotedUsdMark;
  }

  public final void setQuotedUsdMark(final double quotedUsdMark) {
    this.quotedUsdMark = quotedUsdMark;
  }

  public final double getSettleCoinUsdMark() {
    return settleCoinUsdMark;
  }

  public final void setSettleCoinUsdMark(final double settleCoinUsdMark) {
    this.settleCoinUsdMark = settleCoinUsdMark;
  }

  public final void setUserOpenOrdersByPair(final UserOpenOrdersByPair userOpenOrdersByPair) {
    this.userOpenOrdersByPair = userOpenOrdersByPair;
  }

  public final UserOpenOrdersByPair getUserOpenOrdersByPair() {
    return userOpenOrdersByPair;
  }

  public final int getOpenOrdersCount() {
    if (userOpenOrdersByPair == null)
      return 0;
    return userOpenOrdersByPair.getBidsCount() + userOpenOrdersByPair.getAsksCount();
  }

  public final int getBankruptPriceInt() {
    return bankruptPriceInt;
  }

  public final void setBankruptPriceInt(final int bankruptPriceInt) {
    this.bankruptPriceInt = bankruptPriceInt;
  }

  public void resetMarkAsReturned() {
    markAsReturned = false;
  }

  public void markAsReturned() {
    markAsReturned = true;
  }

  public boolean isMarkAsReturned() {
    return markAsReturned;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("Position [instrumentId=").append(instrumentId).append(QUANTITY_EQ).append(quantity).append(", availableQuantity=")
        .append(availableQuantity).append(ASSETTYPE_EQ).append(assetType).append(", usdCostBasis=").append(usdCostBasis)
        .append(", usdAvgCostBasis=").append(getUsdAvgCostBasisDouble()).append(USDVALUE_EQ).append(usdValue).append(USDUNREALIZED_EQ)
        .append(usdUnrealized).append(", usdRealized=").append(usdRealized).append(QUOTEDUSDMARK_EQ).append(quotedUsdMark)
        .append(SETTLECOINUSDMARK_EQ).append(settleCoinUsdMark).append(SETTLECOINUNREALIZED_EQ).append(settleCoinUnrealized)
        .append(SETTLECOINREALIZED_EQ).append(settleCoinRealized).append(", bankruptPriceInt=").append(bankruptPriceInt).append("]");
  }

  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"Position\"").append(",\"instrumentId\":").append(instrumentId).append(",\"quantity\":").append(quantity)
        .append(",\"availableQuantity\":").append(availableQuantity).append(",\"assetType\":").append(assetType.value())
        .append(",\"usdCostBasis\":").append(usdCostBasis).append(",\"usdAvgCostBasis\":").append(getUsdAvgCostBasisDouble())
        .append(",\"usdValue\":").append(usdValue).append(",\"usdUnrealized\":").append(usdUnrealized).append(",\"usdRealized\":")
        .append(usdRealized).append(",\"quotedUsdMark\":").append(quotedUsdMark).append(",\"settleCoinUsdMark\":").append(settleCoinUsdMark)
        .append(",\"settleCoinUnrealized\":").append(settleCoinUnrealized).append(",\"settleCoinRealized\":").append(settleCoinRealized)
        .append(",\"bankruptPriceInt\":").append(bankruptPriceInt);
    sb.append("}");
    return sb.toString();
  }
}
