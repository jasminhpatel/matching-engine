package com.solfini.util;

import java.security.Security;
import java.util.Arrays;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

public class MailUtil implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(MailUtil.class);

  public static final String EMAIL_ADDRESS = "info@solfini.com";
  public static final String SMTP_HOST = "mail.smtp.host";
  public static final String SMTP_PORT = "mail.smtp.port";

  private static String user = PropertyReader.getProperty("mail.username", EMAIL_ADDRESS);
  private static String password = PropertyReader.getProperty("mail.password", EncryptDecrypt2.decrypt("V-r7Tpvr2_7lIaC9qnAMwA=="));
  private static String host = PropertyReader.getProperty(SMTP_HOST, "mail.solfini1.com");
  private static String from = PropertyReader.getProperty("mail.from", EMAIL_ADDRESS);
  private static String port = PropertyReader.getProperty(SMTP_PORT, "465");
  private static final String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
  private static final Properties props = buildProps();
  private static final Properties sslProps = buildSSLProps();

  private MailUtil() {
    // Private constructor
  }

  private static final Properties buildProps() {
    // Set the host
    Properties props = new Properties();
    props.put(SMTP_HOST, host);
    props.put("mail.smtp.auth", "true");
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.starttls.enable", "true");
    if ((port != null) && (port.length() > 0))
      props.put(SMTP_PORT, port);

    return props;
  }

  private static final Properties buildSSLProps() {
    Properties props = new Properties();
    props.put(SMTP_HOST, host);
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.auth", "true");
    props.put("mail.debug", "true");
    props.put(SMTP_PORT, port);
    props.put("mail.smtp.socketFactory.port", port);
    props.put("mail.smtp.socketFactory.class", SSL_FACTORY);
    props.put("mail.smtp.socketFactory.fallback", "false");
    props.put("mail.smtp.ssl.checkserveridentity", "true");

    return props;
  }

  public static boolean sendMail(final String subject, final String message, final String ctype, String masterUser, final String[] to,
      final String[] cc, final String[] bcc) {
    // Construct the automated message

    if (masterUser == null) {
      masterUser = from;
    }

    if ("smtp.gmail.com".equalsIgnoreCase(host) || "mail.name.com".equalsIgnoreCase(host)) {
      return sendSSLMessage(to, subject, message, ctype, from);
    }

    Session session = Session.getDefaultInstance(props, null);
    try {
      javax.mail.Message emsg = new MimeMessage(session);
      InternetAddress from = new InternetAddress(masterUser);
      InternetAddress[] replyto = {new InternetAddress(masterUser)};

      // Set the attributes for the Message object
      emsg.setFrom(from);
      if (to != null && to.length > 0) {
        for (int i = 0; i < to.length; i++) {
          emsg.addRecipient(Message.RecipientType.TO, new InternetAddress(to[i]));
        }
      }
      if (cc != null && cc.length > 0) {
        for (int i = 0; i < cc.length; i++) {
          emsg.addRecipient(Message.RecipientType.CC, new InternetAddress(cc[i]));
        }
      }
      if (bcc != null && bcc.length > 0) {
        for (int i = 0; i < bcc.length; i++) {
          emsg.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc[i]));
        }
      }
      emsg.setSubject(subject);
      emsg.setReplyTo(replyto);
      emsg.setContent(message, ctype);
      boolean auth = true;
      if (auth) {
        Transport tr = session.getTransport("smtp");
        tr.connect(host, user, password);
        LOGGER.error(LOG_FMT_2, "Trying auth...connect=", tr.isConnected());
        emsg.saveChanges(); // don't forget this
        tr.sendMessage(emsg, emsg.getAllRecipients());
        tr.close();
      } else
        Transport.send(emsg);

      LOGGER.debug("Message Sent");
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
      return false;
    }
    return true;
  }

  public static void sendMessage(final String from, final String[] to, final String[] cc, final String[] bcc, final String subject,
      final String text, final String html, final byte[] excelBytes, final String filename) throws MessagingException {

    Session session = Session.getDefaultInstance(props, null);

    // Define message
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress(from));
    if (to != null && to.length > 0) {
      for (int i = 0; i < to.length; i++) {
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to[i]));
      }
    }
    if (cc != null && cc.length > 0) {
      for (int i = 0; i < cc.length; i++) {
        message.addRecipient(Message.RecipientType.CC, new InternetAddress(cc[i]));
      }
    }
    if (bcc != null && bcc.length > 0) {
      for (int i = 0; i < bcc.length; i++) {
        message.addRecipient(Message.RecipientType.BCC, new InternetAddress(bcc[i]));
      }
    }
    message.setSubject(subject);

    MimeMultipart content = new MimeMultipart("alternative");
    MimeBodyPart textPart = new MimeBodyPart();
    MimeBodyPart htmlPart = new MimeBodyPart();
    textPart.setText(text);
    htmlPart.setContent(html, "text/html");
    content.addBodyPart(textPart);
    content.addBodyPart(htmlPart);


    // attach excel file
    if ((excelBytes != null) && (filename != null)) {
      MimeBodyPart excelPart = new MimeBodyPart();
      ByteArrayDataSource ds = new ByteArrayDataSource(excelBytes, "application/vnd.ms-excel");
      excelPart.setDataHandler(new DataHandler(ds));
      excelPart.setFileName(filename);
      content.addBodyPart(excelPart);
    }

    message.setContent(content);

    // Send message
    Transport.send(message);
  }

  static {
    Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
  }

  public static final boolean sendSSLMessage(final String[] recipients, final String subject, final String message, final String ctype,
      final String from) {
    LOGGER.debug(LOG_FMT_8, ">>> sendSSLMessage recipients=", Arrays.toString(recipients), ", from=", from, ", subject=", subject,
        MESSAGE_EQ, message);

    Session session = Session.getDefaultInstance(sslProps, new javax.mail.Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, password);
      }
    });
    session.setDebug(false);

    try {
      Message msg = new MimeMessage(session);
      InternetAddress addressFrom = new InternetAddress(from);
      msg.setFrom(addressFrom);

      InternetAddress[] addressTo = new InternetAddress[recipients.length];
      for (int i = 0; i < recipients.length; i++) {
        addressTo[i] = new InternetAddress(recipients[i]);
      }
      msg.setRecipients(Message.RecipientType.TO, addressTo);

      // Setting the Subject and Content Type
      msg.setSubject(subject);
      msg.setContent(message, ctype);
      LOGGER.info("sendSSLMessage sending");
      Transport.send(msg);
    } catch (Exception e) {
      LOGGER.error("error in sendSSLMessage", e);
      return false;
    }
    LOGGER.info("sendSSLMessage SSL Message Sent");
    return true;
  }
}
