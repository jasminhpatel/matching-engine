package com.solfini.instrument;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import com.solfini.common.Appendable;
import com.solfini.common.Constants;
import uk.co.real_logic.artio.fields.DecimalFloat;

import static com.solfini.instrument.Position.assetIdComparator;

/**
 * 
 * @author Chris Mack
 *
 */
public class Balance implements Appendable, Constants {

  private int assetId;
  private final DecimalFloat balanceAmount = new DecimalFloat();
  private final DecimalFloat balanceChange = new DecimalFloat();
  private int eventType;
  private long orderId;
  private long execId;
  private long secondaryExecId;

  private boolean hasPositionBasisData;
  private double usdCostBasis;
  private double usdAvgCostBasis;
  private double usdValue;
  private double usdUnrealized;
  private double usdRealized;
  private double quotedUsdMark;
  private double settleCoinUsdMark;
  private double settleCoinUnrealized;
  private double settleCoinRealized;

  private Set<long[]> assetIdtreeSet = null; // set contains pairs of [assetId, tokenId]


  public Balance() {}

  public Balance(final Position position, final int scale) {
    this.assetId = position.getInstrumentId();
    this.balanceAmount.value(position.getQuantity());
    this.balanceAmount.scale(scale);
    this.balanceChange.value(0);
    this.balanceChange.scale(0);

    this.hasPositionBasisData = true;
    this.usdCostBasis = position.getUsdCostBasis();
    this.usdAvgCostBasis = position.getUsdAvgCostBasisDouble();
    this.usdValue = position.getUsdValue();
    this.usdUnrealized = position.getUsdUnrealized();
    this.usdRealized = position.getUsdRealizedDouble();
    this.quotedUsdMark = position.getQuotedUsdMark();
    this.settleCoinUsdMark = position.getSettleCoinUsdMark();
    this.settleCoinUnrealized = position.getSettleCoinUnrealized();
    this.settleCoinRealized = position.getSettleCoinRealized();

    final Set<long[]> sourceAssetIdtreeSet = position.getAssetIdtreeSet();
    if (sourceAssetIdtreeSet != null) {
      this.assetIdtreeSet = new TreeSet<long[]>(sourceAssetIdtreeSet);
    } else
      this.assetIdtreeSet = null;
  }

  public Balance(final int assetId, final long balance, final int balance_scale, final long balance_change, final int balance_change_scale,
      final Set<long[]> sourceAssetIdtreeSet) {
    this.assetId = assetId;
    this.balanceAmount.value(balance);
    this.balanceAmount.scale(balance_scale);
    this.balanceChange.value(balance_change);
    this.balanceChange.scale(balance_change_scale);
    this.hasPositionBasisData = false;

    if (sourceAssetIdtreeSet != null) {
      this.assetIdtreeSet = new TreeSet<long[]>(assetIdComparator);
      this.assetIdtreeSet.addAll(sourceAssetIdtreeSet);
    } else
      this.assetIdtreeSet = null;
  }

  public final void set(final Position position, final int scale) {
    this.assetId = position.getInstrumentId();
    this.balanceAmount.value(position.getQuantity());
    this.balanceAmount.scale(scale);
    this.balanceChange.value(0);
    this.balanceChange.scale(0);

    this.hasPositionBasisData = true;
    this.usdCostBasis = position.getUsdCostBasis();
    this.usdAvgCostBasis = position.getUsdAvgCostBasisDouble();
    this.usdValue = position.getUsdValue();
    this.usdUnrealized = position.getUsdUnrealized();
    this.usdRealized = position.getUsdRealizedDouble();
    this.quotedUsdMark = position.getQuotedUsdMark();
    this.settleCoinUsdMark = position.getSettleCoinUsdMark();
    this.settleCoinUnrealized = position.getSettleCoinUnrealized();
    this.settleCoinRealized = position.getSettleCoinRealized();

    final Set<long[]> sourceAssetIdtreeSet = position.getAssetIdtreeSet();
    if (sourceAssetIdtreeSet != null) {
      this.assetIdtreeSet = new TreeSet<long[]>(assetIdComparator);
      this.assetIdtreeSet.addAll(sourceAssetIdtreeSet);
    } else
      this.assetIdtreeSet = null;
  }

  public int getAssetId() {
    return assetId;
  }

  public void setAssetId(final int assetId) {
    this.assetId = assetId;
  }

  public DecimalFloat getBalance() {
    return balanceAmount;
  }

  public void setBalance(final long value, final int scale) {
    this.balanceAmount.value(value);
    this.balanceAmount.scale(scale);
  }

  public final DecimalFloat getBalanceChange() {
    return balanceChange;
  }

  public final void setBalanceChange(final long value, final int scale) {
    this.balanceChange.value(value);
    this.balanceChange.scale(scale);
  }

  public final int getEventType() {
    return eventType;
  }

  public final void setEventType(final int eventType) {
    this.eventType = eventType;
  }

  public final long getOrderId() {
    return orderId;
  }

