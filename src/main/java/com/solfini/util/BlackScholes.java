package com.solfini.util;

import java.util.Arrays;

import com.solfini.common.CustomLogger;

// This defines a number of functions related to the Black-Scholes
// option pricing formula. This includes Black-Scholes call and put
// prices, Black-Scholes call and put implied volatilities and the various
// option greeks - delta, gamma, vega, theta and rho
// https://github.com/jrvarma/black-scholes-java/blob/master/src/BlackScholes.java
// test with https://goodcalculators.com/black-scholes-calculator/
/**
 *
 * @author Chris Mack
 *
 *         to calculate d1 of Black-Scholes
 *
 * @param stockPrice | stock price
 * @param x | strike price
 * @param rate | risk free rate
 * @param time | time to maturity in years
 * @param v | implied volatility of returns of underlying asset/stock return d1
 */


public class BlackScholes {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(BlackScholes.class);

  public static final double vHigh = 100;
  public static final double vvHigh = 1E+30;
  // The constants
  public static final double PI = 3.141592653589793;
  public static final double A1 = 0.319381530;
  public static final double A2 = -0.356563782;
  public static final double A3 = 1.781477937;
  public static final double A4 = -1.821255978;
  public static final double A5 = 1.330274429;
  public static final double CDF_GAMMA = 0.2316419;
  public static final double CDF_PRECISION = 1e-6;

  // This computes the BlackScholes quantity d1 safely i.e.
  // no division by zero and no log of zero
  private static final double safeD1(final double stockPrice, final double strikePrice, final double sigma, final double time,
      final double adjRate) {
    double s0;
    if (sigma == 0 || time == 0) {
      s0 = stockPrice * Math.exp((adjRate + sigma * sigma / 2) * time);
      if (s0 > strikePrice) {
        return vHigh;
      }
      if (s0 < strikePrice) {
        return -vHigh;
      }
      return 0.0;
    } else {
      if (strikePrice == 0) {
        return vHigh;
      } else {
        // Below is the BlackScholes formula for d1
        return (Math.log(stockPrice / strikePrice) + (adjRate + sigma * sigma / 2) * time) / (sigma * Math.sqrt(time));
      }
    }
  }

  private BlackScholes() {}

  public static final double[] calc(final double stockPrice, final double strikePrice, final double rate, final double sigma,
      final double time, final double div_yield) {
    final double[] result = new double[12];
    final double adjRate = rate - div_yield;
    final double d1 = safeD1(stockPrice, strikePrice, sigma, time, adjRate);
    final double d2 = d1 - sigma * Math.sqrt(time);

    // Black Scholes call price
    result[0] = Math.exp(-rate * time) * (stockPrice * Math.exp(adjRate * time) * normalCDF(d1) - strikePrice * normalCDF(d2)); // call px
    result[1] = Math.exp(-div_yield * time) * normalCDF(d1); // call delta
    result[2] = callTheta(d1, d2, stockPrice, strikePrice, rate, sigma, time, div_yield); // callTheta
    result[3] = strikePrice * time * Math.exp(-rate * time) * normalCDF(d2); // callRho
    result[4] = normalCDF(d2); // call risk neutral probability of put exercise

    result[5] = Math.exp(-rate * time) * (-stockPrice * Math.exp(adjRate * time) * normalCDF(-d1) + strikePrice * normalCDF(-d2)); // put px
    result[6] = -Math.exp(-div_yield * time) * normalCDF(-d1); // put delta
    result[7] = putTheta(d1, d2, stockPrice, strikePrice, rate, sigma, time, div_yield); // putTheta
    result[8] = -strikePrice * time * Math.exp(-rate * time) * normalCDF(-d2); // putRho
    result[9] = normalCDF(-d2); // call risk neutral probability of put exercise

    result[10] = gamma(d1, stockPrice, sigma, time, div_yield); // gamma
    result[11] = stockPrice * Math.sqrt(time) * normOrdinate(d1) * Math.exp(-div_yield * time); // call/put vega
    return result;
  }


  public static final double callTheta(final double d1, final double d2, final double stockPrice, final double strikePrice,
      final double rate, final double sigma, final double time, final double div_yield) {
    // Black Scholes call theta
    double a, b, c;
    if (time == 0) {
      if (Math.abs(d1) == vHigh || sigma == 0) {
        a = 0;
      } else {
        a = -vvHigh;
      }
    } else {
      a = -stockPrice * normOrdinate(d1) * sigma * Math.exp(-div_yield * time) / (2 * Math.sqrt(time));
    }
    b = rate * strikePrice * Math.exp(-rate * time) * normalCDF(d2);
    c = div_yield * stockPrice * normalCDF(d1) * Math.exp(-div_yield * time);
    return a - b + c;
  }

