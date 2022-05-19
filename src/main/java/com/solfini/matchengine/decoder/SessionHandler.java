package com.solfini.matchengine.decoder;

import java.util.HashMap;
import java.util.Map;

import com.solfini.common.Constants;
import com.solfini.common.CustomLogger;
import com.solfini.common.Message;
import com.solfini.matchengine.message.session.HeartbeatMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.matchengine.message.session.LogoutMessage;
import com.solfini.matchengine.message.session.ResendRequestMessage;
import com.solfini.matchengine.message.session.SequenceResetMessage;
import com.solfini.matchengine.session.SessionInfo;
import com.solfini.sbe.encoder.BooleanType;
import com.solfini.sbe.encoder.HeartbeatDecoder;
import com.solfini.sbe.encoder.LogonDecoder;
import com.solfini.sbe.encoder.MessageHeaderDecoder;
import com.solfini.sbe.encoder.MsgType;
import com.solfini.sbe.encoder.ResendRequestDecoder;
import com.solfini.sbe.encoder.SequenceResetDecoder;
import com.solfini.user.User;
import com.solfini.user.UserCache;

/**
 *
 * @author Chris Mack
 *
 */
public class SessionHandler implements Constants {
  private static final CustomLogger LOGGER = CustomLogger.getLogger(SessionHandler.class);
  private static final long RESPONSE_CHANNEL_ID = 0;

  private final Map<String, SessionInfo> senderCompToSessionInfoMap = new HashMap<>();

  public SessionHandler() {
    // default constructor
  }

  public Message decodeLogon(final MessageHeaderDecoder headerDecoder, final LogonDecoder logonDecoder) {
    final SessionInfo inboundSessionInfo = buildSessionInfo(RESPONSE_CHANNEL_ID, headerDecoder.senderCompId());
    final SessionInfo outboundSessionInfo = buildSessionInfo(RESPONSE_CHANNEL_ID, headerDecoder.senderCompId());

    final int heartBeatInterval = logonDecoder.heartBtInt();
    final String username = logonDecoder.username();

    User user = UserCache.getByLogin(username);
    if (user != null) {
      final LogonMessage message = new LogonMessage();
      inboundSessionInfo.setMessageSequenceNumber(headerDecoder.msgSeqNum() + 1);
      inboundSessionInfo.setAuthenticated(true);

      outboundSessionInfo.setMessageSequenceNumber(1);
      message.setUser(user);
      message.setSessionInfo(outboundSessionInfo);
      message.setSenderCompId(outboundSessionInfo.getSenderCompId());
      message.setHeartBeatInterval(heartBeatInterval);


      if (BooleanType.TRUE == logonDecoder.resetSeqNumFlag()) {
        senderCompToSessionInfoMap.put(inboundSessionInfo.getSenderCompId(), inboundSessionInfo);
      } else {
        senderCompToSessionInfoMap.putIfAbsent(inboundSessionInfo.getSenderCompId(), inboundSessionInfo);
      }

      return message;

    } else {
      final LogoutMessage logoutMessage = new LogoutMessage();
      logoutMessage.setSessionInfo(outboundSessionInfo);
      logoutMessage.setSenderCompId(outboundSessionInfo.getSenderCompId());
      logoutMessage.setText("Invalid username or password.");
      logoutMessage.setLogonFailure(true);

      inboundSessionInfo.setAuthenticated(false);

      return logoutMessage;
    }
  }


  private final SessionInfo buildSessionInfo(final long responseChannelId, final String senderCompId) {
    final SessionInfo sessionInfo = new SessionInfo();
    sessionInfo.setConnectionId(responseChannelId);
    sessionInfo.setSenderCompId(senderCompId);
    return sessionInfo;
  }

  public final Message decodeHeartbeat(final MessageHeaderDecoder headerDecoder, final HeartbeatDecoder heartbeatDecoder) {
    final HeartbeatMessage heartbeatMessage = new HeartbeatMessage();
    heartbeatMessage.setSenderCompId(headerDecoder.senderCompId());

    return heartbeatMessage;
  }