  public final void setOrderId(final long orderId) {
    this.orderId = orderId;
  }

  public final long getExecId() {
    return execId;
  }

  public final void setExecId(final long execId) {
    this.execId = execId;
  }

  public final long getSecondaryExecId() {
    return secondaryExecId;
  }

  public final void setSecondaryExecId(final long secondaryExecId) {
    this.secondaryExecId = secondaryExecId;
  }

  public final double getUsdCostBasis() {
    return usdCostBasis;
  }

  public final void setUsdCostBasis(final double usdCostBasis) {
    this.usdCostBasis = usdCostBasis;
  }

  public final double getUsdAvgCostBasis() {
    return usdAvgCostBasis;
  }

  public final void setUsdAvgCostBasis(final double usdAvgCostBasis) {
    this.usdAvgCostBasis = usdAvgCostBasis;
  }

  public final double getUsdValue() {
    return usdValue;
  }

  public final void setUsdValue(final double usdValue) {
    this.usdValue = usdValue;
  }

  public final double getUsdUnrealized() {
    return usdUnrealized;
  }

  public final void setUsdUnrealized(final double usdUnrealized) {
    this.usdUnrealized = usdUnrealized;
  }

  public final double getUsdRealized() {
    return usdRealized;
  }

  public final void setUsdRealized(final double usdRealized) {
    this.usdRealized = usdRealized;
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


  public final Set<long[]> getAssetIdtreeSet() {
    return assetIdtreeSet;
  }

  public final void setAssetIdtreeSet(final Set<long[]> assetIdtreeSet) {
    this.assetIdtreeSet = assetIdtreeSet;
  }

  public final void addAssetId(final long assetId, final int tokenId) {
    if (assetId == 0 && tokenId == 0)
      return;

    if (assetIdtreeSet == null)
      assetIdtreeSet = new TreeSet<>(new AssetIdComparator());

    final long[] value = {assetId, tokenId};
    assetIdtreeSet.add(value);
  }

  public final void removeAssetId(final long assetId, final int tokenId) {
    if (assetId == 0)
      return;

    if (assetIdtreeSet == null)
      assetIdtreeSet = new TreeSet<>(new AssetIdComparator());

    final long[] value = {assetId, tokenId};
    assetIdtreeSet.remove(value);
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    appendTo(s);
    return s.toString();
  }

  @Override
  public StringBuilder appendTo(final StringBuilder s) {
    return s.append("Balance [assetId=").append(assetId).append(", balance=").append(balanceAmount).append(", balance_change=")
        .append(balanceChange).append(", eventType=").append(eventType).append(ORDERID_EQ).append(orderId).append(EXECID_EQ).append(execId)
        .append(", usdCostBasis=").append(usdCostBasis).append(", usdAvgCostBasis=").append(usdAvgCostBasis).append(USDVALUE_EQ)
        .append(usdValue).append(USDUNREALIZED_EQ).append(usdUnrealized).append(", usdRealized=").append(usdRealized)
        .append(QUOTEDUSDMARK_EQ).append(quotedUsdMark).append(SETTLECOINUSDMARK_EQ).append(settleCoinUsdMark)
        .append(SETTLECOINUNREALIZED_EQ).append(settleCoinUnrealized).append(SETTLECOINREALIZED_EQ).append(settleCoinRealized).append("]");
  }

  public String toJSON() {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\"class\":\"Balance\"").append(",\"assetId\":").append(assetId);
    sb.append(",\"balance\":[").append(balanceAmount.value()).append(",").append(balanceAmount.scale()).append("]");

    sb.append(",\"balance_change\":[").append(balanceChange.value()).append(",").append(balanceChange.scale()).append("]");

    sb.append(",\"eventType\":").append(eventType).append(",\"orderId\":").append(orderId).append(",\"execId\":").append(execId)
        .append(",\"secondaryExecId\":").append(secondaryExecId).append(",\"hasPositionBasisData\":").append(hasPositionBasisData)
        .append(",\"usdCostBasis\":").append(usdCostBasis).append(",\"usdAvgCostBasis\":").append(usdAvgCostBasis).append(",\"usdValue\":")
        .append(usdValue).append(",\"usdUnrealized\":").append(usdUnrealized).append(",\"usdRealized\":").append(usdRealized)
        .append(",\"quotedUsdMark\":").append(quotedUsdMark).append(",\"settleCoinUsdMark\":").append(settleCoinUsdMark)
        .append(",\"settleCoinUnrealized\":").append(settleCoinUnrealized).append(",\"settleCoinRealized\":").append(settleCoinRealized);
    sb.append("}");
    return sb.toString();
  }

  public static final class AssetIdComparator implements Comparator<long[]> {
    @Override
    public int compare(final long[] o1, final long[] o2) {
      if (o1[1] < o2[1])
        return -1;
      if (o1[1] > o2[1])
        return 1;

      if (o1[2] < o2[2])
        return -1;
      if (o1[2] > o2[2])
        return 1;

      return 0;
    }
  }
}
