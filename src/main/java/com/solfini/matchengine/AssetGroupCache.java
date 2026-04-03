package com.solfini.matchengine;

import java.time.Duration;
import java.util.*;

import com.solfini.common.Constants;
import com.solfini.common.Context;
import com.solfini.common.CustomLogger;
import com.solfini.common.ManyToOneConcurrentArrayQueueCustom;
import com.solfini.common.Message;
import com.solfini.internal.admin.schema.MarketStatus;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.TokenType;
import com.solfini.sbe.encoder.UpdateType;
import com.solfini.util.PropertyReader;
import com.solfini.util.TimeUtil;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

public class AssetGroupCache implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(AssetGroupCache.class);
  //<groupId, AssetGroup>
  private static final Long2ObjectHashMap<AssetGroup> idToAssetGroupMap = new Long2ObjectHashMap<>();
  //<userId, <groupId, AssetGroup>>
  private static final Long2ObjectHashMap<Long2ObjectHashMap<AssetGroup>> userIdToAssetGroupsMap = new Long2ObjectHashMap<>();
  //<userId, <assetId, AssetGroup>> ERC 20
  private static final Long2ObjectHashMap<Long2ObjectHashMap<AssetGroup>> userIdToAssetIdToAssetGroupsMap = new Long2ObjectHashMap<>();

  private static long nextId = 1;
  private static long lastUpdatedTime = 0;
  private static final long TIME_THRESHOLD = 300_000;

  private static final ManyToOneConcurrentArrayQueueCustom<Message> matcherToPublisherQueue = Context.getMatcherToPublisherQueue();

  public static final void onLoad(final AssetGroup assetGroup) {
    idToAssetGroupMap.put(assetGroup.getId(), assetGroup);
    if (assetGroup.getId() >= nextId)
      nextId = assetGroup.getId() + 1;

    // cache by userId
    final Long2ObjectHashMap<AssetGroup> assetGroupsForUser = userIdToAssetGroupsMap.computeIfAbsent(assetGroup.getOwnerUserId(),
        v -> new Long2ObjectHashMap<>());
    assetGroupsForUser.put(assetGroup.getId(), assetGroup);

    // cache by securityId
    if (TokenType.ERC20_GROUP == assetGroup.getTokenType()) {
      final Long2ObjectHashMap<AssetGroup> userIdToERC20AssetGroupsMap =
          userIdToAssetIdToAssetGroupsMap.computeIfAbsent(assetGroup.getOwnerUserId(), v -> new Long2ObjectHashMap<>());
      userIdToERC20AssetGroupsMap.put(assetGroup.getAssetId(), assetGroup);
    }
  }

  // update model
  public static final AssetGroup onModel(final AssetGroup assetGroup) {
    if (assetGroup.getId() == 0) { // assign new id
      assetGroup.setId(nextId);
      nextId++;
    }

    final AssetGroup prevAssetGroup = idToAssetGroupMap.get(assetGroup.getId());
    if (prevAssetGroup == null) { // new add
      if (UpdateType.DELETE != assetGroup.getUpdateType())
        onLoad(assetGroup);
    } else {
      //if owner has changed
      if (prevAssetGroup.getOwnerUserId() != assetGroup.getOwnerUserId()) {
        Long2ObjectHashMap<AssetGroup> userAssetGroups = userIdToAssetGroupsMap.get(prevAssetGroup.getOwnerUserId());
        if (userAssetGroups != null) {
          userAssetGroups.remove(prevAssetGroup.getId());
        }
        if (TokenType.ERC20_GROUP == prevAssetGroup.getTokenType()) {
          userAssetGroups = userIdToAssetIdToAssetGroupsMap.get(prevAssetGroup.getOwnerUserId());
          if (userAssetGroups != null) {
            userAssetGroups.remove(prevAssetGroup.getAssetId());
          }
        }
      }
      prevAssetGroup.copySet(assetGroup);

      if (UpdateType.DELETE == assetGroup.getUpdateType())
        prevAssetGroup.removeAll(assetGroup.getAssetIdGroupTreeSet());
      else
        prevAssetGroup.addAll(assetGroup.getAssetIdGroupTreeSet());
    }

    if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
      matcherToPublisherQueue.addGuaranteed(assetGroup);
    }

    return assetGroup;
  }

  public static final AssetGroup get(final long id) {
    final AssetGroup assetGroup = idToAssetGroupMap.get(id);
    if (assetGroup != null)
      return assetGroup;

    return null;
  }

  // getAll with filters
  public static final Collection<AssetGroup> getAll() {
    return idToAssetGroupMap.values();
  }

  public static final Collection<AssetGroup> getByUserId(final long userId, final int securityId) {
    final Long2ObjectHashMap<AssetGroup> userAssetGroupMap = userIdToAssetGroupsMap.get(userId);
    if (userAssetGroupMap != null && userAssetGroupMap.size() > 0) {
      final ArrayList<AssetGroup> list = new ArrayList<>();
      for (AssetGroup assetGroup : userAssetGroupMap.values()) {
        if (assetGroup.getSecurityId() == securityId)
          list.add(assetGroup);
      }

      return list;
    }
    return new ArrayList<>();
  }

  public static final AssetGroup getByUserIdAndERC20Asset(final long userId, final long assetId) {
    final Long2ObjectHashMap<AssetGroup> userAssetGroupMap = userIdToAssetIdToAssetGroupsMap.get(userId);
    if (userAssetGroupMap != null) {
      return userAssetGroupMap.get(assetId);
    }

    return null;
  }

  // called for snapshots
  public static final void restateAllAssetGroups(final long snapId) {
    for (final AssetGroup assetGroupSrc : idToAssetGroupMap.values()) {
      final AssetGroup assetGroup = new AssetGroup();
      assetGroup.copySet(assetGroupSrc);
      assetGroup.addAll(assetGroupSrc.getAssetIdGroupTreeSet());
      assetGroup.setSnapId(snapId);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_6, RESTATEALL_ASSET_GROUPS_EQ, assetGroup);
      }
      if (MarketStatus.DR_MODE != Context.getMarketStatus()) {
        matcherToPublisherQueue.addGuaranteed(assetGroup);
      }
    }

  }

  public static void loadAssetGroupsFromKafka() {
    if (Context.getAssetGroupsCompactionTopic() != null) {
      LOGGER.info(Constants.LOG_FMT_2, "Loading assetGroups from kafka topic: ", Context.getAssetGroupsCompactionTopic());
      final KafkaAssetGroupListener kafkaAssetGroupListener = new KafkaAssetGroupListener(Context.getAssetGroupsCompactionTopic());
      kafkaAssetGroupListener.loadAssetGroupsFromKafka();
    } else {
      LOGGER.info(Constants.LOG_FMT_2, "Unable to load assetGroups ASSET_GROUPS_COMPACTION_TOPIC not defined: ");
    }
  }

  public static class KafkaAssetGroupListener {
    private final Properties properties;
    private final String topic;
    // process bytes with 19 offset
    private static final int OFFSET = 19;
    private final UnsafeBuffer decoderUnsafeBuffer = new UnsafeBuffer();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final AssetGroupDecoder assetGroupDecoder = new AssetGroupDecoder();

    public KafkaAssetGroupListener(final String topic) {
      this.topic = topic;
      this.properties = new Properties();
      this.properties.putAll(PropertyReader.getPropertyGroup("KAFKA.CONSUMER"));
      if (this.properties.getProperty("group.id") == null) {
        this.properties.setProperty("group.id", "group-" + new Random().nextInt(2_000_000_000));
      }
      this.properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      this.properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    }

    //The AssetGroup topic is a compacted topic: it keeps only the last message for each primary key.
    //Before loading the snap file, keep a copy of previous messages, as the snap loading process emits AssetGroup messages when processing BalanceAdminMessage.
    public void loadAssetGroupsFromKafka() {
      LOGGER.info(Constants.LOG_FMT_2, "AssetCache size before: ", idToAssetGroupMap.size(), " next: " + nextId);
      try (final KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(this.properties)) {
        final List<TopicPartition> partitions = new ArrayList<>();
        for (PartitionInfo partitionInfo : consumer.partitionsFor(topic)) {
          partitions.add(new TopicPartition(topic, partitionInfo.partition()));
        }

        consumer.assign(partitions);
        consumer.seekToBeginning(partitions);

        final Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

        // Check if topic is empty
        boolean topicEmpty = true;
        for (TopicPartition partition : partitions) {
          long beginningOffset = consumer.position(partition);
          long endOffset = endOffsets.get(partition);
          if (endOffset > beginningOffset) {
            topicEmpty = false;
            break;
          }
        }

        if (topicEmpty) {
          LOGGER.info("Topic is empty, no messages to process");
          LOGGER.info(Constants.LOG_FMT_2, "AssetCache size after: ", idToAssetGroupMap.size(), " next: " + nextId);
          return;
        }

        boolean running = true;
        long startKafkaOffset = 0;
        long lastKafkaOffset = 0;
        long endOffset = endOffsets.get(partitions.getFirst());

        while (running) {
          final ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));

          // Exit if no records and we've reached the end
          if (records.isEmpty()) {
            // Check if we've processed all available messages
            boolean allPartitionsAtEnd = true;
            for (TopicPartition partition : partitions) {
              if (consumer.position(partition) < endOffsets.get(partition)) {
                allPartitionsAtEnd = false;
                break;
              }
            }
            if (allPartitionsAtEnd) {
              LOGGER.info("No more messages available, exiting");
              running = false;
              continue;
            }
          }

          boolean firstMessageReceived = false;
          for (final ConsumerRecord<String, byte[]> record : records) {
            try {
              final byte[] readData = record.value();
              final long kafkaOffset = record.offset();

              long seqNum = 0;
              for (int i = 0; i < 8; i++) {
                seqNum <<= 8;
                seqNum |= (readData[i] & 0xFF);
              }

              long sendTime = 0;
              for (int i = 8; i < 16; i++) {
                sendTime <<= 8;
                sendTime |= (readData[i] & 0xFF);
              }
              // messageType
              final byte messageType = readData[16];

              if (!firstMessageReceived) {
                firstMessageReceived = true;
                startKafkaOffset = kafkaOffset;
              }
              lastKafkaOffset = kafkaOffset;
              // process bytes with 17 offset
              onMessage(seqNum, sendTime, kafkaOffset, messageType, readData);
            } catch (Exception e) {
              LOGGER.error(ERROR_LOG, e);
            }
            // Stop when the end offset is reached
            //long endOffset = endOffsets.get(new TopicPartition(record.topic(), record.partition()));
            if (record.offset() + 1 >= endOffset) {
              LOGGER.info(Constants.LOG_FMT_2, "AssetGroup loading completed.");
              running = false;
            }
          }
        }
        LOGGER.info(Constants.LOG_FMT_2, "AssetCache size after: ", idToAssetGroupMap.size(), " next: " + nextId);
        LOGGER.info(Constants.LOG_FMT_4, "AssetGroups loaded from Kafka. startKafkaOffset: ", startKafkaOffset, " endKafkaOffset: ", lastKafkaOffset);
      }
    }

    public void onMessage(final long seqNum, final long sendTime, final long recordOffset, final byte messageType, final byte[] data) {
      final long latency = (TimeUtil.getTime() - sendTime) / 1000; // in microseconds
      final int length = data.length - OFFSET;

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(LOG_FMT_10, RECEIVED_SEQNUM_EQ, seqNum, SEND_TIME_EQ, sendTime, RECORDOFFSET_EQ, recordOffset, MESSAGETYPE_EQ,
            messageType, LATENCY_EQ, latency);
      }

      try {
        final Message message = decode(data, length);

        if (message instanceof AssetGroup assetGroup) {
          assetGroup.setSourceSeqNum(seqNum);
          assetGroup.setSourceSendTime(sendTime);
          assetGroup.setKafkaRecordOffset(recordOffset);
          LOGGER.info(assetGroup.toJSON());
          AssetGroupCache.onModel(assetGroup);// read before snap load and update cache after snap is loaded.
        }

      } catch (Exception e) {
        e.printStackTrace();
        LOGGER.error("KafkaAssetGroupListener error ", e);
        if (e.getMessage().contains("Queue")) {
          LOGGER.error("KafkaAssetGroupListener Queue error sleeping");
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e1) {
            e1.printStackTrace();
          }
        }
      }
    }

    private Message decode(final byte[] data, final int length) {
      // wrap bytes
      decoderUnsafeBuffer.wrap(data);
      headerDecoder.wrap(decoderUnsafeBuffer, OFFSET);

      try {
        Message message;
        switch (headerDecoder.templateId()) {
          case AssetGroupDecoder.TEMPLATE_ID:
            assetGroupDecoder.wrap(decoderUnsafeBuffer, OFFSET + headerDecoder.encodedLength(), headerDecoder.blockLength(),
                headerDecoder.version());

            message = new AssetGroup(assetGroupDecoder);
            message.setSenderCompId(headerDecoder.senderCompId());
            message.setSequenceNumber(headerDecoder.msgSeqNum());
            message.setSourceSeqNum(headerDecoder.sourceSeqNum());
            message.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());
            return message;

          default:
            if (LOGGER.isWarnEnabled()) {
              LOGGER.warn(LOG_FMT_2, UNABLE_TO_DECODE_MSGTYPE, data);
            }
            return null;
        }
      } catch (Exception e) {
        LOGGER.error(DECODE_ERROR_MSGTYPE_EQ, e);
        throw e;
      }
    }
  }
}
