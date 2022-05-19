/*
 * Created on May 25, 2005 TODO To change the template for this generated file go to Window - Preferences - Java - Code Style - Code
 * Templates
 */
package com.solfini.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;

/**
 * Class FileStore.
 *
 * @author Chris.
 */
public class FileStore implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(FileStore.class);

  private static String fileStorePath = null;

  private FileStore() {
    // do nothing
  }

  public static String getFileStorePath() {
    if (fileStorePath == null) {
      fileStorePath = PropertyReader.getProperty("FILE_STORE_PATH", "");
    }
    return fileStorePath;
  }

  public static String writeFile(int id, byte[] data) {
    try {
      String path = getFileStorePath();
      String dir = "" + id / 1000;
      String filepath = path + dir;
      String totalpath = filepath + File.separator + id + ".bin";
      File f = new File(filepath);
      f.mkdirs();
      f = new File(totalpath);
      boolean created = f.createNewFile();
      LOGGER.info(LOG_FMT_6, ">>>>>>> writeFile created=", created, ", location=", filepath, File.separator, id, ".bin");

      try (FileOutputStream fos = new FileOutputStream(f);) {
        fos.write(data);
        fos.flush();
        return totalpath;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        return null;
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return null;
  }

  public static String writeFile(String filename, byte[] data) {
    try {
      String path = getFileStorePath();
      String dir = "0";
      String filepath = path + dir;
      String totalpath = filepath + File.separator + filename;
      File f = new File(filepath);
      f.mkdirs();
      f = new File(totalpath);
      boolean created = f.createNewFile();
      LOGGER.info(LOG_FMT_6, ">>>>>>> writeFile created=", created, ", location=", filepath, File.separator, filename);

      try (FileOutputStream fos = new FileOutputStream(f);) {
        fos.write(data);
        fos.flush();
        return totalpath;
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        return null;
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return null;
  }

  public static byte[] readFile(int id) {
    byte[] bytes = null;
    try {
      String path = getFileStorePath();
      String dir = "" + id / 1000;
      String filepath = path + dir;
      File file = new File(filepath + File.separator + id + ".bin");

      try (InputStream is = new FileInputStream(file);) {
        LOGGER.info(LOG_FMT_4, "location=", filepath, File.separator, id, ".bin");
        // Create the byte array to hold the data
        long length = file.length();
        bytes = new byte[(int) length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
          offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
          throw new IOException("Could not completely read file " + file.getName());
        }
      } catch (Exception e) {
        LOGGER.error(ERROR_LOG, e);
        return bytes;
      }
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return bytes;
  }

  public static void createIfNotExists(final String path) {
    final File dir = new File(path);
    if (!dir.exists())
      dir.mkdirs();
  }
}
