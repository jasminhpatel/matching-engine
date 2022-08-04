package com.solfini.matchengine.decoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.solfini.common.Constants;
import com.solfini.common.Message;
import com.solfini.matchengine.message.internal.AssetGroup;
import com.solfini.pool.AssetGroupObjectPool;
import com.solfini.sbe.encoder.AssetGroupDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;

/**
 *
 * @author Chris Mack
 *
 */
public class AssetGroupRequestHandler implements Constants {
  private static final Logger LOGGER = LoggerFactory.getLogger(AssetGroupRequestHandler.class);


  public final Message decodeAssetGroupRequest(final MessageHeaderDecoder headerDecoder, final AssetGroupDecoder assetGroupDecoder) {
    try {
      final AssetGroup assetGroup = AssetGroupObjectPool.get();
      assetGroup.set(assetGroupDecoder);
      assetGroup.setSenderCompId(headerDecoder.senderCompId());
      assetGroup.setKafkaRecordOffset(headerDecoder.kafkaRecordOffset());

      return assetGroup;
    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }

    return null;
  }

}
