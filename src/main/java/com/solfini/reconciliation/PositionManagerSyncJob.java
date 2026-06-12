package com.solfini.reconciliation;

import static com.solfini.common.Constants.POLYGON;
import static com.solfini.common.Constants.XDC;

import com.solfini.common.Context;
import com.solfini.util.MailUtil;
import com.solfini.util.MailUtil.MailAttachment;
import com.solfini.util.PropertyReader;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.mail.MessagingException;
import java.io.IOException;

public class PositionManagerSyncJob {
  public static void main(String[] args) {
    sync(args);
  }

  public static void sync(final String[] args) {
    try {
      final StringBuilder summary = new StringBuilder();
      final StringBuilder userPositions = new StringBuilder();
      boolean success = PositionManagerSnapUpdater.update(args, summary);
      if (success) {
        final StringBuilder marketMakerPositions = new StringBuilder();
        success = PositionManagerReconciliation.reconcile(args, summary, userPositions, marketMakerPositions);
        if (success) {
          if (sendSuccessEmail()) {
            sendReconciliationSuccessEmail(summary, userPositions, marketMakerPositions);
          }
        } else {
          sendReconciliationFailureEmail(summary, userPositions);
        }
      } else {
        System.out.println("Position update summary: " + summary);
        sendUpdateFailureEmail(summary);
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1); // exit with error code
    }
    System.exit(0); // clean exit
  }

  private static void sendUpdateFailureEmail(final StringBuilder summary) throws MessagingException, IOException {
    summary.insert(0, "Polygon-User Position Sync Job Failed:\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = "Polygon-User Position Update Failed.";
    String body = summary.toString();

    MailUtil.sendMessage(to, subject, body, null);
  }

  private static void sendReconciliationFailureEmail(final StringBuilder summary, final StringBuilder userPositions)
      throws MessagingException, IOException {
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = "Polygon-User Position Reconciliation Failed.";
    String body = summary.toString();
    List<MailAttachment> mailAttachments = new ArrayList<>(1);
    MailAttachment mailAttachment = new MailAttachment("reconciliation_report.csv", "text/csv", userPositions.toString());
    mailAttachments.add(mailAttachment);

    MailUtil.sendMessage(to, subject, body, mailAttachments);
  }

  private static void sendReconciliationSuccessEmail(final StringBuilder summary, final StringBuilder userPositions, final StringBuilder marketMakerPositions)
      throws MessagingException, IOException {
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = "Polygon-User Position Reconciliation Successful.";
    String body = summary.toString();
    List<MailAttachment> mailAttachments = new ArrayList<>(2);
    mailAttachments.add(new MailAttachment("reconciliation_report.csv", "text/csv", userPositions.toString()));
    mailAttachments.add(new MailAttachment("market_maker_positions.csv", "text/csv", marketMakerPositions.toString()));

    MailUtil.sendMessage(to, subject, body, mailAttachments);
  }

  private static boolean sendSuccessEmail() {
    LocalTime now = LocalTime.now();
    final int sendSuccessEmailAtHour = Context.getPositionManagerEodEmailAtHour();

    return now.getHour() == sendSuccessEmailAtHour && (now.getMinute() < 10);
  }
}
