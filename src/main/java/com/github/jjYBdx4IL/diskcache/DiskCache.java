package com.github.jjYBdx4IL.diskcache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jjYBdx4IL
 */
public class DiskCache {

    private static final Logger LOG = LoggerFactory.getLogger(DiskCache.class);
    public static final String DEFAULT_TABLE_NAME = "cachedata";
    public static final String DEFAULT_DB_NAME = "diskcachedb";
    public static final int MAX_VARCHAR_LEN = 32672;
    public static final long DEFAULT_EXPIRY_SECS = 86400;
    public static final String DERBY_LOG_PROPNAME = "derby.stream.error.file";
    public static final long DATA_WARN_LEN = 100 * 1024 * 1024;

    private static File getDefaultParentDir() {
        File configDir = new File(System.getProperty("user.home"), ".config");
        // store databases under maven target dir if we run as part of a maven test
        if (System.getProperty("basedir") != null) {
            configDir = new File(System.getProperty("basedir"), "target");
        }
        File f = new File(configDir, DiskCache.class.getName());
        return f;
    }

    private final Connection conn;
    private long expiryMillis = DEFAULT_EXPIRY_SECS * 1000L;
    private final HttpClient httpclient;
    private final String tableName;
    private final String dbName;
    private final File parentDir;

    /**
     *
     *
     * @param parentDir may be null. in that case databases get crated either below ~/.config/...DiskCache or
     * &lt;pwd>/target/...DiskCache if run as part of a maven test.
     * @param dbName the database name identifying the database on disk, ie. the directory below parentDir
     * where Derby stores the database's data. may be null, in which case the default "diskcachedb" is used.
     * @param tableName may be null. In that case the default "cachedata" is used.
     */
    public DiskCache(File parentDir, String dbName, String tableName) {
        this(parentDir, dbName, tableName, false);
    }

    public DiskCache(String dbName) {
        this(null, dbName, null, false);
    }

    public DiskCache(File parentDir, String dbName, String tableName, boolean reinit) {
        if (dbName != null) {
            if (dbName.contains(File.separator)) {
                throw new IllegalArgumentException("the db name must not contain " + File.separator);
            }
            if (dbName.contains("/")) {
                throw new IllegalArgumentException("the db name must not contain /");
            }
            if (dbName.contains("\\")) {
                throw new IllegalArgumentException("the db name must not contain \\");
            }
            if (dbName.contains(":")) {
                throw new IllegalArgumentException("the db name must not contain :");
            }
            if (dbName.contains(";")) {
                throw new IllegalArgumentException("the db name must not contain ;");
            }
        }

        httpclient = HttpClients.createDefault();
        this.parentDir = parentDir != null ? parentDir : getDefaultParentDir();
        this.dbName = dbName != null ? dbName : DEFAULT_DB_NAME;
        this.tableName = tableName != null ? tableName : DEFAULT_TABLE_NAME;

        final File dbDir = new File(this.parentDir, this.dbName);

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

        try {
            final String derbyLog = new File(dbDir, "derby.log").getAbsolutePath();
            if (!System.getProperty(DERBY_LOG_PROPNAME, "").equals(derbyLog)) {
                LOG.info("setting derby log file to " + derbyLog);
                System.setProperty("derby.stream.error.file", derbyLog);
            }

            // we put the derby db into the "derby/" sub directory so we can put another storage into the same structure
            // below dbDir for large files later on.
            final String dbLocation = new File(dbDir, "derby").getAbsolutePath().replaceAll(":", "\\:");
            conn = DriverManager.getConnection("jdbc:derby:" + dbLocation + ";create=true");
            final boolean tableExists;
            try (ResultSet res = conn.getMetaData().getTables(null, "APP", this.tableName.toUpperCase(), null)) {
                tableExists = res.next();
            }
            if (!tableExists) {
                execStmt("CREATE TABLE " + this.tableName + " (ID INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), cachekey VARCHAR(" + MAX_VARCHAR_LEN + ") UNIQUE NOT NULL, lmod BIGINT NOT NULL, cachedata BLOB NOT NULL)");
                execStmt("CREATE INDEX idx0 ON " + this.tableName + " (cachekey)");
                LOG.debug("derby db initialized.");
            }
            LOG.info("started.");
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public DiskCache setExpirySecs(long secs) {
        this.expiryMillis = secs * 1000L;
        return this;
    }

    private void execStmt(String s) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(s);
        }
    }

    public void put(URL url, byte[] data) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException();
        }
        put(url.toExternalForm(), data);
    }

    public void put(String key, byte[] data) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException();
        }
        if (data == null) {
            throw new IllegalArgumentException();
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (key.length() > MAX_VARCHAR_LEN) {
            throw new IllegalArgumentException("key too long: " + key);
        }
        if (data.length > DATA_WARN_LEN) {
            LOG.warn("The data for key " + key + " is " + data.length + " bytes long. It is not recommended to store large data chunks using " + DiskCache.class.getName());
        }
        try {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tableName + " WHERE cachekey = ?")) {
                ps.setString(1, key);
                ps.execute();
            }
            Blob blob = conn.createBlob();
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + tableName + "(cachekey,lmod,cachedata) VALUES(?,?,?)")) {
                ps.setString(1, key);
                ps.setLong(2, System.currentTimeMillis());
                try (OutputStream os = blob.setBinaryStream(1)) {
                    IOUtils.write(data, os);
                }
                ps.setBlob(3, blob);
                ps.execute();
            } finally {
                blob.free();
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
        LOG.debug("stored " + key);
    }

    public byte[] get(URL url) throws IOException {
        return get(url.toExternalForm());
    }

    /**
     *
     * @param key
     * @return returns null if the key was not found or the data has expired.
     * @throws IOException
     */
    public byte[] get(String key) throws IOException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT cachedata FROM " + tableName + " WHERE cachekey = ? AND lmod > ?")) {
            ps.setString(1, key);
            ps.setLong(2, System.currentTimeMillis() - expiryMillis);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Blob value = rs.getBlob(1);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream is = value.getBinaryStream()) {
                        IOUtils.copy(is, baos);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
        return null;
    }

    public byte[] retrieve(URL url) throws IOException {
        byte[] data = get(url);
        if (data != null) {
            LOG.debug("returning cached data for " + url.toExternalForm());
            return data;
        }

        LOG.debug("retrieving " + url.toExternalForm());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        HttpGet httpGet = new HttpGet(url.toExternalForm());
        HttpResponse response = httpclient.execute(httpGet);
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IOException("url returned status code " + response.getStatusLine().getStatusCode() + ": " + url.toExternalForm());
        }
        try (InputStream is = response.getEntity().getContent()) {
            IOUtils.copy(is, baos);
        }

        data = baos.toByteArray();
        put(url, data);
        return data;
    }

    public byte[] retrieve(String url) throws IOException {
        try {
            return retrieve(new URL(url));
        } catch (MalformedURLException ex) {
            throw new IOException(ex);
        }
    }
}
