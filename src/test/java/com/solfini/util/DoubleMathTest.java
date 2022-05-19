package com.solfini.util;

import net.openhft.chronicle.core.Maths;
import org.junit.Assert;
import org.junit.Test;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import static org.junit.Assert.assertEquals;

public class DoubleMathTest {
  public static final int N_ITERATIONS = 1_000_000;
  public static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-HH:mm:ss.SSS");

  public static final long price = 8_000_321_321L; // 8_000_321_321L;
  public static final long price2 = 8_000_000_000L;

  public static final int price_scale = 6;
  public static final int price_scale_mult = 1_000_000;
  public static final int price_scale_mult2 = 100_000_000;


  public static final long qty = 20_000_000_000L;
  public static final int qty_scale = 6;
  public static final int qty_scale_mult = 1_000_000;

  public static final long cost = 30_003_003L;


  @Test
  public void multTest() {
    double d = (((double) price / price_scale_mult) * ((double) qty / qty_scale_mult));
    long dl = (long) (d * qty_scale_mult);

    long l = ((price * qty) / price_scale_mult) / qty_scale_mult;
    long m = MbxMath.multiplyQtyAndPrice(price, price_scale_mult, qty, qty_scale_mult);
    long m2 = MbxMath.multiplyQtyAndPrice(price2, price_scale_mult, qty, qty_scale_mult);
    long l2 = (price / price_scale_mult * qty / qty_scale_mult);

    System.out.println("\nmultTest");

    System.out.println("d=" + d);
    System.out.println("dl=" + dl);

    System.out.println("l=" + l);
    System.out.println("m=" + m);
    System.out.println("m2=" + m2);

    System.out.println("l2=" + l2);

    assertEquals(m, dl);

    // warmup
    for (int i = 0; i < 1_000_000; i++) {
      double td = (((double) price / price_scale_mult) * ((double) qty / qty_scale_mult));
      long tdl = (long) (td * qty_scale_mult);
    }
    for (int i = 0; i < 1_000_000; i++) {
      long tm = MbxMath.multiplyQtyAndPrice(price, price_scale_mult, qty, qty_scale_mult);
    }

    // benchmark
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 100_000_000; i++) {
      double td = (((double) price / price_scale_mult) * ((double) qty / qty_scale_mult));
      long tdl = (long) (td * qty_scale_mult);
    }
    System.out.println("tdl t0=" + (System.currentTimeMillis() - t0));

