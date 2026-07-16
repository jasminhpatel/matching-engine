package com.solfini.reconciliation;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import com.solfini.util.MailUtil;
import com.solfini.util.MailUtil.MailAttachment;
import com.solfini.util.PropertyReader;

/**
 * Manual, non-JUnit sender used to preview the TSYS-93 per-asset notional table
 * as it will render in a real email client. Not picked up by Surefire (no Test suffix).
 * Run with: mvn -q exec:java -Dexec.mainClass=com.solfini.reconciliation.SampleReconciliationEmailSender
 */
public final class SampleReconciliationEmailSender {

  private SampleReconciliationEmailSender() {
  }

  public static void main(final String[] args) throws Exception {
    // Must run before any MailUtil static field is touched: MailUtil reads
    // mail.* properties at class-init time via PropertyReader's static state.
    try (FileInputStream configStream = new FileInputStream("config.properties")) {
      PropertyReader.initialize(configStream, null);
    }

    final double tolerancePercentage = 0.05;

    final StringBuilder summary = new StringBuilder();
    summary.append("[TEST] Blockchain–User Withdrawable Reconciliation Summary: \n");
    summary.append("\t Snapshot Id: 20260701140000000\n");
    summary.append("\t No of missing users in the contract: 0\n");
    summary.append("\t No of missing Positions: 0\n");
    summary.append("\t No of mismatched Positions: 1\n");
    summary.append("\t No of matched Positions: 482\n\n");
    summary.append("\t Total of user position in USD: 1204512.33\n");
    summary.append("\t Total of market maker positions in USD: 58210.00\n");
    summary.append("\t Total USDC balance of the contract: 987654.12\n");
    summary.append("\t Total USDT balance of the contract: 214000.55\n\n");

    final AssetBalanceCheckResult usdcCheck = ContractBalanceChecker.check("USDC", 950000.00, 987654.12, tolerancePercentage);
    final AssetBalanceCheckResult usdtCheck = ContractBalanceChecker.check("USDT", 214000.55, 214000.55, tolerancePercentage);
    final AssetBalanceCheckResult xusdcCheck = ContractBalanceChecker.check("XUSDC", 0.0, 12000.00, tolerancePercentage);

    summary.append(ContractBalanceChecker.formatTableHeader());
    summary.append(ContractBalanceChecker.formatTableRow(920000.00, 30000.00, usdcCheck));
    summary.append(ContractBalanceChecker.formatTableRow(210500.00, 3500.55, usdtCheck));
    summary.append("\n");
    summary.append(ContractBalanceChecker.formatTableHeader());
    summary.append(ContractBalanceChecker.formatTableRow(0.0, 0.0, xusdcCheck));

    final StringBuilder html = new StringBuilder();
    html.append("<div style=\"font-family:Arial,Helvetica,sans-serif;color:#222222;\">");
    html.append("<h2 style=\"margin:0 0 12px;color:#2c3e50;\">[TEST] Blockchain–User Withdrawable Reconciliation Summary</h2>");
    html.append("<table style=\"border-collapse:collapse;font-size:13px;margin-bottom:16px;\">");
    appendHtmlInfoRow(html, "Snapshot Id", "20260701140000000");
    appendHtmlInfoRow(html, "Missing users in the contract", 0);
    appendHtmlInfoRow(html, "Missing Positions", 0);
    appendHtmlInfoRow(html, "Mismatched Positions", 1);
    appendHtmlInfoRow(html, "Matched Positions", 482);
    appendHtmlInfoRow(html, "Total user position (USD)", "1,204,512.33");
    appendHtmlInfoRow(html, "Total market maker positions (USD)", "58,210.00");
    appendHtmlInfoRow(html, "Total USDC balance of the contract", "987,654.12");
    appendHtmlInfoRow(html, "Total USDT balance of the contract", "214,000.55");
    html.append("</table>");
    html.append(ContractBalanceChecker.formatHtmlTableOpen());
    html.append(ContractBalanceChecker.formatHtmlTableRow(920000.00, 30000.00, usdcCheck));
    html.append(ContractBalanceChecker.formatHtmlTableRow(210500.00, 3500.55, usdtCheck));
    html.append(ContractBalanceChecker.formatHtmlTableClose());
    html.append("<br>");
    html.append(ContractBalanceChecker.formatHtmlTableOpen());
    html.append(ContractBalanceChecker.formatHtmlTableRow(0.0, 0.0, xusdcCheck));
    html.append(ContractBalanceChecker.formatHtmlTableClose());
    html.append("</div>");

    System.out.println(summary);

    final String[] to = {"alerts.mihindu@gmail.com"};
    final String subject = "[TEST] Blockchain–User Withdrawable Reconciliation";
    final List<MailAttachment> mailAttachments = new ArrayList<>(1);
    mailAttachments.add(new MailAttachment("reconciliation_report.csv", "text/csv",
        "asset,required,actual,diff\nUSDC,950000.00,987654.12,3.96%\nUSDT,214000.55,214000.55,0.00%\n"));

    MailUtil.sendMessage(to, subject, summary.toString(), html.toString(), mailAttachments);
    System.out.println("Sent test email to " + to[0]);
  }

  private static void appendHtmlInfoRow(final StringBuilder html, final String label, final Object value) {
    html.append("<tr>")
        .append("<td style=\"padding:3px 12px 3px 0;color:#555555;\">").append(label).append("</td>")
        .append("<td style=\"padding:3px 0;font-weight:bold;\">").append(value).append("</td>")
        .append("</tr>");
  }
}
