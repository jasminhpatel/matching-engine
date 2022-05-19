/*
 * Created on May 25, 2005 TODO To change the template for this generated file go to Window - Preferences - Java - Code Style - Code
 * Templates
 */
package com.solfini.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.slf4j.LoggerFactory;

import com.solfini.common.Constants;

import org.slf4j.Logger;

/**
 * Class FileStore.
 *
 * @author Chris.
 */
public class FileAppenderUtil implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileAppenderUtil.class);

  private final String DIR;
  private final String extension;
  private FileChannel[] channelArr;
  private final ByteBuffer buffer = ByteBuffer.allocateDirect(32_768);

  public FileAppenderUtil(final String DIR, final String extension) {
    this.DIR = DIR;
    this.extension = extension;
    channelArr = new FileChannel[16];
  }

  // 50k in 1 second, keeps channels open
  public final void appendWrite(final int fileId, final byte[] data) throws IOException {
    if (fileId >= channelArr.length)
      channelArr = Arrays.copyOf(channelArr, fileId + 16);

    if (channelArr[fileId] == null) {
      Path pathTemp = FileSystems.getDefault().getPath(DIR + fileId + extension);
      channelArr[fileId] = FileChannel.open(pathTemp, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    buffer.clear();
    buffer.put(data);
    buffer.flip();
    channelArr[fileId].write(buffer);
  }


  public static void createIfNotExists(final String path) {
    final File dir = new File(path);
    if (!dir.exists())
      dir.mkdirs();
  }

  // 50k in 1 second, keeps channels open
  private final Path path = FileSystems.getDefault().getPath("/reports/fundingRateCalc/fundingRateCalc.log");

  public final void appendWrite(final String input) {
    try (final FileChannel fileChannel =
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {

      final byte[] data = (input + "\n").getBytes();
      buffer.clear();
      buffer.put(data);
      buffer.flip();
      fileChannel.write(buffer);
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
  }
}
