package com.solfini.util;

import org.junit.Assert;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static com.solfini.util.MailUtil.EMAIL_ADDRESS;

public class MailUtilTest {

  @Test
  public  void sendMail() {
    String from = EMAIL_ADDRESS;
    String[] to = new String[1];
    to[0] = "commoditybull@gmail.com";
    String[] cc = new String[0];
    String[] bcc = new String[0];
    String subject = "Solfini Match Engine MailUtil Unit Test";
    String text = "Solfini Match Engine <b>MailUtil Test</b>";
    String html = "Solfini Match Engine <b>MailUtil Test</b>";
    byte[] excelBytes = null;
    String filename = null;
    List toList = new ArrayList();
    toList.add("commoditybull@gmail.com");

    try {
      // MailUtil.sendMessage(from, to, cc, bcc, subject, text, html, excelBytes, filename);
      Assert.assertTrue(MailUtil.sendMail(subject, text, "text/html", from, to, cc, bcc));
      // MailUtil.sendMail(subject, text, "text/html", from, host, user, password, toList);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
