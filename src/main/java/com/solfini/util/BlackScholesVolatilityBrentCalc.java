package com.solfini.util;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;

public final class BlackScholesVolatilityBrentCalc {
  public interface Function extends UnivariateFunction {
    public int getIterToSolve();
  }

  private static class BlackScholesVolatilityFunction implements Function {
    private final double stockPrice;
    private final double strikePrice;
    private final double rate;
    private final double time;
    private final double div;

    private final double optionPrice;
    private final boolean isCall;

    private int iterToSolve;

    public BlackScholesVolatilityFunction(final double optionPrice, final boolean isCall, final double stockPrice, final double strikePrice,
        final double rate, final double time, final double div) {
      this.stockPrice = stockPrice;
      this.strikePrice = strikePrice;
      this.rate = rate;
      this.time = time;
      this.div = div;
      this.optionPrice = optionPrice;
      this.isCall = isCall;
      this.iterToSolve = 0;
    }

    public final int getIterToSolve() {
      return iterToSolve;
    }

    public double value(final double x) {
      iterToSolve++;

      // x = sigma
      final double[] result = BlackScholes.calc(stockPrice, strikePrice, rate, x, time, div);

      if (isCall)
        return optionPrice - result[0];
      else
        return optionPrice - result[5];
    }
  }

  public static final double solve(final double optionPrice, final boolean isCall, final double stockPrice, final double strikePrice,
      final double rate, final double time, final double div) {
    final double tolerance = 1e-6;
    final int maxEval = 500;
    final double min = 0;
    final double max = 20;

    final BrentSolver solver = new BrentSolver(tolerance);

    double result = 0;
    try {
      final Function f = new BlackScholesVolatilityFunction(optionPrice, isCall, stockPrice, strikePrice, rate, time, div);
      result = solver.solve(maxEval, f, min, max);
      double yvalue = f.value(result);
      //System.err.println("result=" + result + ", count=" + (f.getIterToSolve() - 1) + ", yvalue=" + yvalue);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public static final void testBrent() {
    double stockPrice = 100;
    double strikePrice = 90;
    double rate = .02;
    double time = 0.5;
    // double sigma = x; // .65; // 1=100%
    double div = 0;
    double optionPrice = 23.161958985583023;
    boolean isCall = true;
    // call price = 23.161958985583023

    final double result = BlackScholesVolatilityBrentCalc.solve(optionPrice, isCall, stockPrice, strikePrice, rate, time, div);
    System.err.println("result=" + result);
  }


  public static final void main(final String[] args) {
    testBrent();
    System.err.println(">>>>>>>>>>>>>>testA");
    // testCalibrateParToUpfront();
    System.err.println(">>>>>>>>>>>>>>testB");
    // testCalibrateParToUpfrontSimple();
    System.err.println(">>>>>>>>>>>>>>testC");
  }
}

