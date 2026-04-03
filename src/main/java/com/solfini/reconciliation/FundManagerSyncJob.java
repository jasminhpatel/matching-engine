package com.solfini.reconciliation;

import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;

import javax.mail.MessagingException;
import java.io.IOException;

public class FundManagerSyncJob {
  public static void main(String[] args) {
    try {
      final StringBuilder summary = new StringBuilder();
      boolean success = FundManagerSnapUpdater.update(args, summary, null);
      if (success) {
        FundManagerReconciliation.reconcile(args, summary, true);
      } else {
        System.out.println(summary);
        sendFailureEmail(summary);
      }
    } catch (Exception e) {
      final StringBuilder summary = new StringBuilder();
      summary.append("Ethereum–User Withdrawable Sync Job Failed:\n");
      summary.append(e.getMessage());
      try {
        sendFailureEmail(summary);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      e.printStackTrace();
      System.exit(1); // exit with error code
    }
    System.exit(0); // clean exit
  }

  private static void sendFailureEmail(final StringBuilder summary) throws MessagingException, IOException {
    summary.insert(0, "Ethereum–User Withdrawable Sync Job Failed:\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = "Ethereum–User Withdrawable Reconciliation Failed.";
    String body = summary.toString();

    MailUtil.sendMessage(to, subject, body, null);
  }
}
