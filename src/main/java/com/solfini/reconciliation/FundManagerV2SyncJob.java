package com.solfini.reconciliation;

import static com.solfini.common.Constants.XDC;
import static com.solfini.common.Constants.XUSDC;

import com.solfini.util.MailUtil;
import com.solfini.util.PropertyReader;
import java.io.IOException;
import javax.mail.MessagingException;

public class FundManagerV2SyncJob {
  public static void main(String[] args) {
    //todo uncomment after testing and execute in multiple threads
    //sync(args, MAINNET, USDC);
    //sync(args, MAINNET, USDT);
    sync(args, XDC, XUSDC);
    System.exit(0); // clean exit
  }

  private static void sync(String[] args, final String network, final String symbol) {
    final StringBuilder summary = new StringBuilder();
    try {
      boolean success = FundManagerV2SnapUpdater.update(args, summary, null, network, symbol);
      if (success) {
        FundManagerV2Reconciliation.reconcile(args, summary, true, network, symbol);
      } else {
        System.out.println(summary);
        sendFailureEmail(summary);
      }
    } catch (Exception e) {
      summary.append("Ethereum–User Withdrawable Sync Job Failed:\n");
      summary.append(e.getMessage());
      try {
        sendFailureEmail(summary);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      e.printStackTrace();
    }
    System.out.println(summary);
  }

  private static void sendFailureEmail(final StringBuilder summary) throws MessagingException, IOException {
    summary.insert(0, "Ethereum–User Withdrawable Sync Job Failed:\n");
    String[] to = PropertyReader.getProperty("RECONCILIATION_ALERT_EMAILS", "alerts.rohanw@gmail.com").split(",");
    String subject = "Ethereum–User Withdrawable Reconciliation Failed.";
    String body = summary.toString();

    MailUtil.sendMessage(to, subject, body, null);
  }
}
