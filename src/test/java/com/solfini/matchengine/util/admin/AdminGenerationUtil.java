package com.solfini.matchengine.util.admin;

import java.nio.ByteBuffer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import com.solfini.internal.admin.schema.BalanceAdminMessageEncoder;
import com.solfini.internal.admin.schema.FIXUserAdminMessageEncoder;
import com.solfini.internal.admin.schema.MessageHeaderEncoder;
import com.solfini.internal.admin.schema.RequestStatus;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder.BalanceGroupEncoder;


/**
 *
 * @author Chris Mack
 *
 */
public class AdminGenerationUtil {


  public static DirectBuffer createUserAdminMessage(boolean includeBalance) {


    ByteBuffer adminMessageBuffer = ByteBuffer.allocateDirect(8192);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    UserAdminMessageEncoder userEncoder = new UserAdminMessageEncoder();
    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    userEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, 0, headerEncoder);

    userEncoder.updateType(UpdateType.PUT);
    userEncoder.userId(1);
    userEncoder.username("chris");
    userEncoder.password("password");
    userEncoder.firmId(2);

    if (includeBalance) {

      BalanceGroupEncoder balanceGroupEncoder = userEncoder.balanceGroupCount(2);

      balanceGroupEncoder.next().assetId(1).balance().value(10199).scale(2);
      balanceGroupEncoder.next().assetId(2).balance().value(22398).scale(2);

    }

    userEncoder.buffer().byteBuffer().limit(userEncoder.limit() + BalanceGroupEncoder.sbeHeaderSize());

    return userEncoder.buffer();

  }

  public static DirectBuffer createBalanceAdminMessage() {

    ByteBuffer adminMessageBuffer = ByteBuffer.allocate(8192);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    BalanceAdminMessageEncoder balanceEncoder = new BalanceAdminMessageEncoder();
    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    balanceEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, 0, headerEncoder);

    balanceEncoder.updateType(UpdateType.PUT);
    balanceEncoder.userId(1);
    balanceEncoder.firmId(2);
    balanceEncoder.feeTier(1);

    balanceEncoder.balanceGroupCount(0);

    System.out.println(balanceEncoder.limit());
    balanceEncoder.buffer().byteBuffer().limit((balanceEncoder.limit()));

    return balanceEncoder.buffer();


  }

  public static DirectBuffer createFIXUserMessage() {

    ByteBuffer adminMessageBuffer = ByteBuffer.allocate(8192);
    UnsafeBuffer adminMessageUnsafeBuffer = new UnsafeBuffer(adminMessageBuffer);

    FIXUserAdminMessageEncoder fixUserEncoder = new FIXUserAdminMessageEncoder();
    MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    fixUserEncoder.wrapAndApplyHeader(adminMessageUnsafeBuffer, 0, headerEncoder);

    fixUserEncoder.updateType(UpdateType.PUT);
    fixUserEncoder.username("chris");
    fixUserEncoder.password("password");
    fixUserEncoder.senderCompId("COMPID");
    fixUserEncoder.requestStatus(RequestStatus.FAIL);

    fixUserEncoder.buffer().byteBuffer().limit(fixUserEncoder.limit());

    return fixUserEncoder.buffer();

  }

}
