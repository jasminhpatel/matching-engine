package com.solfini.util;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.sbe.encoder.MsgType;


/**
 *
 * @author Chris Mack
 *
 *         see DecoderTest for example
 */
public class FixDecoderUtil implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(FixDecoderUtil.class);

  // process bytes with 17 offset
  private static final int KAFKA_OFFSET = 17;
  private static final int FIX_OFFSET = 11; // "FIX.4.4 9=?"
  private static final int FIX_HEADER_SKIP = KAFKA_OFFSET + FIX_OFFSET;

  private FixDecoderUtil() {
    // hidden default constructor
  }

  // default offsets to FIX_HEADER_SKIP = KAFKA_OFFSET + FIX_OFFSET
  public static final MsgType decodeMsgType(final byte[] data) {
    try {
      final int length = data.length;
      // manually decode msgType from header
      for (int i = FIX_HEADER_SKIP; i < length; i++) {
        if (data[i] == '=' && (data[i - 2] == '3') && (data[i - 1] == '5')) {
          switch (data[i + 1]) {
            case 'A':
              switch (data[i + 2]) {
                case 'P':
                  return MsgType.POSITION_REPORT; // AP
                default:
                  return MsgType.LOGON; // A
              }

            case '8':
              return MsgType.EXECUTION_REPORT;
            case 'D':
              return MsgType.ORDER_SINGLE;
            case 'B':
              switch (data[i + 2]) {
                case 'D':
                  return MsgType.NETWORK_STATUS_RESPONSE; // BD
                default:
                  return MsgType.NEWS; // B
              }
            case '5':
              return MsgType.LOGOUT;
            case 'F':
              return MsgType.ORDER_CANCEL_REQUEST;
            case 'G':
              return MsgType.ORDER_CANCEL_REPLACE_REQUEST;
            case 'q':
              return MsgType.ORDER_MASS_CANCEL_REQUEST;
            case '0':
              return MsgType.HEARTBEAT;
            case 'j':
              return MsgType.BUSINESS_MESSAGE_REJECT;
            case '9':
              return MsgType.ORDER_CANCEL_REJECT;
            case 'W':
              return MsgType.MARKET_DATA_SNAPSHOT_FULL_REFRESH;
            case 'X':
              return MsgType.MARKET_DATA_INCREMENTAL_REFRESH;
            case '2':
              return MsgType.RESEND_REQUEST;
            case '4':
              return MsgType.SEQUENCE_RESET;
            default:
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_2, "error in decodeMsgType, data=", StringUtil.fixToString(data), e);
    }
    LOGGER.warn(LOG_FMT_2, "unable to decode in decodeMsgType, data=", data);
    return null;
  }

  // use FIX_OFFSET + startOffset
  public static final MsgType decodeMsgType(final byte[] data, final int startOffset) {
    try {
      final int length = data.length;
      // manually decode msgType from header
      for (int i = FIX_OFFSET + startOffset; i < length; i++) {
        if (data[i] == '=' && (data[i - 2] == '3') && (data[i - 1] == '5')) {
          switch (data[i + 1]) {
            case 'A':
              switch (data[i + 2]) {
                case 'P':
                  return MsgType.POSITION_REPORT; // AP
                default:
                  return MsgType.LOGON; // A
              }

            case '8':
              return MsgType.EXECUTION_REPORT;
            case 'D':
              return MsgType.ORDER_SINGLE;
            case 'B':
              switch (data[i + 2]) {
                case 'D':
                  return MsgType.NETWORK_STATUS_RESPONSE; // BD
                default:
                  return MsgType.NEWS; // B
              }
            case '5':
              return MsgType.LOGOUT;
            case 'F':
              return MsgType.ORDER_CANCEL_REQUEST;
            case 'G':
              return MsgType.ORDER_CANCEL_REPLACE_REQUEST;
            case 'q':
              return MsgType.ORDER_MASS_CANCEL_REQUEST;
            case '0':
              return MsgType.HEARTBEAT;
            case 'j':
              return MsgType.BUSINESS_MESSAGE_REJECT;
            case '9':
              return MsgType.ORDER_CANCEL_REJECT;
            case 'W':
              return MsgType.MARKET_DATA_SNAPSHOT_FULL_REFRESH;
            case 'X':
              return MsgType.MARKET_DATA_INCREMENTAL_REFRESH;
            case '2':
              return MsgType.RESEND_REQUEST;
            case '4':
              return MsgType.SEQUENCE_RESET;
            default:
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error(LOG_FMT_2, "error in decodeMsgType, data=", data, e);
    }
    LOGGER.warn(LOG_FMT_2, "unable to decode in decodeMsgType, data=", data);
    return null;
  }

}
