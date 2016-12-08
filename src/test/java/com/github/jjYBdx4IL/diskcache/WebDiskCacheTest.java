package com.github.jjYBdx4IL.diskcache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Github jjYBdx4IL Projects
 */
public class WebDiskCacheTest {

    private static final Logger LOG = LoggerFactory.getLogger(WebDiskCacheTest.class);
    private static final WebDiskCache cache = new WebDiskCache(null, null, true);

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