  public final Message decodeResendRequest(final MessageHeaderDecoder headerDecoder, final ResendRequestDecoder resendRequestDecoder) {
    final ResendRequestMessage resendRequest = new ResendRequestMessage();
    resendRequest.setSenderCompId(headerDecoder.senderCompId());
    resendRequest.setBeginSeqNo(resendRequestDecoder.beginSequenceNo());
    resendRequest.setEndSeqNo(resendRequestDecoder.endSequenceNo());

    return resendRequest;
  }

  public final Message decodeSequenceReset(final MessageHeaderDecoder headerDecoder, final SequenceResetDecoder sequenceResetDecoder) {

    final SequenceResetMessage sequenceResetMessage = new SequenceResetMessage();
    sequenceResetMessage.setSenderCompId(headerDecoder.senderCompId());
    sequenceResetMessage.setGapFillFlag(BooleanType.TRUE == sequenceResetDecoder.gapFillFlag());
    sequenceResetMessage.setNewSeqNo(sequenceResetDecoder.newSequenceNo());

    final SessionInfo inboundSessionInfo = senderCompToSessionInfoMap.get(headerDecoder.senderCompId());
    if (inboundSessionInfo != null) {
      inboundSessionInfo.setMessageSequenceNumber(sequenceResetDecoder.newSequenceNo());
    }
    return sequenceResetMessage;
  }

  public final Message validateFIXSession(final MsgType msgType, final long msgSeqNumber, final String senderCompId,
      final long responseChannelId) {

    if (MsgType.LOGON.equals(msgType) || MsgType.SEQUENCE_RESET.equals(msgType)) {
      return null;
    }

    final SessionInfo inboundSessionInfo = senderCompToSessionInfoMap.get(senderCompId);
    if (inboundSessionInfo == null || !inboundSessionInfo.isAuthenticated()) {
      // TODO Reject
      final SessionInfo sessionInfo = new SessionInfo();
      sessionInfo.setConnectionId(responseChannelId);
      sessionInfo.setSenderCompId(senderCompId);
      sessionInfo.setAuthenticated(false);
      senderCompToSessionInfoMap.putIfAbsent(senderCompId, sessionInfo);

      final LogoutMessage logoutMessage = new LogoutMessage();
      logoutMessage.setSessionInfo(sessionInfo);
      logoutMessage.setSenderCompId(senderCompId);
      logoutMessage.setText("Not logged in.");
      logoutMessage.setLogonFailure(true);

      return logoutMessage;
    }


    long expectedSequenceNumber = inboundSessionInfo.getMessageSequenceNumber();

    if (msgSeqNumber != expectedSequenceNumber) {

      if (msgSeqNumber < expectedSequenceNumber) {
        // Serious error has occurred. Logout customer.
        LogoutMessage logoutMessage = new LogoutMessage();
        logoutMessage.setSenderCompId(senderCompId);
        logoutMessage.setText("Sequence number too low, expected: " + expectedSequenceNumber);
        LOGGER.info(LOG_FMT_6, "cSequence number too low, msgSeqNumber=", msgSeqNumber, ", expectedSequenceNumber=", expectedSequenceNumber,
            SENDERCOMPID_EQ, senderCompId);

        return logoutMessage;

      } else {
        LOGGER.info(LOG_FMT_8, "creating resendRequest, msgSeqNumber=", msgSeqNumber, ", expectedSequenceNumber=", expectedSequenceNumber,
            SENDERCOMPID_EQ, senderCompId, ", responseChannelId=", responseChannelId);

        inboundSessionInfo.setMessageSequenceNumber(msgSeqNumber + 1);

        // Resend Request to customer.
        ResendRequestMessage resendRequestMessage = new ResendRequestMessage();
        resendRequestMessage.setOriginated(true);
        resendRequestMessage.setSenderCompId(senderCompId);
        resendRequestMessage.setBeginSeqNo(expectedSequenceNumber);
        resendRequestMessage.setEndSeqNo(msgSeqNumber);
        return resendRequestMessage;
      }
    }

    inboundSessionInfo.incrementAndGetMessageSequenceNumber();

    return null;
  }

}
