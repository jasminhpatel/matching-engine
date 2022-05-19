package com.solfini.util;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MemoryMappedUtil {

  private final RandomAccessFile memoryMappedFile;
  // Mapping a file into memory
  private final MappedByteBuffer buffer;
  private final long byteCapacity;
  private final long recordCapacity;
  private final int recordBytesWithHeader;
  private int byteMarker;
  private int recordMarker;
  private long seqNum;

  public MemoryMappedUtil(final String path, final long recordCapacity, final int recordBytes) throws IOException {
    memoryMappedFile = new RandomAccessFile(path, "rw");
    this.recordCapacity = recordCapacity;
    this.recordBytesWithHeader = recordBytes + 8;
    byteCapacity = recordCapacity * recordBytes;
    buffer = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, byteCapacity);
  }

  public final int getRecordMarker() {
    return recordMarker;
  }

  public final void setRecordMarker(int recordMarker) {
    this.recordMarker = recordMarker;
  }

  public final long getSeqNum() {
    return seqNum;
  }

  public final void setSeqNum(long seqNum) {
    this.seqNum = seqNum;
  }

  public final long getByteCapacity() {
    return byteCapacity;
  }

  public final long getRecordCapacity() {
    return recordCapacity;
  }

  public final MappedByteBuffer getBuffer() {
    return buffer;
  }

  public final long append(final byte[] data) {
    seqNum++;
    recordMarker++;
    byteMarker = recordMarker * recordBytesWithHeader;
    buffer.putLong(byteMarker, seqNum);
    for (int i = 0; i < data.length; i++)
      buffer.put(byteMarker + 8 + i, data[i]);

    return seqNum;
  }

  public final void put(final int recordIndex, final long seqNum, final byte[] data) {
    byteMarker = recordIndex * recordBytesWithHeader;
    buffer.putLong(byteMarker, seqNum);
    for (int i = 0; i < data.length; i++)
      buffer.put(byteMarker + 8 + i, data[i]);
  }
}
