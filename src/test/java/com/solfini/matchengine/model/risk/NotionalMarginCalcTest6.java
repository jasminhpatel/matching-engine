package com.solfini.matchengine.model.risk;

import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.internal.admin.schema.Sector;
import com.solfini.preordercheck.NotionalMarginCalc;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static com.solfini.preordercheck.NotionalMarginCalc.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Chris Mack
 */
public class NotionalMarginCalcTest6 {

  @Before
  public void before() {
    // do nothing
  }

  @Test
  public void calcImpactDiscountTest() {
    for (int i = 0; i < 1011; i++) {
      System.out.println("" + i * 10 + ", " + NotionalMarginCalc.calcImpactDiscount(null, i * 10));
    }
    assertEquals(0.97, NotionalMarginCalc.calcImpactDiscount(null, 0), .001);
    assertEquals(0.9680599999999999, NotionalMarginCalc.calcImpactDiscount(null, 10), .001);
    assertEquals(0.9661238799999999, NotionalMarginCalc.calcImpactDiscount(null, 20), .00001);
    assertEquals(0.9641916322399999, NotionalMarginCalc.calcImpactDiscount(null, 30), .00001);
    assertEquals(0.96226324897552, NotionalMarginCalc.calcImpactDiscount(null, 40), .00001);
  }

  @Test
  public void calcRequiredMargin0() {
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0, Sector.NOT_DEFINED);
    instrumentPair.setMarginCurveId(0);
    double d = NotionalMarginCalc.calcRequiredMargin(1_000, 1_000, 0, instrumentPair, null);
    double d2 = NotionalMarginCalc.calcMaintMargin(1_000, 1_000, 0, instrumentPair, null);

    assertEquals(50.0, d, .00001);
    assertEquals(25.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000, 100_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000, 100_000, 0, instrumentPair, null);

