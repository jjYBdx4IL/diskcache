package com.github.jjYBdx4IL.diskcache;

import static org.junit.Assert.*;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjYBdx4IL
 */
public class DiskCacheTest {

    private static final Logger LOG = LoggerFactory.getLogger(DiskCacheTest.class);
    private static final DiskCache cache = new DiskCache(null, null, null, true);

    @Test
    public void testSetExpirySecs() {
        cache.setExpirySecs(86400);
    }

    @Test
    public void testPutGet() throws Exception {
        assertNull(cache.get("123"));
        cache.put("123", "1234".getBytes());
        byte[] a = cache.get("123");
        assertNotNull(a);
        assertArrayEquals("1234".getBytes(), a);
    }

    @Test
    public void testWebGet() throws Exception {
        byte[] a = cache.get("https://www.google.com/");
        byte[] b = cache.retrieve("https://www.google.com/");
        byte[] c = cache.get("https://www.google.com/");
        assertNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertArrayEquals(b, c);
    }
}
