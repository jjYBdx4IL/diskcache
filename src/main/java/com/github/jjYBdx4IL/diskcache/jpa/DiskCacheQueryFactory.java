package com.github.jjYBdx4IL.diskcache.jpa;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/**
 *
 * @author Github jjYBdx4IL Projects
 */
public class DiskCacheQueryFactory {

    private final EntityManager em;

    public DiskCacheQueryFactory(EntityManager em) {
        this.em = em;
    }

    public TypedQuery<DiskCacheEntry> getByUrlQuery(String url) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<DiskCacheEntry> criteriaQuery = cb.createQuery(DiskCacheEntry.class);
        final Root<DiskCacheEntry> userRoot = criteriaQuery.from(DiskCacheEntry.class);

        Predicate p1 = cb.equal(userRoot.get(DiskCacheEntry_.url), url);
        Predicate p2 = cb.ge(userRoot.get(DiskCacheEntry_.size), 0);
        return em.createQuery(criteriaQuery.where(cb.and(p1, p2)));
    }

}
