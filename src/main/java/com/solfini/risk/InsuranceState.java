package com.solfini.risk;

import com.solfini.user.User;
import com.solfini.util.PropertyReader;

/**
 *
 * @author Chris Mack
 *
 */
public final class InsuranceState {
  private static User user;
  private static boolean useInsurance = "true".equalsIgnoreCase(PropertyReader.getProperty("USE_INSURANCE", "true"));
  private static int insurancePositonPercentLimit = PropertyReader.getProperty("INSURANCE_POSITION_PERCENT_LIMIT", 25);
  private static int insuranceLossPercentLimit = PropertyReader.getProperty("INSURANCE_LOSS_PERCENT_LIMIT", 10);
  private static int insuranceAutoCloseMode = PropertyReader.getProperty("INSURANCE_AUTO_CLOSE_MODE", 0);

  private InsuranceState() {
    // default constructor
  }

  public static final User getUser() {
    return user;
  }

  public static final void setUser(final User value) {
    user = value;
  }

  public static final boolean isUseInsurance() {
    return useInsurance;
  }

  public static final void setUseInsurance(final boolean value) {
    useInsurance = value;
  }

  public static final int getInsurancePositonPercentLimit() {
    return insurancePositonPercentLimit;
  }

  public static final void setInsurancePositonPercentLimit(final int value) {
    insurancePositonPercentLimit = value;
  }

  public static final int getInsuranceLossPercentLimit() {
    return insuranceLossPercentLimit;
  }

  public static final void setInsuranceLossPercentLimit(final int value) {
    insuranceLossPercentLimit = value;
  }

  public static final int getInsuranceAutoCloseMode() {
    return insuranceAutoCloseMode;
  }

  public static final void setInsuranceAutoCloseMode(final int value) {
    insuranceAutoCloseMode = value;
  }
}
