package com.solfini.reconciliation;

import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;
import java.io.IOException;
import java.util.List;
import javax.mail.MessagingException;

public class FundManagerIntradaySyncJob {
  public static void main(String[] args) {
    try {
      final List<Integer> withdrawHeldUsers = WithdrawTxnCache.loadFromDB();
      if (withdrawHeldUsers.isEmpty()) {
        System.out.println("No onhold withdraw transactions.");
        return;
      } else {
        System.out.println("Onhold withdraw transactions detected for users: " + withdrawHeldUsers.toString());
      }
      final StringBuilder summary = new StringBuilder();
      boolean success = FundManagerSnapUpdater.update(args, summary, withdrawHeldUsers);
      if (success) {
        FundManagerReconciliation.reconcile(args, summary, false);
      } else {
        System.out.println(summary);
        sendFailureEmail(summary);
      }
    } catch (Exception e) {
      final StringBuilder summary = new StringBuilder();
      summary.append("FundManager Sync Job Failed:\n");
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
    summary.insert(0, "FundManager Intraday Sync Job Failed:\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = "FundManager Reconciliation Failed.";
    String body = summary.toString();

    MailUtil.sendMessage(to, subject, body, null);
  }
}
