package com.solfini.util;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.util.MailUtil.MailAttachment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AsyncSender implements Runnable {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AsyncSender.class);
  private final String subject;
  private final String body;
  private final String contentType;
  private final String filename;
  private final byte[] content;
  private final boolean writeToFile;

  public AsyncSender(final String subject, final String body, final String contentType,
      final String filename, final byte[] content, final boolean writeToFile) {
    this.subject = subject;
    this.body = body;
    this.contentType = contentType;
    this.filename = filename;
    this.content = content;
    this.writeToFile = writeToFile;
  }

  public static void processInANewThread(final String subject, final String body, final String contentType,
      final String filename, final byte[] content) {
    Thread t = new Thread(new AsyncSender(subject, body, contentType, filename, content, true));
    t.start();
  }

  public static void sendEmailInANewThread(final String subject, final String body, final String contentType,
      final String filename, final byte[] content) {
    Thread t = new Thread(new AsyncSender(subject, body, contentType, filename, content, false));
    t.start();
  }

  @Override
  public void run() {
    if (this.writeToFile) {
      writeContentToFile();
    }
    sendEmail();
  }

  private void sendEmail() {
    try {
      final String[] to = Context.getReconciliationAlertEmails().split(",");

      List<MailAttachment> mailAttachments = new ArrayList<>(1);
      mailAttachments.add(new MailAttachment(filename, contentType, new String(content)));

      MailUtil.sendMessage(to, subject, body, mailAttachments);
      LOGGER.info("Email sent successfully successfully:");
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private void writeContentToFile() {
    try {
      final LocalDateTime now = LocalDateTime.now();
      final String datedFilename = filename + "-" + now.getYear() + "-" + now.getMonth() + "-" + now.getDayOfMonth() + ".csv";
      final Path filePath = Paths.get(Context.getReconciliationOutputDirectory(), datedFilename);

      Files.write(filePath, content,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);

      LOGGER.info(Constants.LOG_FMT_2, "File written successfully: ", filePath);

    } catch (IOException e) {
      LOGGER.error(Constants.ERROR_LOG, e);
    }
  }
}
