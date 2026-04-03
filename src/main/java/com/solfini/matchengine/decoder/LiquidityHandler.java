package com.solfini.matchengine.decoder;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.matchengine.message.internal.LiquidityResponse;
import com.solfini.sbe.encoder.LiquidityResponseDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;

public class LiquidityHandler implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(LiquidityHandler.class);

  public final Message decodeLiquidityMessage(final MessageHeaderDecoder headerDecoder, final LiquidityResponseDecoder liquidityDecoder) {
    try {
      final LiquidityResponse liquidityMessage = new LiquidityResponse();
      liquidityMessage.set(liquidityDecoder);

      return liquidityMessage;

    } catch (Exception e) {
      LOGGER.error(ERROR_LOG, e);
    }
    return null;
  }
}
