package com.solfini.report.check;

import com.google.common.collect.Lists;
import java.util.List;

public class StatesCheckReport {
  protected List<List<String>> orderBookCheckResult = Lists.newArrayList();

  public List<List<String>> getOrderBookCheckResult() {
    return orderBookCheckResult;
  }

  public void setOrderBookCheckResult(List<List<String>> orderBookCheckResult) {
    this.orderBookCheckResult = orderBookCheckResult;
  }

  public String getCheckResultStr() {
    final StringBuilder sb = new StringBuilder();
    for (List<String> list : orderBookCheckResult) {
      for (String str : list) {
        sb.append(str).append(",");
      }
    }

    if (sb.length() > 0) {
      return sb.substring(0, sb.length() - 1);
    } else {
      return "";
    }
  }
}
