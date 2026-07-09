package com.solfini.reconciliation;

final class ContractBalanceChecker {

  private ContractBalanceChecker() {
  }

  static AssetBalanceCheckResult check(final String assetName, final double requiredBacking,
      final double actualBalance, final double tolerancePercentage) {
    if (requiredBacking == 0) {
      return new AssetBalanceCheckResult(assetName, requiredBacking, actualBalance, 0, true, true, false);
    }
    final double changePercentage = Math.abs(requiredBacking - actualBalance) / Math.abs(requiredBacking);
    final boolean beyondTolerance = changePercentage > tolerancePercentage;
    // A contract holding at least what it owes is never a liability risk, so an excess balance is
    // not a mismatch - only a shortfall (or a negative required backing, which is a data problem
    // regardless of direction) should ever fail the check.
    final boolean overfunded = requiredBacking > 0 && actualBalance >= requiredBacking;
    final boolean matched = overfunded || !beyondTolerance;
    final boolean over = overfunded && beyondTolerance;
    return new AssetBalanceCheckResult(assetName, requiredBacking, actualBalance, changePercentage, matched, false, over);
  }

  static String formatTableHeader() {
    return "\t Asset  | User Notional | MM Notional |   Required | Contract Bal | Diff %  | Status\n"
        + "\t " + "-".repeat(88) + "\n";
  }

  static String formatTableRow(final double userNotional, final double mmNotional, final AssetBalanceCheckResult result) {
    final String status = result.skipped() ? "SKIPPED (no notional owed)"
        : result.over() ? "OK (OVER)"
        : result.matched() ? "OK" : "[ALERT] FAILED";
    final String diff = result.skipped() ? "-" : String.format("%.2f%%", result.changePercentage() * 100);
    return String.format("\t %-6s | %13.2f | %11.2f | %10.2f | %12.2f | %7s | %s\n",
        result.assetName(), userNotional, mmNotional, result.requiredBacking(), result.actualBalance(), diff, status);
  }

  static String formatHtmlTableOpen() {
    return "<table style=\"border-collapse:collapse;font-family:Arial,Helvetica,sans-serif;font-size:13px;margin:8px 0;min-width:640px;\">"
        + "<tr style=\"background-color:#2c3e50;color:#ffffff;\">"
        + htmlHeaderCell("Asset", "left") + htmlHeaderCell("User Notional", "right") + htmlHeaderCell("MM Notional", "right")
        + htmlHeaderCell("Required", "right") + htmlHeaderCell("Contract Bal", "right") + htmlHeaderCell("Diff %", "right")
        + htmlHeaderCell("Status", "center")
        + "</tr>";
  }

  static String formatHtmlTableRow(final double userNotional, final double mmNotional, final AssetBalanceCheckResult result) {
    final String status = result.skipped() ? "SKIPPED" : result.over() ? "OK (OVER)" : (result.matched() ? "OK" : "FAILED");
    final String statusColor = result.skipped() ? "#7f8c8d" : result.over() ? "#2980b9" : (result.matched() ? "#1e8449" : "#c0392b");
    final String statusBg = result.skipped() ? "#ecf0f1" : result.over() ? "#eaf2fb" : (result.matched() ? "#eafaf1" : "#fdecea");
    final String diff = result.skipped() ? "-" : String.format("%.2f%%", result.changePercentage() * 100);
    return "<tr>"
        + htmlCell(result.assetName(), "left", null, null, true)
        + htmlCell(String.format("%,.2f", userNotional), "right", null, null, false)
        + htmlCell(String.format("%,.2f", mmNotional), "right", null, null, false)
        + htmlCell(String.format("%,.2f", result.requiredBacking()), "right", null, null, false)
        + htmlCell(String.format("%,.2f", result.actualBalance()), "right", null, null, false)
        + htmlCell(diff, "right", null, null, false)
        + htmlCell(status, "center", statusColor, statusBg, true)
        + "</tr>";
  }

  static String formatHtmlTableClose() {
    return "</table>";
  }

  private static String htmlHeaderCell(final String text, final String align) {
    return String.format("<th style=\"padding:8px 12px;border:1px solid #ddd;text-align:%s;\">%s</th>", align, text);
  }

  private static String htmlCell(final String text, final String align, final String color, final String background,
      final boolean bold) {
    final StringBuilder style = new StringBuilder("padding:6px 12px;border:1px solid #ddd;text-align:").append(align).append(';');
    if (color != null) {
      style.append("color:").append(color).append(';');
    }
    if (background != null) {
      style.append("background-color:").append(background).append(';');
    }
    if (bold) {
      style.append("font-weight:bold;");
    }
    return String.format("<td style=\"%s\">%s</td>", style, text);
  }
}