    assertEquals(5000.0, d, .00001);
    assertEquals(2500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(1_000_000, 1_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(1_000_000, 1_000_000, 0, instrumentPair, null);

    assertEquals(75000.0, d, .00001);
    assertEquals(37500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(10_000_000, 10_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(10_000_000, 10_000_000, 0, instrumentPair, null);

    assertEquals(1625000.0, d, .00001);
    assertEquals(812500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000_000, 100_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000_000, 100_000_000, 0, instrumentPair, null);

    assertEquals(38625000.0, d, .00001);
    assertEquals(19312500.0, d2, .00001);
  }

  @Test
  public void calcRequiredMargin1() {
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0, Sector.NOT_DEFINED);
    instrumentPair.setMarginCurveId(1);

    double d = NotionalMarginCalc.calcRequiredMargin(1_000, 1_000, 0, instrumentPair, null);
    double d2 = NotionalMarginCalc.calcMaintMargin(1_000, 1_000, 0, instrumentPair, null);

    assertEquals(50.0, d, .00001);
    assertEquals(25.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000, 100_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000, 100_000, 0, instrumentPair, null);

    assertEquals(5000.0, d, .00001);
    assertEquals(2500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(1_000_000, 1_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(1_000_000, 1_000_000, 0, instrumentPair, null);

    assertEquals(105000.0, d, .00001);
    assertEquals(52500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(10_000_000, 10_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(10_000_000, 10_000_000, 0, instrumentPair, null);

    assertEquals(2350000.0, d, .00001);
    assertEquals(1175000.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000_000, 100_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000_000, 100_000_000, 0, instrumentPair, null);

    assertEquals(46710000.0, d, .00001);
    assertEquals(23355000.0, d2, .00001);
  }

  @Test
  public void calcRequiredMargin2() {
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0, Sector.NOT_DEFINED);
    instrumentPair.setMarginCurveId(2);
    double d = NotionalMarginCalc.calcRequiredMargin(1_000, 1_000, 0, instrumentPair, null);
    double d2 = NotionalMarginCalc.calcMaintMargin(1_000, 1_000, 0, instrumentPair, null);

    assertEquals(50.0, d, .00001);
    assertEquals(25.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000, 100_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000, 100_000, 0, instrumentPair, null);

    assertEquals(5000.0, d, .00001);
    assertEquals(2500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(1_000_000, 1_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(1_000_000, 1_000_000, 0, instrumentPair, null);

    assertEquals(50000.0, d, .00001);
    assertEquals(25000.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(10_000_000, 10_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(10_000_000, 10_000_000, 0, instrumentPair, null);

    assertEquals(1350000.0, d, .00001);
    assertEquals(675000.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000_000, 100_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000_000, 100_000_000, 0, instrumentPair, null);

    assertEquals(31300000.0, d, .00001);
    assertEquals(15650000.0, d2, .00001);
  }

  @Test
  public void calcRequiredMargin3() {
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0, Sector.NOT_DEFINED);
    instrumentPair.setMarginCurveId(3);

    double d = NotionalMarginCalc.calcRequiredMargin(1_000, 1_000, 0, instrumentPair, null);
    double d2 = NotionalMarginCalc.calcMaintMargin(1_000, 1_000, 0, instrumentPair, null);

    assertEquals(50.0, d, .00001);
    assertEquals(25.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000, 100_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000, 100_000, 0, instrumentPair, null);

    assertEquals(5000.0, d, .00001);
    assertEquals(2500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(1_000_000, 1_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(1_000_000, 1_000_000, 0, instrumentPair, null);

    assertEquals(50000.0, d, .00001);
    assertEquals(25000.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(10_000_000, 10_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(10_000_000, 10_000_000, 0, instrumentPair, null);

    assertEquals(1125000.0, d, .00001);
    assertEquals(562500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000_000, 100_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000_000, 100_000_000, 0, instrumentPair, null);

    assertEquals(27600000.0, d, .00001);
    assertEquals(13800000.0, d2, .00001);
  }

  @Test
  public void calcRequiredMargin100() {
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0, Sector.NOT_DEFINED);
    instrumentPair.setMarginCurveId(100);
    double d = NotionalMarginCalc.calcRequiredMargin(1_000, 1_000, 0, instrumentPair, null);
    double d2 = NotionalMarginCalc.calcMaintMargin(1_000, 1_000, 0, instrumentPair, null);

    assertEquals(50.0, d, .00001);
    assertEquals(25.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000, 100_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000, 100_000, 0, instrumentPair, null);

    assertEquals(5000.0, d, .00001);
    assertEquals(2500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(1_000_000, 1_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(1_000_000, 1_000_000, 0, instrumentPair, null);

    assertEquals(75000.0, d, .00001);
    assertEquals(37500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(10_000_000, 10_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(10_000_000, 10_000_000, 0, instrumentPair, null);

    assertEquals(1625000.0, d, .00001);
    assertEquals(812500.0, d2, .00001);

    d = NotionalMarginCalc.calcRequiredMargin(100_000_000, 100_000_000, 0, instrumentPair, null);
    d2 = NotionalMarginCalc.calcMaintMargin(100_000_000, 100_000_000, 0, instrumentPair, null);

    assertEquals(38625000.0, d, .00001);
    assertEquals(19312500.0, d2, .00001);
  }

  @Test
  public void calculateImpactDiscount() {
    Assert.assertEquals(0.97, calcImpactDiscount(null, (double) (0)), 0.000000000000001);
    Assert.assertEquals(0.950773672051391, calcImpactDiscount(null, (double) (100)), 0.000000000000001);
    Assert.assertEquals(0.8141459353175781, calcImpactDiscount(null, (double) (1000)), 0.000000000000001);
    Assert.assertEquals(0.7513926016356333, calcImpactDiscount(null, (double) (5180)), 0.000000000000001);
    Assert.assertEquals(0.7484304401686964, calcImpactDiscount(null, (double) (9130)), 0.000000000000001);
    Assert.assertEquals(0.7477870634649018, calcImpactDiscount(null, (double) (10100)), 0.000000000000001);
  }


  @Test
  public void calculateMaintAndRequiredMargin() {
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PAIR, 250, 500, 260, 0, Sector.NOT_DEFINED);

    Assert.assertEquals(25.0, calcMaintMargin(1_000, 1_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(50.0, calcRequiredMargin(1_000, 1_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(2500.0, calcMaintMargin(100_000, 100_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(5000.0, calcRequiredMargin(100_000, 100_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(5000.0, calcMaintMargin(200_000, 200_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(10000.0, calcRequiredMargin(200_000, 200_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(12500.0, calcMaintMargin(500_000, 500_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(25000.0, calcRequiredMargin(500_000, 500_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(27500.0, calcMaintMargin(800_000, 800_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(55000.0, calcRequiredMargin(800_000, 800_000, 0, instrumentPair, null), 0.1);

    Assert.assertEquals(37500.0, calcMaintMargin(1_000_000, 1_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(75000.0, calcRequiredMargin(1_000_000, 1_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(87500.0, calcMaintMargin(2_000_000, 2_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(175000.0, calcRequiredMargin(2_000_000, 2_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(312500.0, calcMaintMargin(5_000_000, 5_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(625000.0, calcRequiredMargin(5_000_000, 5_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(612500.0, calcMaintMargin(8_000_000, 8_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(1225000.0, calcRequiredMargin(8_000_000, 8_000_000, 0, instrumentPair, null), 0.1);

    Assert.assertEquals(812500.0, calcMaintMargin(10_000_000, 10_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(1625000.0, calcRequiredMargin(10_000_000, 10_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(2062500.0, calcMaintMargin(20_000_000, 20_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(4125000.0, calcRequiredMargin(20_000_000, 20_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(7312500.0, calcMaintMargin(50_000_000, 50_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(1.4625E7, calcRequiredMargin(50_000_000, 50_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(1.43125E7, calcMaintMargin(80_000_000, 80_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(2.8625E7, calcRequiredMargin(80_000_000, 80_000_000, 0, instrumentPair, null), 0.1);

    Assert.assertEquals(1.93125E7, calcMaintMargin(100_000_000, 100_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(3.8625E7, calcRequiredMargin(100_000_000, 100_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(4.43125E7, calcMaintMargin(200_000_000, 200_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(8.8625E7, calcRequiredMargin(200_000_000, 200_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(1.193125E8, calcMaintMargin(500_000_000, 500_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(2.38625E8, calcRequiredMargin(500_000_000, 500_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(1.943125E8, calcMaintMargin(800_000_000, 800_000_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(3.88625E8, calcRequiredMargin(800_000_000, 800_000_000, 0, instrumentPair, null), 0.1);

    Assert.assertEquals(2.44315E8, calcMaintMargin(1000_010_000, 1000_010_000, 0, instrumentPair, null), 0.1);
    Assert.assertEquals(4.8863E8, calcRequiredMargin(1000_010_000, 1000_010_000, 0, instrumentPair, null), 0.1);

  }

  public static void main(String[] args) {
    System.out.println("");
    InstrumentPair instrumentPair =
        new InstrumentPair(14, "SPY/USD", "SPY/USD", null, null, (short) 2, (short) 2, 2, AssetType.PERPETUAL_SWAP, 40, 80, 260, 6, Sector.NOT_DEFINED); // 40
                                                                                                                                     // bps,
    // 80 bps

    int notional = 1000;
    double value = (double) (1_000);
    double margin = calcRequiredMargin(value, value, 0, instrumentPair, null);
    System.out.println(notional + ", " + margin + ", " + (notional / margin) + ", " + (margin * 100 / notional) + "%");


    for (int i = 0; i < 10; i++) {
      notional = i * 5_000;
      value = (double) (i * 5_000);
      margin = calcRequiredMargin(value, value, 0, instrumentPair, null);
      if (margin != 0)
        System.out.println(notional + ", " + margin + ", " + (notional / margin) + ", " + (margin * 100 / notional) + "%");
    }

    for (int i = 0; i < 5500; i++) {
      notional = i * 10_000;
      value = (double) (i * 10_000);
      margin = calcRequiredMargin(value, value, 0, instrumentPair, null);
      if (margin != 0)
        System.out.println(notional + ", " + margin + ", " + (notional / margin) + ", " + (margin * 100 / notional) + "%");
    }
  }
}
