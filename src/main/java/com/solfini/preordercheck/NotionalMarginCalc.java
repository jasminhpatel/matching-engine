package com.solfini.preordercheck;

import com.solfini.instrument.Instrument;
import com.solfini.instrument.InstrumentPair;
import com.solfini.internal.admin.schema.AssetType;
import com.solfini.user.User;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public class NotionalMarginCalc {

  // lowest tier threshold on the risk
  // curve to charge liquidation fee
  public static final double LOWEST_TIER_THRESHOLD = PropertyReader.getProperty("LOWEST_TIER_THRESHOLD", 500_000_000);

  private NotionalMarginCalc() {
    // do nothing
  }

  public static final double calcRequiredMargin(final double notional, final double positionQuantity, final double markPriceOption,
      final InstrumentPair instrumentPair, final User user) {
    if ((AssetType.PAIR == instrumentPair.getAssetType()) || (AssetType.DATED_FUTURE == instrumentPair.getAssetType())
        || (AssetType.PERPETUAL_SWAP == instrumentPair.getAssetType())) {
      // market maker gets minimum requirements

      int marginCurveId = instrumentPair.getMarginCurveId();
      if (user != null) {
        if (User.MARKET_MAKER == user.getUserType())
          return Math.abs(notional) * .05;
        if (user.getMarginCurveIdOverride() > 0)
          marginCurveId = user.getMarginCurveIdOverride();
      }

      final double startMarginRate = instrumentPair.getRequiredMarginBasisPoints() * .0001;

      if (marginCurveId == 0) {
        return calcMargin0(Math.abs(notional), startMarginRate);
      } else {
        switch (instrumentPair.getMarginCurveId()) {
          case 0:
            return calcMargin0(Math.abs(notional), startMarginRate);
          case 1:
            return calcMargin1(Math.abs(notional), startMarginRate);
          case 2:
            return calcMargin2(Math.abs(notional), startMarginRate);
          case 3:
            return calcMargin3(Math.abs(notional), startMarginRate);
          case 4:
            return calcMargin4(Math.abs(notional), startMarginRate);
          case 5:
            return calcMargin5(Math.abs(notional), startMarginRate);
          case 6:
            return calcMargin6(Math.abs(notional), startMarginRate);
          default:
            return calcMargin0(Math.abs(notional), startMarginRate);
        }
      }
    } else if (AssetType.OPTION_CALL == instrumentPair.getAssetType()) {
      if (notional == 0)
        return 0;
      else if (positionQuantity < 0 || notional < 0) { // short call
        // double quantityCalced = markPriceOption == 0 ? notional : notional / markPriceOption;
        // return Math.max(0.30, 0.30 * markPriceOption) + markPriceOption;
        return Math.max(0.30, 0.30 * Math.abs(notional)) + Math.abs(notional);
      } else { // long call
        return Math.abs(notional);
      }
    } else if (AssetType.OPTION_PUT == instrumentPair.getAssetType()) {
      if (notional == 0)
        return 0;
      else if (positionQuantity < 0 || notional < 0) { // short put
        // double quantityCalced = markPriceOption == 0 ? notional : notional / markPriceOption;
        // return Math.max(0.30, 0.30 * markPriceOption) + markPriceOption;
        return Math.max(0.30, 0.30 * Math.abs(notional)) + Math.abs(notional);
      } else { // long put
        return Math.abs(notional);
      }
    } else {
      return 0;
    }
  }

  public static final double calcMaintMargin(final double notional, final double quantity, final double markPriceOption,
      final InstrumentPair instrumentPair, final User user) {
    if ((AssetType.PAIR == instrumentPair.getAssetType()) || (AssetType.DATED_FUTURE == instrumentPair.getAssetType())
        || (AssetType.PERPETUAL_SWAP == instrumentPair.getAssetType())) {
      // market maker gets minimum requirements

      int marginCurveId = instrumentPair.getMarginCurveId();
      if (user != null) {
        if (User.MARKET_MAKER == user.getUserType())
          return Math.abs(notional) * .025;
        if (user.getMarginCurveIdOverride() > 0)
          marginCurveId = user.getMarginCurveIdOverride();
      }

      final double startMarginRate = instrumentPair.getMaintMarginBasisPoints() * .0001;

      if (marginCurveId == 0) {
        return calcMargin0(Math.abs(notional), startMarginRate);
      } else {
        switch (instrumentPair.getMarginCurveId()) {
          case 0:
            return calcMargin0(Math.abs(notional), startMarginRate);
          case 1:
            return calcMargin1(Math.abs(notional), startMarginRate);
          case 2:
            return calcMargin2(Math.abs(notional), startMarginRate);
          case 3:
            return calcMargin3(Math.abs(notional), startMarginRate);
          case 4:
            return calcMargin4(Math.abs(notional), startMarginRate);
          case 5:
            return calcMargin5(Math.abs(notional), startMarginRate);
          case 6:
            return calcMargin6(Math.abs(notional), startMarginRate);
          default:
            return calcMargin0(Math.abs(notional), startMarginRate);
        }
      }
    } else if (AssetType.OPTION_CALL == instrumentPair.getAssetType()) {
      if (quantity == 0) {
        return 0;
      } else if (quantity > 0) { // long call
        return Math.abs(notional) * .99;
      } else { // short call
        // return Math.max(0.15, 0.15 * markPriceOption) + markPriceOption;
        return Math.max(0.15, 0.15 * Math.abs(notional)) + Math.abs(notional);
      }
    } else if (AssetType.OPTION_PUT == instrumentPair.getAssetType()) {
      if (quantity == 0) {
        return 0;
      } else if (quantity > 0) { // long put
        return Math.abs(notional) * .99;
      } else { // short put
        // return Math.max(0.15, 0.15 * markPriceOption) + markPriceOption;
        return Math.max(0.15, 0.15 * Math.abs(notional)) + Math.abs(notional);
      }
    } else {
      return 0;
    }
  }


  private static final double calcMargin0(double absNotional, final double startMarginRate) {
    if (absNotional < 500_000) {
      return (absNotional) * startMarginRate;
    } else {
      double requiredMargin = (500_000) * startMarginRate;
      absNotional -= 500_000;
      if (absNotional < 1_500_000) {
        return requiredMargin + (2 * (absNotional) * startMarginRate);
      } else {
        requiredMargin = requiredMargin + (2 * (1_500_000) * startMarginRate);
        absNotional -= 1_500_000;
        if (absNotional < 3_000_000) {
          return requiredMargin + (3 * (absNotional) * startMarginRate);
        } else {
          requiredMargin = requiredMargin + (3 * (3_000_000) * startMarginRate);
          absNotional -= 3_000_000;
          if (absNotional < 5_000_000) {
            return requiredMargin + (4 * (absNotional) * startMarginRate);
          } else {
            requiredMargin = requiredMargin + (4 * (5_000_000) * startMarginRate);
            absNotional -= 5_000_000;
            if (absNotional < 10_000_000) {
              return requiredMargin + (5 * (absNotional) * startMarginRate);
            } else {
              requiredMargin = requiredMargin + (5 * (10_000_000) * startMarginRate);
              absNotional -= 10_000_000;
              if (absNotional < 15_000_000) {
                return requiredMargin + (6 * (absNotional) * startMarginRate);
              } else {
                requiredMargin = requiredMargin + (6 * (15_000_000) * startMarginRate);
                absNotional -= 15_000_000;
                if (absNotional < 25_000_000) {
                  return requiredMargin + (8 * (absNotional) * startMarginRate);
                } else {
                  requiredMargin = requiredMargin + (8 * (25_000_000) * startMarginRate);
                  absNotional -= 25_000_000;

                  requiredMargin = requiredMargin + (10 * (absNotional) * startMarginRate);
                  return requiredMargin;

                }
              }
            }
          }
        }
      }
    }
  }


  private static final double calcMargin1(double absNotional, final double startMarginRate) {
    if (absNotional < 200_000) {
      return (absNotional) * startMarginRate;
    } else {
      double requiredMargin = (200_000) * startMarginRate;
      absNotional -= 200_000;
      if (absNotional < 500_000) {
        return requiredMargin + (2 * (absNotional) * startMarginRate);
      } else {
        requiredMargin = requiredMargin + (2 * (500_000) * startMarginRate);
        absNotional -= 500_000;
        if (absNotional < 1_000_000) {
          return requiredMargin + (3 * (absNotional) * startMarginRate);
        } else {
          requiredMargin = requiredMargin + (3 * (1_000_000) * startMarginRate);
          absNotional -= 1_000_000;
          if (absNotional < 2_000_000) {
            return requiredMargin + (4 * (absNotional) * startMarginRate);
          } else {
            requiredMargin = requiredMargin + (4 * (2_000_000) * startMarginRate);
            absNotional -= 2_000_000;
            if (absNotional < 3_000_000) {
              return requiredMargin + (5 * (absNotional) * startMarginRate);
            } else {
              requiredMargin = requiredMargin + (5 * (3_000_000) * startMarginRate);
              absNotional -= 3_000_000;
              if (absNotional < 4_000_000) {
                return requiredMargin + (6 * (absNotional) * startMarginRate);
              } else {
                requiredMargin = requiredMargin + (6 * (4_000_000) * startMarginRate);
                absNotional -= 4_000_000;
                if (absNotional < 5_000_000) {
                  return requiredMargin + (8 * (absNotional) * startMarginRate);
                } else {
                  requiredMargin = requiredMargin + (8 * (5_000_000) * startMarginRate);
                  absNotional -= 5_000_000;

                  requiredMargin = requiredMargin + (10 * (absNotional) * startMarginRate);
                  return requiredMargin;

                }
              }
            }
          }

        }
      }
    }
  }

  private static final double calcMargin2(double absNotional, final double startMarginRate) {
    if (absNotional < 1_000_000) {
      return (absNotional) * startMarginRate;
    } else {
      double requiredMargin = (1_000_000) * startMarginRate;
      absNotional -= 1_000_000;
      if (absNotional < 2_500_000) {
        return requiredMargin + (2 * (absNotional) * startMarginRate);
      } else {
        requiredMargin = requiredMargin + (2 * (2_500_000) * startMarginRate);
        absNotional -= 2_500_000;
        if (absNotional < 5_000_000) {
          return requiredMargin + (3 * (absNotional) * startMarginRate);
        } else {
          requiredMargin = requiredMargin + (3 * (5_000_000) * startMarginRate);
          absNotional -= 5_000_000;
          if (absNotional < 8_000_000) {
            return requiredMargin + (4 * (absNotional) * startMarginRate);
          } else {
            requiredMargin = requiredMargin + (4 * (8_000_000) * startMarginRate);
            absNotional -= 8_000_000;
            if (absNotional < 15_000_000) {
              return requiredMargin + (5 * (absNotional) * startMarginRate);
            } else {
              requiredMargin = requiredMargin + (5 * (15_000_000) * startMarginRate);
              absNotional -= 15_000_000;
              if (absNotional < 25_000_000) {
                return requiredMargin + (6 * (absNotional) * startMarginRate);
              } else {
                requiredMargin = requiredMargin + (6 * (25_000_000) * startMarginRate);
                absNotional -= 25_000_000;
                if (absNotional < 45_000_000) {
                  return requiredMargin + (8 * (absNotional) * startMarginRate);
                } else {
                  requiredMargin = requiredMargin + (8 * (35_000_000) * startMarginRate);
                  absNotional -= 35_000_000;

                  requiredMargin = requiredMargin + (10 * (absNotional) * startMarginRate);
                  return requiredMargin;

                }
              }
            }
          }

        }
      }
    }
  }


  private static final double calcMargin3(double absNotional, final double startMarginRate) {
    if (absNotional < 2_000_000) {
      return (absNotional) * startMarginRate;
    } else {
      double requiredMargin = (2_000_000) * startMarginRate;
      absNotional -= 2_000_000;
      if (absNotional < 3_500_000) {
        return requiredMargin + (2 * (absNotional) * startMarginRate);
      } else {
        requiredMargin = requiredMargin + (2 * (3_500_000) * startMarginRate);
        absNotional -= 3_500_000;
        if (absNotional < 7_000_000) {
          return requiredMargin + (3 * (absNotional) * startMarginRate);
        } else {
          requiredMargin = requiredMargin + (3 * (7_000_000) * startMarginRate);
          absNotional -= 7_000_000;
          if (absNotional < 12_000_000) {
            return requiredMargin + (4 * (absNotional) * startMarginRate);
          } else {
            requiredMargin = requiredMargin + (4 * (12_000_000) * startMarginRate);
            absNotional -= 12_000_000;
            if (absNotional < 20_000_000) {
              return requiredMargin + (5 * (absNotional) * startMarginRate);
            } else {
              requiredMargin = requiredMargin + (5 * (20_000_000) * startMarginRate);
              absNotional -= 20_000_000;
              if (absNotional < 35_000_000) {
                return requiredMargin + (6 * (absNotional) * startMarginRate);
              } else {
                requiredMargin = requiredMargin + (6 * (35_000_000) * startMarginRate);
                absNotional -= 35_000_000;
                if (absNotional < 45_000_000) {
                  return requiredMargin + (8 * (absNotional) * startMarginRate);
                } else {
                  requiredMargin = requiredMargin + (8 * (35_000_000) * startMarginRate);
                  absNotional -= 35_000_000;

                  requiredMargin = requiredMargin + (10 * (absNotional) * startMarginRate);
                  return requiredMargin;

                }
              }
            }
          }

        }
      }
    }
  }

  // curve from
  private static final double calcMargin4(double absNotional, final double startMarginRate) {
    if (absNotional < 10_000) {
      return (absNotional) * startMarginRate;
    } else {
      double requiredMargin = (10_000) * startMarginRate;
      absNotional -= 10_000;
      if (absNotional < 15_000) {
        return requiredMargin + (1.42 * (absNotional) * startMarginRate);
      } else {
        requiredMargin = requiredMargin + (1.42 * (15_000) * startMarginRate);
        absNotional -= 15_000;
        if (absNotional < 25_000) {
          return requiredMargin + (2.08 * (absNotional) * startMarginRate);
        } else {
          requiredMargin = requiredMargin + (2.08 * (25_000) * startMarginRate);
          absNotional -= 25_000;
          if (absNotional < 100_000) {
            return requiredMargin + (2.925 * (absNotional) * startMarginRate);
          } else {
            requiredMargin = requiredMargin + (2.925 * (100_000) * startMarginRate);
            absNotional -= 100_000;
            if (absNotional < 100_000) {
              return requiredMargin + (4.064 * (absNotional) * startMarginRate);
            } else {
              requiredMargin = requiredMargin + (4.064 * (100_000) * startMarginRate);
              absNotional -= 100_000;
              if (absNotional < 150_000) {
                return requiredMargin + (11.5 * (absNotional) * startMarginRate);
              } else {
                requiredMargin = requiredMargin + (11.5 * (150_000) * startMarginRate);
                absNotional -= 150_000;
                if (absNotional < 300_000) {
                  return requiredMargin + (21 * (absNotional) * startMarginRate);
                } else {
                  requiredMargin = requiredMargin + (21 * (300_000) * startMarginRate);
                  absNotional -= 300_000;
                  if (absNotional < 300_000) {
                    return requiredMargin + (53.8 * (absNotional) * startMarginRate);
                  } else {
                    requiredMargin = requiredMargin + (53.8 * (300_000) * startMarginRate);
                    absNotional -= 300_000;
                    if (absNotional < 500_000) {
                      return requiredMargin + (44 * (absNotional) * startMarginRate);
                    } else {
                      requiredMargin = requiredMargin + (44 * (500_000) * startMarginRate);
                      absNotional -= 500_000;
                      if (absNotional < 500_000) {
                        return requiredMargin + (44.5 * (absNotional) * startMarginRate);
                      } else {
                        requiredMargin = requiredMargin + (44.5 * (500_000) * startMarginRate);
                        absNotional -= 500_000;
                        if (absNotional < 2_00_000) {
                          return requiredMargin + (46 * (absNotional) * startMarginRate);
                        } else {
                          requiredMargin = requiredMargin + (46 * (2_00_000) * startMarginRate);
                          absNotional -= 2_00_000;
                          if (absNotional < 3_000_000) {
                            return requiredMargin + (48 * (absNotional) * startMarginRate);
                          } else {
                            requiredMargin = requiredMargin + (48 * (3_000_000) * startMarginRate);
                            absNotional -= 3_000_000;
                            if (absNotional < 8_000_000) {
                              return requiredMargin + (76.5 * (absNotional) * startMarginRate);
                            } else {
                              requiredMargin = requiredMargin + (76.5 * (8_000_000) * startMarginRate);
                              absNotional -= 8_000_000;

                              requiredMargin = requiredMargin + (95 * (absNotional) * startMarginRate);
                              return requiredMargin;

                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }

        }
      }
    }
  }

  private static final double calcMargin5(double absNotional, final double startMarginRate) {
    if (absNotional < 10_000) {
      return (absNotional) * startMarginRate;
    } else {
      double requiredMargin = (10_000) * startMarginRate;
      absNotional -= 10_000;
      if (absNotional < 15_000) {
        return requiredMargin + (1.25 * (absNotional) * startMarginRate);
      } else {
        requiredMargin = requiredMargin + (1.25 * (15_000) * startMarginRate);
        absNotional -= 15_000;
        if (absNotional < 25_000) {
          return requiredMargin + (1.6625 * (absNotional) * startMarginRate);
        } else {
          requiredMargin = requiredMargin + (1.6625 * (25_000) * startMarginRate);
          absNotional -= 25_000;
          if (absNotional < 100_000) {
            return requiredMargin + (2.5 * (absNotional) * startMarginRate);
          } else {
            requiredMargin = requiredMargin + (2.5 * (100_000) * startMarginRate);
            absNotional -= 100_000;
            if (absNotional < 100_000) {
              return requiredMargin + (3.125 * (absNotional) * startMarginRate);
            } else {
              requiredMargin = requiredMargin + (3.125 * (100_000) * startMarginRate);
              absNotional -= 100_000;
              if (absNotional < 150_000) {
                return requiredMargin + (6.25 * (absNotional) * startMarginRate);
              } else {
                requiredMargin = requiredMargin + (6.25 * (150_000) * startMarginRate);
                absNotional -= 150_000;
                if (absNotional < 300_000) {
                  return requiredMargin + (12.5 * (absNotional) * startMarginRate);
                } else {
                  requiredMargin = requiredMargin + (12.5 * (300_000) * startMarginRate);
                  absNotional -= 300_000;
                  if (absNotional < 300_000) {
                    return requiredMargin + (25 * (absNotional) * startMarginRate);
                  } else {
                    requiredMargin = requiredMargin + (25 * (300_000) * startMarginRate);
                    absNotional -= 300_000;
                    if (absNotional < 500_000) {
                      return requiredMargin + (31.25 * (absNotional) * startMarginRate);
                    } else {
                      requiredMargin = requiredMargin + (31.25 * (500_000) * startMarginRate);
                      absNotional -= 500_000;
                      if (absNotional < 1_000_000) {
                        return requiredMargin + (37.5 * (absNotional) * startMarginRate);
                      } else {
                        requiredMargin = requiredMargin + (37.5 * (1_000_000) * startMarginRate);
                        absNotional -= 1_000_000;
                        if (absNotional < 10_000_000) {
                          return requiredMargin + (62.5 * (absNotional) * startMarginRate);
                        } else {
                          requiredMargin = requiredMargin + (62.5 * (10_000_000) * startMarginRate);
                          absNotional -= 10_000_000;
                          if (absNotional < 12_500_000) {
                            return requiredMargin + (83.3375 * (absNotional) * startMarginRate);
                          } else {
                            requiredMargin = requiredMargin + (83.3375 * (12_500_000) * startMarginRate);
                            absNotional -= 12_500_000;


                            requiredMargin = requiredMargin + (125 * (absNotional) * startMarginRate);
                            return requiredMargin;
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // 100% required, no margin
  private static final double calcMargin6(double absNotional, final double startMarginRate) {
    return absNotional;
  }

  // reverse of margin curve
  public static final double calcEstMarginBuyingPower5(final double usdCollateral) {
    return usdCollateral * calcEstLeverage5(usdCollateral);
  }

  // reverse of margin curve
  public static final double calcEstLeverage5(final double usdCollateral) {
    if (usdCollateral < 20562.5) {
      if (usdCollateral < 80)
        return 125;
      if (usdCollateral < 296.5)
        return 101;
      if (usdCollateral < 562.5)
        return 88.88;
      if (usdCollateral < 762.5)
        return 78.68;
      if (usdCollateral < 962.5)
        return 72.72;
      if (usdCollateral < 1362.5)
        return 66.05;
      if (usdCollateral < 2162.5)
        return 60.11;
      if (usdCollateral < 2812.5)
        return 56.88;
      if (usdCollateral < 3812.5)
        return 52.459;
      if (usdCollateral < 4562.5)
        return 50.41;
      if (usdCollateral < 5562.5)
        return 46.74;
      if (usdCollateral < 7062.5)
        return 41.06;
      if (usdCollateral < 8062.5)
        return 38.449;
      if (usdCollateral < 9562.5)
        return 35.55;
      if (usdCollateral < 12062.5)
        return 32.33;
      if (usdCollateral < 13562.5)
        return 30.23;
      if (usdCollateral < 15562.5)
        return 27.63;
      // if (usdCollateral < 20562.5)
      return 23.34;
    }
    if (usdCollateral < 22562.5)
      return 23.34;
    if (usdCollateral < 22562.5)
      return 22.16;
    if (usdCollateral < 26562.5)
      return 20.32;
    if (usdCollateral < 33562.5)
      return 18.17;
    if (usdCollateral < 38562.5)
      return 17.11;
    if (usdCollateral < 48562.5)
      return 15.03;
    if (usdCollateral < 52562.5)
      return 14.268;
    if (usdCollateral < 60562.5)
      return 13.04;
    if (usdCollateral < 68562.5)
      return 12.1;
    if (usdCollateral < 88562.5)
      return 10.50;
    if (usdCollateral < 110062.5)
      return 9.35;
    if (usdCollateral < 145062.5)
      return 8.06;
    if (usdCollateral < 195062.5)
      return 7.02;
    if (usdCollateral < 233562.5)
      return 6.5;
    if (usdCollateral < 320562.5)
      return 5.64;
    if (usdCollateral < 437562.5)
      return 5.02;
    if (usdCollateral < 707562.5)
      return 4.04;
    if (usdCollateral < 927562.5)
      return 3.55;
    if (usdCollateral < 1427562.5)
      return 3.01;
    if (usdCollateral < 2822562.5)
      return 2.51;
    if (usdCollateral < 8181028.5)
      return 2.01;
    else // if (usdCollateral < 22241312.5)
      return 1.5;
  }


  // reverse of margin curve
  public static final double calcEstCollateral5(final double leverage) {
    if (leverage >= 125)
      return 80;
    if (leverage >= 101)
      return 296.5;
    if (leverage >= 88.88)
      return 562.5;
    if (leverage >= 78.68)
      return 762.5;
    if (leverage >= 72.72)
      return 962.5;
    if (leverage >= 66.05)
      return 1362.5;
    if (leverage >= 60.11)
      return 2162.5;
    if (leverage >= 56.88)
      return 2812.5;
    if (leverage >= 52.459)
      return 3812.5;
    if (leverage >= 50.41)
      return 4562.5;
    if (leverage >= 46.74)
      return 5562.5;
    if (leverage >= 41.06)
      return 7062.5;
    if (leverage >= 38.449)
      return 8062.5;
    if (leverage >= 35.55)
      return 9562.5;
    if (leverage >= 32.33)
      return 12062.5;
    if (leverage >= 30.23)
      return 13562.5;
    if (leverage >= 27.63)
      return 15562.5;
    if (leverage >= 23.34)
      return 20562.5;
    if (leverage >= 22.16)
      return 22562.5;
    if (leverage >= 20.32)
      return 26562.5;
    if (leverage >= 18.17)
      return 33562.5;
    if (leverage >= 17.11)
      return 38562.5;
    if (leverage >= 15.03)
      return 48562.5;
    if (leverage >= 14.268)
      return 52562.5;
    if (leverage >= 13.04)
      return 60562.5;
    if (leverage >= 12.1)
      return 68562.5;
    if (leverage >= 10.50)
      return 88562.5;
    if (leverage >= 9.35)
      return 110062.5;
    if (leverage >= 8.06)
      return 145062.5;
    if (leverage >= 7.02)
      return 195062.5;
    if (leverage >= 6.5)
      return 233562.5;
    if (leverage >= 5.64)
      return 320562.5;
    if (leverage >= 5.02)
      return 437562.5;
    if (leverage >= 4.04)
      return 707562.5;
    if (leverage >= 3.55)
      return 927562.5;
    if (leverage >= 3.01)
      return 1427562.5;
    if (leverage >= 2.51)
      return 2822562.5;
    if (leverage >= 2.01)
      return 8181028.5;
    if (leverage >= 1.500)
      return 22241312.5;
    return 25_000_000;
  }

  // reverse of margin curve
  public static final double calcEstMarginBuyingPower4(final double usdCollateral) {
    return usdCollateral * calcEstLeverage4(usdCollateral);
  }

  // reverse of margin curve
  public static final double calcEstLeverage4(final double usdCollateral) {
    if (usdCollateral < 20_000) {
      if (usdCollateral < 80)
        return 125;
      if (usdCollateral < 250)
        return 100;
      if (usdCollateral < 666.66)
        return 75;
      if (usdCollateral < 900)
        return 66.66;
      if (usdCollateral < 1602)
        return 56;
      if (usdCollateral < 2300)
        return 52;
      if (usdCollateral < 3000)
        return 50;
      if (usdCollateral < 4000)
        return 45;
      if (usdCollateral < 6250)
        return 40;
      if (usdCollateral < 7558)
        return 38;
      if (usdCollateral < 10307)
        return 31;
      if (usdCollateral < 11519)
        return 27.78;
      if (usdCollateral < 15155)
        return 23.75;
      else // if (usdCollateral < 20_000)
        return 20;
    }
    if (usdCollateral < 26_063)
      return 17.26;
    if (usdCollateral < 30_911)
      return 15.85;
    if (usdCollateral < 44_243)
      return 13.56;
    if (usdCollateral < 54_547)
      return 11.73;
    if (usdCollateral < 70_000)
      return 10;
    if (usdCollateral < 100_915)
      return 8.12;
    if (usdCollateral < 145_091)
      return 6.4;
    if (usdCollateral < 200_000)
      return 5;
    if (usdCollateral < 375_000)
      return 4;
    if (usdCollateral < 757_575)
      return 3.3;
    if (usdCollateral < 6_250_000)
      return 2;
    else
      return 1.5;
  }

  private static final double[] impactPointArr = buildImpactPointArr();

  private static final double[] buildImpactPointArr() {
    double impactDiscount = 0.97;
    final double[] impactPointArr = new double[1000];
    for (int i = 0; i < 50; i++) {
      impactPointArr[i] = impactDiscount;
      impactDiscount = impactDiscount * 0.998;
    }
    for (int i = 50; i < 100; i++) {
      impactPointArr[i] = impactDiscount;
      impactDiscount = impactDiscount * 0.9985;
    }
    for (int i = 100; i < 150; i++) {
      impactPointArr[i] = impactDiscount;
      impactDiscount = impactDiscount * 0.999;
    }
    for (int i = 150; i < 200; i++) {
      impactPointArr[i] = impactDiscount;
      impactDiscount = impactDiscount * 0.9995;
    }
    for (int i = 200; i < 250; i++) {
      impactPointArr[i] = impactDiscount;
      impactDiscount = impactDiscount * 0.99995;
    }
    for (int i = 250; i < 1000; i++) {
      impactPointArr[i] = impactDiscount;
      impactDiscount = impactDiscount * 0.99999;
    }
    return impactPointArr;
  }

  // returns multiplier of discount. 1=0 discount, .95=5 percent
  // if not BTC or ETH, double the impact discount to 6%
  public static final double calcImpactDiscount(final Instrument instrument, double qty) {
    double instrumentDiscount = 1;
    if (instrument != null && instrument.getSymbol() != null && instrument.getSymbol().indexOf("BTC") < 0
        && instrument.getSymbol().indexOf("ETH") < 0) {
      instrumentDiscount = 0.97;
    }

    if (qty < 10) {
      return 0.97;
    } else {
      int index = (int) (qty * .1);
      if (index > 999)
        index = 999;
      return instrumentDiscount * impactPointArr[index];
    }
  }
}
