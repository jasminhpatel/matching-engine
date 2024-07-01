package com.solfini.matchengine.model.orderbook;

import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.Sector;

public class TestMarginCalc {

  /*
   * could also use private int estimatedUserCount; private int daysFeedIsActive; private double estimatedVolatility; private double
   * estimatedVAR;
   */
  public static final double calcRequiredMargin(double absNotional, final InstrumentPair instrumentPair) {
    if (absNotional < 1_000) {
      return (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01;
    } else {
      double requiredMargin = (1_000) * instrumentPair.getRequiredMarginBasisPoints() * .01;
      absNotional -= 1_000;
      if (absNotional < 10_000) {
        return requiredMargin + (2 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
      } else {
        requiredMargin = requiredMargin + (2 * (10_000) * instrumentPair.getRequiredMarginBasisPoints() * .01);
        absNotional -= 10_000;
        if (absNotional < 50_000) {
          return requiredMargin + (3 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
        } else {
          requiredMargin = requiredMargin + (3 * (50_000) * instrumentPair.getRequiredMarginBasisPoints() * .01);
          absNotional -= 50_000;
          if (absNotional < 100_000) {
            return requiredMargin + (4 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
          } else {
            requiredMargin = requiredMargin + (4 * (100_000) * instrumentPair.getRequiredMarginBasisPoints() * .01);
            absNotional -= 100_000;
            if (absNotional < 500_000) {
              return requiredMargin + (5 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
            } else {
              requiredMargin = requiredMargin + (5 * (500_000) * instrumentPair.getRequiredMarginBasisPoints() * .01);
              absNotional -= 500_000;
              if (absNotional < 1_000_000) {
                return requiredMargin + (6 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
              } else {
                requiredMargin = requiredMargin + (6 * (1_000_000) * instrumentPair.getRequiredMarginBasisPoints() * .01);
                absNotional -= 1_000_000;
                if (absNotional < 5_000_000) {
                  return requiredMargin + (8 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
                } else {
                  requiredMargin = requiredMargin + (8 * (5_000_000) * instrumentPair.getRequiredMarginBasisPoints() * .01);
                  absNotional -= 5_000_000;

                  requiredMargin = requiredMargin + (10 * (absNotional) * instrumentPair.getRequiredMarginBasisPoints() * .01);
                  return requiredMargin;

                }
              }
            }
          }

        }
      }
    }

  }

  /*
   * could also use private int estimatedUserCount; private int daysFeedIsActive; private double estimatedVolatility; private double
   * estimatedVAR;
   */
  public static final double calcMaintMargin(double absNotional, final InstrumentPair instrumentPair) {
    if (absNotional < 1_000) {
      return (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01;
    } else {
      double maintMargin = (1_000) * instrumentPair.getMaintMarginBasisPoints() * .01;
      absNotional -= 1_000;
      if (absNotional < 10_000) {
        return maintMargin + (2 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
      } else {
        maintMargin = maintMargin + (2 * (10_000) * instrumentPair.getMaintMarginBasisPoints() * .01);
        absNotional -= 10_000;
        if (absNotional < 50_000) {
          return maintMargin + (3 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
        } else {
          maintMargin = maintMargin + (3 * (50_000) * instrumentPair.getMaintMarginBasisPoints() * .01);
          absNotional -= 50_000;
          if (absNotional < 100_000) {
            return maintMargin + (4 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
          } else {
            maintMargin = maintMargin + (4 * (100_000) * instrumentPair.getMaintMarginBasisPoints() * .01);
            absNotional -= 100_000;
            if (absNotional < 500_000) {
              return maintMargin + (5 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
            } else {
              maintMargin = maintMargin + (5 * (500_000) * instrumentPair.getMaintMarginBasisPoints() * .01);
              absNotional -= 500_000;
              if (absNotional < 1_000_000) {
                return maintMargin + (6 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
              } else {
                maintMargin = maintMargin + (6 * (1_000_000) * instrumentPair.getMaintMarginBasisPoints() * .01);
                absNotional -= 1_000_000;
                if (absNotional < 5_000_000) {
                  return maintMargin + (8 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
                } else {
                  maintMargin = maintMargin + (8 * (5_000_000) * instrumentPair.getMaintMarginBasisPoints() * .01);
                  absNotional -= 5_000_000;

                  maintMargin = maintMargin + (10 * (absNotional) * instrumentPair.getMaintMarginBasisPoints() * .01);
                  return maintMargin;

                }
              }
            }
          }

        }
      }
    }
  }

  public static void main(String[] args) {
    int maintMarginPercent = 2;
    int requiredMarginPercent = 4;
    InstrumentPair instrumentPair = new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR,
        maintMarginPercent, requiredMarginPercent, 260, 0, Sector.NOT_DEFINED);
    double notional = 500.0;
    double calc = calcMaintMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcMaintMargin=" + calc + ", percent=" + (calc / notional));
    notional = 1500.0;
    calc = calcMaintMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcMaintMargin=" + calc + ", percent=" + (calc / notional));
    notional = 15000.0;
    calc = calcMaintMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcMaintMargin=" + calc + ", percent=" + (calc / notional));
    notional = 50000.0;
    calc = calcMaintMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcMaintMargin=" + calc + ", percent=" + (calc / notional));
    notional = 2500000.0;
    calc = calcMaintMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcMaintMargin=" + calc + ", percent=" + (calc / notional));
    notional = 25000000.0;
    calc = calcMaintMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcMaintMargin=" + calc + ", percent=" + (calc / notional));
    System.out.println("----");
    notional = 500.0;
    calc = calcRequiredMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcRequiredMargin=" + calc + ", percent=" + (calc / notional));
    notional = 1500.0;
    calc = calcRequiredMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcRequiredMargin=" + calc + ", percent=" + (calc / notional));
    notional = 15000.0;
    calc = calcRequiredMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcRequiredMargin=" + calc + ", percent=" + (calc / notional));
    notional = 50000.0;
    calc = calcRequiredMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcRequiredMargin=" + calc + ", percent=" + (calc / notional));
    notional = 2500000.0;
    calc = calcRequiredMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcRequiredMargin=" + calc + ", percent=" + (calc / notional));
    notional = 25_000_000.0;
    calc = calcRequiredMargin(notional, instrumentPair);
    System.out.println("notional=" + notional + ", calcRequiredMargin=" + calc + ", percent=" + (calc / notional));
  }
}