    long t1 = System.currentTimeMillis();
    for (int i = 0; i < 100_000_000; i++) {
      long tm = MbxMath.multiplyQtyAndPrice(price, price_scale_mult, qty, qty_scale_mult);
    }
    System.out.println("tm t1=" + (System.currentTimeMillis() - t1));
  }

  @Test
  public void multTest2() {
    // .7 x 287.59 = 201.313
    long usdNotionalLong = MbxMath.multiplyQtyAndPrice(700000, 1000000, 28759, 100);
    Assert.assertEquals(20131, usdNotionalLong);

    // .7 x 287.590 = 201.313
    usdNotionalLong = MbxMath.multiplyQtyAndPrice(700000000, 1000000, 1271559000000L, 100000000);
    Assert.assertEquals(890091300000000L, usdNotionalLong);

    // .7 x 287.590 = 201.313
    usdNotionalLong = MbxMath.multiplyQtyAndPrice(700000, 1000000, 28759, 1000);
    Assert.assertEquals(20131, usdNotionalLong);

    usdNotionalLong = MbxMath.multiplyQtyAndPrice(7000, 1000, 28759, 100);
    Assert.assertEquals(201313, usdNotionalLong);

    usdNotionalLong = MbxMath.multiplyQtyAndPrice2(700000, 1000000, 28759, 100);
    Assert.assertEquals(201313000, usdNotionalLong);
  }

  @Test
  public void divideTest() {
    long m = MbxMath.multiplyQtyAndPrice(price, price_scale_mult, qty, qty_scale_mult);
    double d = (double) m / cost; // d=5333013.712660696
    long l = 10 / cost;
    long mc = m / cost; // loses digits by truncating
    long mc2 = m * price_scale_mult / cost; // overflows

    System.out.println("\ndivideTest");
    System.out.println("d=" + d);
    System.out.println("l=" + l);
    System.out.println("m=" + m); // 160,006,426.420000
    System.out.println("mc=" + mc);
    System.out.println("mc2=" + mc2);



    long md = MbxMath.divide(m, cost, price_scale_mult2);
    System.out.println("md=" + md);
    assertEquals(md, (long) (d * price_scale_mult2));

    double d2 = (double) 1 / 3;
    long md2 = MbxMath.divide(1, 3, price_scale_mult2);
    System.out.println("d2=" + d2);
    System.out.println("md2=" + md2);
    assertEquals(md2, (long) (d2 * price_scale_mult2));

    double d3 = (double) 1 / 30000000000L;
    long md3 = MbxMath.divide(1, 30000000000L, 1000_000_000_000_000L);
    System.out.println("d3=" + d3);
    System.out.println("md3=" + md3);
    assertEquals(md3, (long) (d3 * 1000_000_000_000_000L));

    // benchmark
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 100_000_000; i++) {
      double td2 = (double) 1 / 3;
    }
    System.out.println("tdl t0=" + (System.currentTimeMillis() - t0));

    long t1 = System.currentTimeMillis();
    for (int i = 0; i < 100_000_000; i++) {
      long tmd2 = MbxMath.divide(1, 3, price_scale_mult2);
    }
    System.out.println("tm t1=" + (System.currentTimeMillis() - t1));


  }

  @Test
  public void bigDecimalTest() {
    BigDecimal bd1 = new BigDecimal(1d);
    BigDecimal bd2 = new BigDecimal(3d);
    BigDecimal bd3 = bd1.divide(bd2, 8, BigDecimal.ROUND_HALF_UP);

    Assert.assertEquals(0.33333333, bd3.doubleValue(), 0.00000001);

    double d = 1d / 3d;
    Assert.assertEquals(0.3333333333333333, d, 0.000000000000001);
    Assert.assertEquals(3333.333333333333, d + 3333, 0.000000000001);
  }

  @Test
  public void doubleTest() {
    double d = 1d / 3d;
    Assert.assertEquals(0.3333333333333333, d, 0.0000000000000001);

    double d2 = 0.899999999999999;
    Assert.assertEquals(0.899999999999999, d2, 0.0000000000000001);

    double d3 = 0.000000000000000009;
    Assert.assertEquals(0.000000000000000009, d3, 0.0000000000000000001);

    double d4 = 0.000000000000000009;
    Assert.assertEquals(0.000000000000000009, d4, 0.0000000000000000001);


    double d5 = Maths.roundN(0.899999999999999, 10);
    Assert.assertEquals(0.9, d5, 0.000000000001);

    double d6 = Maths.roundN(0.89999999999999999, 10);
    Assert.assertEquals(0.9, d6, 0.000000000001);

    double d7 = Maths.roundN(12345.89999999999999999, 10);
    Assert.assertEquals(12345.9, d7, 0.000000000001);

    double d8 = Maths.roundN(12345.123456789123, 8);
    Assert.assertEquals(12345.12345679, d8, 0.000000000001);

    double d9 = Maths.roundN(12345.12345678912345678, 15);
    Assert.assertEquals(12345.123456789124, d9, 0.0000000000000001);

    double d10 = Maths.roundN(12345.1000100215001, 15);
    Assert.assertEquals(12345.1000100215001, d10, 0.0000000000000001);

    double d11 = Maths.roundN(1234.1000000001, 10);
    Assert.assertEquals(1234.1000000001, d11, 0.0000000000000001);

    Assert.assertEquals(Long.MAX_VALUE, 9223372036854775807L);

  }


  @Test
  public void mbxRoundToBestPrecision() {
    // benchmark
    Assert.assertEquals(1234.1, MbxMath.roundToBestPrecision(1234.100), 0.00000001);
    MbxMath.roundToBestPrecision(1234.100);
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 100_000_000; i++) {
      MbxMath.roundToBestPrecision(1234.100);
    }
    System.out.println("tdl t0=" + (System.currentTimeMillis() - t0));
  }


  @Test
  public void doubleMathsTest() {
    Assert.assertEquals(4, MbxMath.digitsAfterDecimal(1234.100));
    Assert.assertEquals(1, MbxMath.digitsAfterDecimal(1.100));
    Assert.assertEquals(0, MbxMath.digitsAfterDecimal(0.100));
    Assert.assertEquals(0, MbxMath.digitsAfterDecimal(0.00100));

    Assert.assertEquals(1234.1, MbxMath.roundToBestPrecision(1234.100), 0.000000000000001);
    Assert.assertEquals(1.1, MbxMath.roundToBestPrecision(1.100), 0.000000000000001);
    Assert.assertEquals(0.1, MbxMath.roundToBestPrecision(0.100), 0.000000000000001);
    Assert.assertEquals(0.001, MbxMath.roundToBestPrecision(0.00100), 0.000000000000001);
    Assert.assertEquals(0.002, MbxMath.roundToBestPrecision(0.0019999999999999999999), 0.000000000000001);
    Assert.assertEquals(0.002, MbxMath.roundToBestPrecision(0.0019999999999999), 0.000000000000001);

    Assert.assertEquals(0.002, MbxMath.roundToBestPrecision(0.001999999999999), 0.000000000000001);
    Assert.assertEquals(0.002, MbxMath.roundToBestPrecision(0.00199999999999), 0.000000000000001);
    Assert.assertEquals(0.0019999999999, MbxMath.roundToBestPrecision(0.0019999999999), 0.000000000000001);
    Assert.assertEquals(0.001999999999, MbxMath.roundToBestPrecision(0.001999999999), 0.000000000000001);
    Assert.assertEquals(0.00199999999, MbxMath.roundToBestPrecision(0.00199999999), 0.000000000000001);


    Assert.assertEquals(0.0019999999, MbxMath.roundToBestPrecision(0.0019999999), 0.000000000000001);
    Assert.assertEquals(0.0010000000001, MbxMath.roundToBestPrecision(0.0010000000001), 0.000000000000001);
    Assert.assertEquals(0.0010000000001, MbxMath.roundToBestPrecision(0.0010000000001), 0.000000000000001);

    Assert.assertEquals(0.001, MbxMath.roundToBestPrecision(0.001000000000001), 0.000000000000001);
    Assert.assertEquals(0.001, MbxMath.roundToBestPrecision(0.001000000000001), 0.000000000000001);
    Assert.assertEquals(0.001, MbxMath.roundToBestPrecision(0.001000000000001), 0.000000000000001);
    Assert.assertEquals(0.001, MbxMath.roundToBestPrecision(0.001000000000001), 0.000000000000001);


    double dollar = 1.00;
    double dime = 0.10;
    int number = 7;

    double result = dollar - number * dime;
    Assert.assertEquals(0.3, MbxMath.roundToBestPrecision(result), 0.0000000000001);

    result = 1d - result;
    Assert.assertEquals(0.7, MbxMath.roundToBestPrecision(result), 0.0000000000001);

    result = 12345 + result;
    Assert.assertEquals(12345.7, MbxMath.roundToBestPrecision(result), 0.0000000000001);

    double tradePnl = 28.878 - (288.5 * .1);

    tradePnl = MbxMath.roundToBestPrecision(tradePnl);
    Assert.assertEquals(0.028, tradePnl, 0.0000000000001);

    double settleCoinRealized = MbxMath.roundToBestPrecision(tradePnl / 1.0);
    Assert.assertEquals(0.028, settleCoinRealized, 0.0000000000001);

    tradePnl = -0.091000000000008;
    settleCoinRealized = MbxMath.roundToBestPrecision(tradePnl / 1.0);
    Assert.assertEquals(-0.091, settleCoinRealized, 0.0000000000001);
  }

  @Test
  public void mbxMathDivideTest() {
    long tmd2 = MbxMath.divide(-8_000_321_321L, cost, price_scale_mult2); // tmd2=26665068563
    Assert.assertEquals(-26665068563L, tmd2);
    System.out.println("tmd2=" + tmd2);

  }

  public static void main(String[] args) {
    DoubleMathTest test = new DoubleMathTest();
    test.mbxRoundToBestPrecision();
  }
}
