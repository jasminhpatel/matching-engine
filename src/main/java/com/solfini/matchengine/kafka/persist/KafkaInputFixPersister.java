package com.solfini.matchengine.kafka.persist;

import java.util.Arrays;
import java.util.List;
import org.apache.kafka.common.TopicPartition;

import com.solfini.common.CustomLogger;
import com.solfini.matchengine.kafka.KafkaInputFixListener;
import com.solfini.util.StringUtil;

/**
 *
 * @author Chris Mack
 *
 */
public class KafkaInputFixPersister extends KafkaInputFixListener {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(KafkaInputFixPersister.class);

  private final KafkaPersistUtil kafkaPersistUtil;
  private final long stopTime;
  private final long startOffset;

  public KafkaInputFixPersister(final long snapId, final long replayId, final long stopTime, final long startOffset) {
    super(true); // replay=true

    this.stopTime = stopTime;
    this.startOffset = startOffset;

    kafkaPersistUtil = new KafkaPersistUtil(snapId, replayId);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(LOG_FMT_6, "KafkaPersistUtil=", API_KAFKA_TOPIC_IN, "snapId=", snapId, "replayId=", replayId);
    }
  }

  @Override
  public final void start() {
    if (startOffset < 0)
      super.start(); // attempt to lookup offset
    else {
      // Seek to the correct position of the input topic
      final TopicPartition partition = new TopicPartition(getTopic(), 0);
      final List<TopicPartition> partitions = Arrays.asList(partition);
      getConsumer().assign(partitions);

      getConsumer().seek(partition, startOffset);
      if (LOGGER.isInfoEnabled()) {
        LOGGER.debug(LOG_FMT_2, "Moving input queue cursor to last processed offset: ", startOffset);
      }
    }
  }

  @Override
  public final void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {

    try {
      final byte[] dest = new byte[data.length + 8];
      System.arraycopy(data, 0, dest, 8, data.length);
      kafkaPersistUtil.persist(seqNum, sendTime, recordOffset, messageType, dest);


      // check stopTime
      if(stopTime>0) {
    	  final String content = StringUtil.fixToString(data);
    	  int index = content.indexOf(" 52=20");
    	  if(index>0 && content.indexOf("FIX")>0) {
    		 String datetime= content.substring(index+4, index+25);
    		 long millis = StringUtil.getMillisFromDateYYYMMDDHHMMSSsss(datetime);
    		 if(millis>stopTime) {
    			 // stop
    			 kafkaPersistUtil.close();
    			 LOGGER.warn("Stopping KafkaInputFixPersister");
    			 System.out.println("Stopping KafkaInputFixPersister");
    			 Thread.sleep(1000);
    			 System.exit(0);
    		 }
    	  }
      }

    } catch (Exception e) {
      // decode error!!!
      LOGGER.error("KafkaInputFixPersister error, msgType=" + messageType + LENGTH_EQ + data.length + MSGSEQNUM_EQ + seqNum + SB_EQ
          + StringUtil.fixToString(data), e);
    }
  }

  public static void main(String[] args) {
	  String content = "56=test 34=159840 52=20190803-14:01:09.123 11=0nkC2AFFvZAN7o4DQ907Me 1";

	  int index = content.indexOf(" 52=20");
	  if(index>0 && content.indexOf("FIX")>0) {
		 String datetime= content.substring(index+4, index+25);
		 System.out.println("datetime="+datetime+"|");
		 System.out.println("datetime="+datetime+"|");

	  }
	  System.out.println(System.currentTimeMillis());
  }

}
