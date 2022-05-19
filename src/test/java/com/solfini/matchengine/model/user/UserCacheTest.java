package com.solfini.matchengine.model.user;

import java.util.Properties;
import com.solfini.internal.admin.schema.UpdateType;
import com.solfini.matchengine.message.admin.UserAdminMessage;
import com.solfini.matchengine.model.ModelTest;
import com.solfini.user.User;
import com.solfini.user.UserCache;
import com.solfini.util.LogLevel;
import com.solfini.util.PoolSize;
import com.solfini.util.PropertyReader;
import org.slf4j.event.Level;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UserCacheTest extends ModelTest {

  @Before
  @Override
  public void before() {
    LogLevel.setLevel(Level.TRACE);

    try {
      Properties properties = new Properties();
      PoolSize.minimize(properties);
      properties.setProperty("INITIAL_USER_CACHE_SIZE", "1000");
      PropertyReader.initialize(null, properties);
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  private UserAdminMessage createUserAdminMessage(final int userId, final String username) {
    UserAdminMessage message = new UserAdminMessage();
    message.setUpdateType(UpdateType.PUT);
    message.setUserId(userId);
    message.setUsername(username);
    message.setPassword("password123");
    message.setFirmId(10);
    message.setFeeTier(1);
    message.setStatus(0);
    message.setAccountType(0);
    message.setLmm(false);
    message.setUseDiscountFeesCoin(false);
    return message;
  }

  private UserAdminMessage patch(UserAdminMessage message) {
    message.setUpdateType(UpdateType.PATCH);
    return message;
  }

  private void assertActive(final int userId) {
    User user = UserCache.get(userId);
    Assert.assertNotNull(user);
    Assert.assertTrue(user.isActive());
  }

  private void assertInactive(final int userId) {
    User user = UserCache.get(userId);
    Assert.assertNotNull(user);
    Assert.assertFalse(user.isActive());
  }

  // Add a new user, assert update
  @Test
  public void addNewUser() {
    assertInactive(100);

    UserCache.add(createUserAdminMessage(100, "user100"));
    assertActive(100);

    expectMessage("UserAdminMessage",
      "userType=0, userId=100, username=user100, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Add a duplicate user, assert update
  @Test
  public void addDuplicateUser() {
    assertInactive(101);

    UserCache.add(createUserAdminMessage(101, "user101"));
    assertActive(101);

    UserCache.add(createUserAdminMessage(101, "user101"));
    assertActive(101);

    expectMessage("UserAdminMessage",
      "userType=0, userId=101, username=user101, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    expectMessage("UserAdminMessage",
      "userType=0, userId=101, username=user101, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Add a duplicate user (different userId, same username), assert update
  @Test
  public void addDuplicateUserWithDifferentUserId() {
    assertInactive(102);
    assertInactive(103);

    UserCache.add(createUserAdminMessage(102, "user102"));
    assertActive(102);

    UserCache.add(createUserAdminMessage(103, "user102"));
    assertActive(103);

    expectMessage("UserAdminMessage",
      "userType=0, userId=102, username=user102, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    expectMessage("UserAdminMessage",
      "userType=0, userId=103, username=user102, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Add a duplicate user (same userId, different username), assert update
  @Test
  public void addDuplicateUserWithDifferentUsername() {
    assertInactive(104);
    assertInactive(105);

    UserCache.add(createUserAdminMessage(104, "user104"));
    assertActive(104);

    UserCache.add(createUserAdminMessage(104, "user105"));
    assertActive(104);

    expectMessage("UserAdminMessage",
      "userType=0, userId=104, username=user104, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    expectMessage("UserAdminMessage",
      "userType=0, userId=104, username=user105, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Update a non-existing user, assert update
  @Test
  public void updateNonExistingUser() {
    assertInactive(200);

    UserCache.add(patch(createUserAdminMessage(200, "user200")));
    assertActive(200);

    expectMessage("UserAdminMessage",
      "userType=0, userId=200, username=user200, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Update a user and change fee tier, assert update
  @Test
  public void updateUser() {
    assertInactive(201);

    UserCache.add(createUserAdminMessage(201, "user201"));
    assertActive(201);

    UserAdminMessage update = patch(createUserAdminMessage(201, "user201"));
    update.setFeeTier(2);
    UserCache.add(update);
    assertActive(201);

    expectMessage("UserAdminMessage",
      "userType=0, userId=201, username=user201, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    expectMessage("UserAdminMessage",
      "userType=0, userId=201, username=user201, firmId=10, feeTier=2, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Update a user and change user id, assert update
  @Test
  public void updateUserWithDifferentUserId() {
    assertInactive(202);
    assertInactive(203);

    UserCache.add(createUserAdminMessage(202, "user202"));
    assertActive(202);

    UserAdminMessage update = patch(createUserAdminMessage(203, "user202"));
    UserCache.add(update);
    assertActive(203);

    expectMessage("UserAdminMessage",
      "userType=0, userId=202, username=user202, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    expectMessage("UserAdminMessage",
      "userType=0, userId=203, username=user202, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Update a user and change username, assert update
  @Test
  public void updateUserWithDifferentUsername() {
    assertInactive(204);
    assertInactive(205);

    UserCache.add(createUserAdminMessage(204, "user204"));
    assertActive(204);

    UserAdminMessage update = patch(createUserAdminMessage(204, "user205"));
    UserCache.add(update);
    assertActive(204);

    expectMessage("UserAdminMessage",
      "userType=0, userId=204, username=user204, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    expectMessage("UserAdminMessage",
      "userType=0, userId=204, username=user205, firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Add a new user that overflow the cache, assert update
  @Test
  public void addNewUserOverflowCache() {
    final int userId = UserCache.getCacheSize() + 100;
    try {
      UserCache.get(userId);
      Assert.fail();
    } catch (ArrayIndexOutOfBoundsException e) {
    }

    UserCache.add(createUserAdminMessage(userId, "user" + userId));
    assertActive(userId);

    expectMessage("UserAdminMessage",
      "userType=0, userId=" + userId + ", username=user" + userId + ", firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }

  // Update a user that overflow the cache, assert update
  @Test
  public void updateUserOverflowCache() {
    final int userId = UserCache.getCacheSize() + 100;
    try {
      UserCache.get(userId);
      Assert.fail();
    } catch (ArrayIndexOutOfBoundsException e) {
    }

    UserCache.add(patch(createUserAdminMessage(userId, "user" + userId)));
    assertActive(userId);

    expectMessage("UserAdminMessage",
      "userType=0, userId=" + userId + ", username=user" + userId + ", firmId=10, feeTier=1, status=0, accountType=0, lmm=false, useDiscountFeesCoin=false, requestStatus=SUCCESS");
    assertMessages();
  }
}