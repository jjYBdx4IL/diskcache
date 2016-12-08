package com.github.jjYBdx4IL.diskcache;

import com.github.jjYBdx4IL.test.AdHocHttpServer;
import java.io.IOException;
import java.net.URL;
import org.junit.AfterClass;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.BeforeClass;
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

    private static AdHocHttpServer server;
    private static URL url;
    
    @BeforeClass
    public static void beforeClass() throws Exception {
        server = new AdHocHttpServer();
        url = server.addStaticContent("/", new AdHocHttpServer.StaticResponse("example content"));
    }
    
    @AfterClass
    public static void afterClass() throws IOException, Exception {
        cache.close();
        server.close();
    }
    
    @Test
    public void testWebGet() throws Exception {
        byte[] a = cache.get(url.toExternalForm());
        byte[] b = cache.retrieve(url.toExternalForm());
        byte[] c = cache.get(url.toExternalForm());
        assertNull(a);
        assertNotNull(b);
        assertNotNull(c);
        assertArrayEquals(b, c);
        assertArrayEquals("example content".getBytes(), c);
    }

}
