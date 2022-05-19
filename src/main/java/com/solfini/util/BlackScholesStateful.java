package com.solfini.util;

// This defines a number of functions related to the Black-Scholes
// option pricing formula. This includes Black-Scholes call and put
// prices, Black-Scholes call and put implied volatilities and the various
// option greeks - delta, gamma, vega, theta and rho
// https://github.com/jrvarma/black-scholes-java/blob/master/src/BlackScholes.java
/**
 * to calculate d1 of Black-Scholes
 *
 * @param s | stock price
 * @param x | strike price
 * @param r | risk free rate
 * @param t | time to maturity in years
 * @param v | implied volatility of returns of underlying asset/stock return d1
 */


public class BlackScholesStateful {
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

  // stateful data
  private double d1, d2, g;
  private double s, X, r, Sigma, t;
  private double div_yield = 0;

  // This computes the BlackScholes quantity d1 safely i.e.
  // no division by zero and no log of zero
  private double safeD1() {
    double s0;
    if (Sigma == 0 || t == 0) {
      s0 = s * Math.exp((g + Sigma * Sigma / 2) * t);
      if (s0 > X) {
        return vHigh;
      }
      if (s0 < X) {
        return -vHigh;
      }
      return 0.0;
    } else {
      if (X == 0) {
        return vHigh;
      } else {
        // Below is the BlackScholes formula for d1
        return (Math.log(s / X) + (g + Sigma * Sigma / 2) * t) / (Sigma * Math.sqrt(t));
      }
    }
  }

  BlackScholesStateful() {}

  BlackScholesStateful(final double s_, final double X_, final double r_, final double Sigma_, final double t_) {
    this(s_, X_, r_, Sigma_, t_, 0);
  }

  BlackScholesStateful(final double s_, final double X_, final double r_, final double Sigma_, final double t_, final double div_yield_) {
    s = s_;
    X = X_;
    r = r_;
    Sigma = Sigma_;
    t = t_;
    div_yield = div_yield_;
    g = r - div_yield;
    d1 = safeD1();
    d2 = d1 - Sigma * Math.sqrt(t);
  }

  public final void reset(final double s_, final double X_, final double r_, final double Sigma_, final double t_) {
    reset(s_, X_, r_, Sigma_, t_, 0);
  }

  public final void reset(final double s_, final double X_, final double r_, final double Sigma_, final double t_,
      final double div_yield_) {
    s = s_;
    X = X_;
    r = r_;
    Sigma = Sigma_;
    t = t_;
    div_yield = div_yield_;
    g = r - div_yield;
    d1 = safeD1();
    d2 = d1 - Sigma * Math.sqrt(t);
  }

  public final void reset(final double Sigma_) {
    Sigma = Sigma_;
    d1 = safeD1();
    d2 = d1 - Sigma * Math.sqrt(t);
  }

  public final double callPrice() {
    // Black Scholes call price
    return Math.exp(-r * t) * (s * Math.exp(g * t) * normalCDF(d1) - X * normalCDF(d2));
  }

  public final double putPrice() {
    // Black Scholes put price
    return Math.exp(-r * t) * (-s * Math.exp(g * t) * normalCDF(-d1) + X * normalCDF(-d2));
  }

  public final double callDelta() {
    // Black Scholes call delta
    return Math.exp(-div_yield * t) * normalCDF(d1);
  }

  public final double putDelta() {
    return -Math.exp(-div_yield * t) * normalCDF(-d1);
  }

  public final double callTheta() {
    // Black Scholes call theta
    double a, b, c;
    if (t == 0) {
      if (Math.abs(d1) == vHigh || Sigma == 0) {
        a = 0;
      } else {
        a = -vvHigh;
      }
    } else {
      a = -s * normOrdinate(d1) * Sigma * Math.exp(-div_yield * t) / (2 * Math.sqrt(t));
    }
    b = r * X * Math.exp(-r * t) * normalCDF(d2);
    c = div_yield * s * normalCDF(d1) * Math.exp(-div_yield * t);
    return a - b + c;
  }

  public final double putTheta() {
    // Black Scholes put theta
    double a, b, c;
    if (t == 0) {
      if (Math.abs(d1) == vHigh || Sigma == 0) {
        a = 0;
      } else {
        a = -vvHigh;
      }
    } else {
      a = -s * normOrdinate(d1) * Sigma * Math.exp(-div_yield * t) / (2 * Math.sqrt(t));
    }
    b = r * X * Math.exp(-r * t) * normalCDF(-d2);
    c = div_yield * s * normalCDF(-d1) * Math.exp(-div_yield * t);
    return a + b - c;
  }

  public final double gamma() {
    // Black Scholes call/put gamma
    if (Sigma == 0 || t == 0) {
      if (Math.abs(d1) == vHigh) {
        return 0;
      } else {
        return vvHigh;
      }
    } else {
      return normOrdinate(d1) * Math.exp(-div_yield * t) / (s * Sigma * Math.sqrt(t));
    }
  }


  public final double vega() {
    // Black Scholes call/put vega
    return s * Math.sqrt(t) * normOrdinate(d1) * Math.exp(-div_yield * t);
  }

  public final double callRho() {
    return X * t * Math.exp(-r * t) * normalCDF(d2);
  }

  public final double futuresCallRho() {
    return -t * callPrice();
  }

  public final double futuresPutRho() {
    return -t * putPrice();
  }

  public final double putRho() {
    // Black Scholes put rho
    return -X * t * Math.exp(-r * t) * normalCDF(-d2);
  }

  public final double callProb() {
    // Black Scholes risk neutral probability of put exercise
    return normalCDF(d2);
  }

  public final double putProb() {
    // Black Scholes risk neutral probability of put exercise
    return normalCDF(-d2);
  }


  public final double normOrdinate(final double z) {
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

  public final double normalCDF(final double x) {
    if (Double.NaN == x || Double.isInfinite(x))
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

  public static void main(String[] args) {
    double stockPrice = 100;
    double strikePrice = 10;
    double rate = .02;
    double time = 0.5;
    double volatility = 10;

    BlackScholesStateful blackScholes = new BlackScholesStateful(stockPrice, strikePrice, rate, volatility, time);

    System.out.println("result=" + blackScholes.callPrice());
  }
}
