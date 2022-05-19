package com.solfini.matchengine.publisher;

import com.solfini.common.Context;
import com.solfini.internal.admin.schema.UserAdminMessageEncoder;

/**
 *
 * @author Chris Mack
 *
 */
public class UserAdminEncoderCache {
  private static UserAdminEncoderCache[] cache = UserAdminEncoderCache.build();
  private final UserAdminMessageEncoder encoder = new UserAdminMessageEncoder();

  public UserAdminEncoderCache() {
    super();
  }

  private static UserAdminEncoderCache[] build() {
    cache = new UserAdminEncoderCache[Math.max(Context.getEncoderThreads(), 1)];
    for (int i = 0; i < cache.length; ++i) {
      cache[i] = new UserAdminEncoderCache();
    }
    return cache;
  }

  public static final UserAdminEncoderCache get() {
    if (Context.getEncoderThreads() > 0) {
      final String name = Thread.currentThread().getName();
      return cache[((int) name.charAt(name.length() - 1)) - 48];
    } else {
      return cache[0];
    }
  }

  public final UserAdminMessageEncoder getEncoder() {
    return encoder;
  }
}
