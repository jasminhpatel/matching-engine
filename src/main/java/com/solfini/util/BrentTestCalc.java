package com.solfini.util;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;

public final class BrentTestCalc {
  public interface Function extends UnivariateFunction {
    public int getIterToSolve();
  }

  private static class TestFunction implements Function {
    private final double y;
    private final double z;
    private int iterToSolve;

    public TestFunction(final double y, final double z) {
      this.y = y;
      this.z = z;
      iterToSolve = 0;
    }

    public final int getIterToSolve() {
      return iterToSolve;
    }

    public double value(final double x) {
      iterToSolve++;
      return Math.pow(x, y) + z;
    }
  }

  public static final void testBrent() {
    final double tolerance = 1e-5;
    final int maxEval = 500;
    final double min = -2;
    final double max = -.2;
    final double y = 23;
    final double z = 0.3;
    final BrentSolver solver = new BrentSolver(tolerance);

    double result = 0;
    try {
      Function f = new TestFunction(y, z);
      result = solver.solve(maxEval, f, min, max);
      double yvalue = f.value(result);
      System.err.println("result=" + result + ", count=" + (f.getIterToSolve() - 1) + ", yvalue=" + yvalue);

    } catch (Exception e) {
      e.printStackTrace();
    }
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

