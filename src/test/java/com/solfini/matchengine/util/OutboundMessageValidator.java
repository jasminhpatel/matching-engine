package com.solfini.matchengine.util;

import static org.junit.Assert.assertEquals;
import com.solfini.matchengine.message.outbound.ExecutionReportMessage;
import com.solfini.matchengine.message.session.LogonMessage;
import com.solfini.sbe.encoder.ExecutionReportDecoder;
import com.solfini.sbe.encoder.LogonDecoder;

/**
 *
 * @author Chris Mack
 *
 */
public class OutboundMessageValidator {


  public static void validateLogon(LogonMessage logonMessage, LogonDecoder logonDecoder) {

    assertEquals(logonMessage.getHeartBeatInterval(), logonDecoder.heartBtInt());
    // assertEquals(logonMessage.getMessageType().name(), logonDecoder.header().msgTypeAsEnum().name());


  }

  public static void validateExecutionReport(ExecutionReportMessage executionReportMessage, ExecutionReportDecoder executionReportDecoder) {

    // Sender comp becomes the target comp
    // assertEquals(executionReportMessage.getSenderCompId(),
    // new String(executionReportDecoder.header().targetCompID()));

    assertEquals(new String(executionReportMessage.getClOrdId()), new String(executionReportDecoder.clOrdID()));
    assertEquals(executionReportMessage.getOrderId(), executionReportDecoder.orderId());
    assertEquals(executionReportMessage.getExecId(), executionReportDecoder.execId());
    assertEquals(executionReportMessage.getExecType(), executionReportDecoder.execType());
    assertEquals(executionReportMessage.getOrdStatus(), executionReportDecoder.ordStatus());
    assertEquals(executionReportMessage.getSymbol(), new String(executionReportDecoder.symbol()));
    assertEquals(executionReportMessage.getSide(), executionReportDecoder.side());
    assertEquals(executionReportMessage.getOrdType(), executionReportDecoder.ordType());
    assertEquals(executionReportMessage.getAccount(), executionReportDecoder.userId());

    assertEquals(executionReportMessage.getLeavesQty(), executionReportDecoder.leavesQty());
    assertEquals(executionReportMessage.getCumQty(), executionReportDecoder.cumQty());
    assertEquals(executionReportMessage.getAvgPx(), executionReportDecoder.avgPx());
    assertEquals(executionReportMessage.getPrice(), executionReportDecoder.price());

    assertEquals(executionReportMessage.getOrderQty(), executionReportDecoder.orderQty());
    assertEquals(executionReportMessage.getLastPx(), executionReportDecoder.lastPx());


  }

}
