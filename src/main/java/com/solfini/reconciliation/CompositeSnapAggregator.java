package com.solfini.reconciliation;

import com.solfini.instrument.Position;
import java.util.Set;

/**
 * Sums one user's snapshot withdrawable value across a composite's member instrument IDs. Replaces
 * the old per-network hardcoded ignore/allow-list branch (reconcile()'s pre-composite shape) with a
 * single membership check that works for both a Grouped composite (multiple member ids, e.g.
 * Ethereum's USDC+USDT) and a Segregated one (a single member id, e.g. XDC's XUSDC or native XDC).
 */
public class CompositeSnapAggregator {

  private CompositeSnapAggregator() {
  }

  public static double sumMemberWithdrawable(final Position[] positions, final Set<Integer> memberInstrumentIds) {
    if (positions == null) {
      return 0.0;
    }
    double total = 0.0;
    for (final Position p : positions) {
      if (p != null && p.getQuantity() != 0 && memberInstrumentIds.contains(p.getInstrumentId())) {
        total += p.getUsdValue();
      }
    }
    return total;
  }
}
