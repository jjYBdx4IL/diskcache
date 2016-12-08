package com.github.jjYBdx4IL.diskcache;

import com.github.jjYBdx4IL.diskcache.jpa.DiskCacheEntry;
import com.github.jjYBdx4IL.diskcache.jpa.DiskCacheQueryFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjYBdx4IL
 */
public class DiskCache implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DiskCache.class);
    public static final String DEFAULT_DB_NAME = "diskcachedb";
    public static final int MAX_KEY_LENGTH = 1024;
    public static final long DEFAULT_EXPIRY_SECS = 86400;
    public static final String INVALID_DBNAME_CHARS = File.separatorChar + "/\\;:";
    // store every data file larger than this in its separate file on disk
    public static final long MAX_BLOB_SIZE = 32 * 1024;

    private static File getDefaultParentDir() {
        File configDir = new File(System.getProperty("user.home"), ".config");
        // store databases under maven target dir if we run as part of a maven test
        if (System.getProperty("basedir") != null) {
            configDir = new File(System.getProperty("basedir"), "target");
        }
        File f = new File(configDir, DiskCache.class.getName());
        return f;
    }

    protected long expiryMillis = DEFAULT_EXPIRY_SECS * 1000L;
    private final String dbName;
    private final File parentDir;
    private final File fileStorageDir;

    protected final Map<String, String> props = new HashMap<>();
    protected EntityManagerFactory emf = null;
    protected EntityManager em = null;
    protected final DiskCacheQueryFactory diskCacheQueryFactory;

    /**
     *
     *
     * @param parentDir may be null. in that case databases get crated either below ~/.config/...DiskCache or
     * &lt;pwd>/target/...DiskCache if run as part of a maven test.
     * @param dbName the database name identifying the database on disk, ie. the directory below parentDir where Derby
     * stores the database's data. may be null, in which case the default "diskcachedb" is used.
     */
    public DiskCache(File parentDir, String dbName) {
        this(parentDir, dbName, false);
    }

    public DiskCache(String dbName) {
        this(null, dbName, false);
    }

    public DiskCache(File parentDir, String dbName, boolean reinit) {
        this.dbName = dbName != null ? dbName : DEFAULT_DB_NAME;

        if (StringUtils.containsAny(dbName, INVALID_DBNAME_CHARS)) {
            throw new IllegalArgumentException("the db name must not contain " + INVALID_DBNAME_CHARS);
        }

        this.parentDir = parentDir != null ? parentDir : getDefaultParentDir();

        final File dbDir = new File(this.parentDir, this.dbName);
        this.fileStorageDir = new File(dbDir, "files");

        if (dbDir.exists() && reinit) {
            LOG.info("deleting " + dbDir.getAbsolutePath());
            try {
                FileUtils.deleteDirectory(dbDir);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        if (!this.fileStorageDir.exists()) {
            this.fileStorageDir.mkdirs();
        }

        final String dbLocation = new File(dbDir, "db").getAbsolutePath().replaceAll(":", "\\:");

        props.put("hibernate.hbm2ddl.auto", "create");
        props.put("hibernate.show_sql", "true");
        props.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        props.put("javax.persistence.jdbc.url", "jdbc:h2:" + dbLocation);

        emf = Persistence.createEntityManagerFactory("DiskCachePU", props);
        em = emf.createEntityManager();
        diskCacheQueryFactory = new DiskCacheQueryFactory(em);

        LOG.info("started.");
    }

    public DiskCache setExpirySecs(long secs) {
        this.expiryMillis = secs * 1000L;
        return this;
    }

    public void put(URL url, byte[] data) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException();
        }
        put(url.toExternalForm(), data);
    }

    public void put(String key, byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException();
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            put(key, input);
        }
    }

    public void put(String key, InputStream input) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException();
        }
        if (input == null) {
            throw new IllegalArgumentException();
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("key too long: " + key);
        }

        byte[] buf = new byte[(int) MAX_BLOB_SIZE + 1];
        long size = IOUtils.read(input, buf);

        DiskCacheEntry dce;
        TypedQuery<DiskCacheEntry> results = diskCacheQueryFactory.getByUrlQuery(key);
        if (results.getResultList().isEmpty()) {
            dce = new DiskCacheEntry();
            dce.url = key;
        } else {
            dce = results.getResultList().get(0);
        }

        dce.createdAt = 0L;
        dce.data = null;
        dce.size = -1L;

        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            // write data to a separate file on disk if it is larger than this
            if (size == MAX_BLOB_SIZE + 1) {
                em.persist(dce);
                tx.commit();

                File dataFile = new File(this.fileStorageDir, Long.toString(dce.id));
                try (FileOutputStream fos = new FileOutputStream(dataFile, false)) {
                    IOUtils.write(buf, fos);
                    size += IOUtils.copyLarge(input, fos);
                    fos.getFD().sync();
                }
                LOG.debug("wrote " + size + " bytes to " + dataFile.getAbsolutePath());

                tx.begin();
            } else {
                dce.data = Arrays.copyOf(buf, (int) size);
            }

            dce.createdAt = System.currentTimeMillis();
            dce.size = size;
            em.persist(dce);
            tx.commit();

            LOG.debug("stored " + key + " (" + size + " bytes), " + dce.toString());
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    /**
     *
     * @param key
     * @return returns null if the key was not found or the data has expired.
     * @throws IOException
     */
    public byte[] get(String key) throws IOException {
        return get(key, this.expiryMillis);
    }

    public byte[] get(String key, long _expiryMillis) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream is = getStream(key, _expiryMillis)) {
            if (is == null) {
                return null;
            }
            IOUtils.copyLarge(is, baos);
            return baos.toByteArray();
        }
    }

    /**
     *
     * @param key
     * @param _expiryMillis -1 or less to ignore expiration
     * @return
     * @throws IOException
     */
    public InputStream getStream(String key, long _expiryMillis) throws IOException {

        if (key == null || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException();
        }

        TypedQuery<DiskCacheEntry> results = diskCacheQueryFactory.getByUrlQuery(key);
        if (results.getResultList().isEmpty()) {
            return null;
        }

        DiskCacheEntry dce = results.getResultList().get(0);

        final long notBefore = System.currentTimeMillis() - _expiryMillis;
        if (_expiryMillis >= 0L && dce.createdAt < notBefore) {
            return null;
        }

        if (dce.data == null) {
            return new FileInputStream(new File(this.fileStorageDir, Long.toString(dce.id)));
        } else {
            return new ByteArrayInputStream(dce.data);
        }
    }

    public InputStream getStream(String key) throws IOException {
        return getStream(key, this.expiryMillis);
    }

    @Override
    public void close() throws IOException {
        if (em != null) {
            em.close();
            em = null;
        }
        if (emf != null) {
            emf.close();
            emf = null;
        }
        LOG.debug("closed");
    }
}
