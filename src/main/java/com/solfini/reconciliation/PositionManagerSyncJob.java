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
      final StringBuilder selfHealedSummary = new StringBuilder();
      boolean success = PositionManagerSnapUpdater.update(args, summary, selfHealedSummary);
      if (selfHealedSummary.length() > 0) {
        sendSelfHealedEmail(selfHealedSummary);
      }
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

  // A position whose cached snapshotId (per network) wasn't the immediately preceding snapshot got
  // re-synced this run - i.e. it had missed at least one cycle (e.g. a Polygon RPC outage) and just
  // caught up. Configure recipients via POSITION_MANAGER_SELF_HEAL_ALERT_EMAILS; separate from
  // RECONCILIATION_ALERT_EMAILS so this can be routed to a different list if desired. No default
  // recipient - if unset, this is skipped rather than falling back to any address.
  private static void sendSelfHealedEmail(final StringBuilder selfHealedSummary) throws MessagingException, IOException {
    final String configured = PropertyReader.getProperty("POSITION_MANAGER_SELF_HEAL_ALERT_EMAILS", "");
    if (configured.isBlank()) {
      System.out.println("POSITION_MANAGER_SELF_HEAL_ALERT_EMAILS not configured; skipping self-heal email. summary: " + selfHealedSummary);
      return;
    }

    String[] to = configured.split(",");
    String subject = "Position Manager: stale position(s) self-healed.";
    String body = selfHealedSummary.toString();

    MailUtil.sendMessage(to, subject, body, null);
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
