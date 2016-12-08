package com.github.jjYBdx4IL.diskcache.jpa;

import com.github.jjYBdx4IL.diskcache.DiskCache;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Version;

/**
 *
 * @author Github jjYBdx4IL Projects
 */
@Entity
public class DiskCacheEntry {

    @Id
    @GeneratedValue
    public long id;

    @Basic
    @Column(length = DiskCache.MAX_KEY_LENGTH, nullable = false, unique = true)
    public String url;

    // null iff data is stored in separate file, identified by id
    @Lob
    @Column(nullable = true)
    public byte[] data;

    @Basic
    @Column(nullable = false)
    public long size;

    @Basic
    @Column(nullable = false)
    public long createdAt;

    @Version
    public long version;

    @Override
    public String toString() {
        return "DiskCacheEntry{" + "id=" + id + ", url=" + url + ", data=" + data + ", size=" + size + ", createdAt=" + createdAt + ", version=" + version + '}';
    }

}
