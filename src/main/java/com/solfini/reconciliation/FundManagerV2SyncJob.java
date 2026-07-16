package com.solfini.reconciliation;

import static com.solfini.common.Constants.MAINNET;
import static com.solfini.common.Constants.USDC;
import static com.solfini.common.Constants.USDT;
import static com.solfini.common.Constants.XDC;
import static com.solfini.common.Constants.XUSDC;

import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;
import java.io.IOException;
import javax.mail.MessagingException;

public class FundManagerV2SyncJob {

  public FundManagerV2SyncJob() {
  }

  public static void main(String[] args) {
    final SnapContext snapContext;
    try {
      snapContext = FundManagerV2SnapUpdater.resolveSnapContext(args);
    } catch (Exception e) {
      final StringBuilder summary = new StringBuilder();
      summary.append("Failed to resolve snapshot context (affects all chains):\n").append(e.getMessage());
      try {
        String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
        MailUtil.sendMessage(to, "Blockchain–User Withdrawable Reconciliation Failed.", summary.toString(), null);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      e.printStackTrace();
      System.out.println(summary);
      System.exit(1);
      return;
    }

    try {
      sync(args, MAINNET, USDC, snapContext);
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      sync(args, MAINNET, USDT, snapContext);
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      sync(args, XDC, XUSDC, snapContext);
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.exit(0);
  }

  private static void sync(String[] args, final String network, final String symbol,
      final SnapContext snapContext) {
    final StringBuilder summary = new StringBuilder();
    try {
      boolean success = FundManagerV2SnapUpdater.update(args, summary, null, network, symbol, snapContext);
      if (success) {
        FundManagerV2Reconciliation.reconcile(args, summary, true, network, symbol);
      } else {
        System.out.println(summary);
        sendFailureEmail(summary, network);
      }
    } catch (Exception e) {
      summary.append(FundManagerV2Reconciliation.networkLabel(network)).append("–User Withdrawable Sync Job Failed:\n");
      summary.append(e.getMessage());
      try {
        sendFailureEmail(summary, network);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      e.printStackTrace();
    }
    System.out.println(summary);
  }

  private static void sendFailureEmail(final StringBuilder summary, final String network) throws MessagingException, IOException {
    final String networkLabel = FundManagerV2Reconciliation.networkLabel(network);
    summary.insert(0, networkLabel + "–User Withdrawable Sync Job Failed:\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = networkLabel + "–User Withdrawable Reconciliation Failed.";
    String body = summary.toString();

    MailUtil.sendMessage(to, subject, body, null);
  }
}
