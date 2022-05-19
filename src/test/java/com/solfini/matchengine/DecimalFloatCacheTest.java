package com.solfini.matchengine;

import com.solfini.matchengine.publisher.DecimalFloatCache;
import org.junit.Assert;
import org.junit.Test;
import uk.co.real_logic.artio.fields.DecimalFloat;

public class DecimalFloatCacheTest {
    @Test
    public void testDecimalFloatCache() {
        DecimalFloatCache cache = new DecimalFloatCache();

        for (int i = 0; i < 10_000; ++i) {
            DecimalFloat f = cache.nextDecimalFloat(i, 2);
            Assert.assertEquals(new DecimalFloat(i, 2), f);
        }

        cache.reset();

        for (int i = 0; i < 10_000; ++i) {
            DecimalFloat f = cache.nextDecimalFloat(10_000 - i , 2);
            Assert.assertEquals(new DecimalFloat(10_000 - i, 2), f);
        }
    }
}
