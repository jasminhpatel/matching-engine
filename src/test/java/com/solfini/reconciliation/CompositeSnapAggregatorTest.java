package com.solfini.reconciliation;

import static org.junit.Assert.assertEquals;

import com.solfini.instrument.Position;
import java.util.Set;
import org.junit.Test;

public class CompositeSnapAggregatorTest {

  private static final int USDC_ID = 2;
  private static final int USDT_ID = 3;
  private static final int XUSDC_ID = 3855;

  private static Position position(final int instrumentId, final long quantity, final double usdValue) {
    final Position p = new Position(instrumentId);
    p.setQuantity(quantity);
    p.setUsdValue(usdValue);
    return p;
  }

  @Test
  public void sumMemberWithdrawable_singleMemberInstrument_returnsItsUsdValue() {
    final Position[] positions = { position(USDC_ID, 100L, 1500.0) };

    final double total = CompositeSnapAggregator.sumMemberWithdrawable(positions, Set.of(USDC_ID));

    assertEquals(1500.0, total, 0.0001);
  }

  @Test
  public void sumMemberWithdrawable_instrumentNotInMembership_isExcluded() {
    final Position[] positions = { position(USDC_ID, 100L, 1500.0) };

    final double total = CompositeSnapAggregator.sumMemberWithdrawable(positions, Set.of(XUSDC_ID));

    assertEquals(0.0, total, 0.0001);
  }

  @Test
  public void sumMemberWithdrawable_groupedComposite_sumsAcrossBothMemberInstruments() {
    final Position[] positions = { position(USDC_ID, 100L, 1000.0), position(USDT_ID, 50L, 500.0) };

    final double total = CompositeSnapAggregator.sumMemberWithdrawable(positions, Set.of(USDC_ID, USDT_ID));

    assertEquals(1500.0, total, 0.0001);
  }

  @Test
  public void sumMemberWithdrawable_zeroQuantityPosition_isExcludedEvenIfMember() {
    final Position[] positions = { position(USDC_ID, 0L, 1500.0) };

    final double total = CompositeSnapAggregator.sumMemberWithdrawable(positions, Set.of(USDC_ID));

    assertEquals(0.0, total, 0.0001);
  }

  @Test
  public void sumMemberWithdrawable_nullPositionsArray_returnsZero() {
    final double total = CompositeSnapAggregator.sumMemberWithdrawable(null, Set.of(USDC_ID));

    assertEquals(0.0, total, 0.0001);
  }

  @Test
  public void sumMemberWithdrawable_nullElementInArray_isSkipped() {
    final Position[] positions = { null, position(USDC_ID, 100L, 1500.0) };

    final double total = CompositeSnapAggregator.sumMemberWithdrawable(positions, Set.of(USDC_ID));

    assertEquals(1500.0, total, 0.0001);
  }
}