  public static final double putTheta(final double d1, final double d2, final double stockPrice, final double strikePrice,
      final double rate, final double sigma, final double time, final double div_yield) {
    // Black Scholes put theta
    double a, b, c;
    if (time == 0) {
      if (Math.abs(d1) == vHigh || sigma == 0) {
        a = 0;
      } else {
        a = -vvHigh;
      }
    } else {
      a = -stockPrice * normOrdinate(d1) * sigma * Math.exp(-div_yield * time) / (2 * Math.sqrt(time));
    }
    b = rate * strikePrice * Math.exp(-rate * time) * normalCDF(-d2);
    c = div_yield * stockPrice * normalCDF(-d1) * Math.exp(-div_yield * time);
    return a + b - c;
  }

  public static final double gamma(final double d1, final double stockPrice, final double sigma, final double time,
      final double div_yield) {
    // Black Scholes call/put gamma
    if (sigma == 0 || time == 0) {
      if (Math.abs(d1) == vHigh) {
        return 0;
      } else {
        return vvHigh;
      }
    } else {
      return normOrdinate(d1) * Math.exp(-div_yield * time) / (stockPrice * sigma * Math.sqrt(time));
    }
  }


  public static final double normOrdinate(final double z) {
    // The normal ordinate (probability density function)
    return Math.exp(-0.5 * z * z) / Math.sqrt(2 * PI);
  }

  public final double NormalCDF_old(final double y) {
    // Computes normal integral by using a rational polynomial approximation
    // This approximation is from Example 9.7.3 of
    // Fike, C.T. (1968), Computer Evaluation of Mathematical Functions
    // Englewood Cliffs, N.J., Prentice Hall
    // Let P(x) be the integral of the normal density from 0 to x. {,
    // the best minimax approximation R(x) to P(x) in the range [0,infinity)
    // among the class of rational functions V5,5[0,infinity) satisfying
    // R(0) = P(0) = 0, and
    // lim x tends to infinity R(x) = lim x tends to infinity P(x) = 0.5
    // is the function:
    // a1 + a2*x + a3*x^2 + a4*x^3 + a5*x^4 + a6*x^5
    // ----------------------------------------------------
    // b1 + b2*x + b3*x^2 + b4*x^3 + b5*x^4 + b6*x^5
    // where the constants a1, a2, ..., a6 and b1, b2, ..., b6
    // are as defined below
    // The maximum absolute error of this approximation is 0.46x10^-4
    // i.e. 0.000046. Therefore, this approximation has the same accuracy
    // as the 4 place tables commonly found in statistics test books

    final double a1 = 0, a2 = 9.050508, a3 = 0.767742, a4 = 1.666902, a5 = -0.624298, a6 = 0.5, b1 = 22.601228, B2 = 2.776898,
        b3 = 5.148169, b4 = 2.995582, b5 = -1.238661, b6 = 1;

    // We now compute R(abs(y)) as an approximation to P(abs(y))

    double X = Math.abs(y);
    double temp1 = ((((a6 * X + a5) * X + a4) * X + a3) * X + a2) * X + a1;
    double temp2 = ((((b6 * X + b5) * X + b4) * X + b3) * X + B2) * X + b1;
    double Temp = temp1 / temp2;

    // We now compute the normal integral N(y) from P(abs(y))

    if (y < 0) {
      return 0.5 - Temp;
    } else {
      return 0.5 + Temp;
    }
  }

  public static final double normalCDF(final double x) {
    try {
      if (Double.isNaN(x) || Double.isInfinite(x))
        return 0;

      if (x >= 0.0) {
        double k = 1.0 / (1.0 + CDF_GAMMA * x);
        double temp = normOrdinate(x) * k * (A1 + k * (A2 + k * (A3 + k * (A4 + k * A5))));
        if (temp < CDF_PRECISION)
          return 1.0;
        temp = 1.0 - temp;
        if (temp < CDF_PRECISION)
          return 0.0;
        return temp;
      } else {
        return 1.0 - normalCDF(-x);
      }
    } catch (Throwable e) {
      System.err.println("Error normalCDF x=" + x);
      e.printStackTrace();
      LOGGER.error("Error normalCDF x=" + x, e);
      return 0;
    }
  }

  // replicates Sgn as in visual basic, the signum of a real number
  public final double sgn(final double x) {
    if (x > 0)
      return 1.0;
    else if (x < 0)
      return -1.0;
    else
      return 0.0;
  }

  public static void main2(String[] args) {
    try {
      double stockPrice = 100;
      double strikePrice = 90;
      double rate = .02;
      double time = 0.5;
      double sigma = .65; // 1=100%
      double div = 0;
      // call price = 23.161958985583023
      double[] result = calc(stockPrice, strikePrice, rate, sigma, time, div);

      System.out.println("range");
      for (int i = -1000000; i < 1000000; i++) {
        normalCDF(Double.NaN);
      }
      System.out.println(normalCDF(Double.MAX_VALUE));

      System.out.println("result=" + Arrays.toString(result));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    try {
      double stockPrice = 20400;
      double strikePrice = 20000;
      double rate = .02; // interest rate 2%
      double time = 0.3315; // 121 days
      double sigma = 5.00; // 1=100% implied volatility
      double div = 0;
      // call price = 3533.59
      double[] result = calc(stockPrice, strikePrice, rate, sigma, time, div);


      System.out.println("result=" + Arrays.toString(result));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
