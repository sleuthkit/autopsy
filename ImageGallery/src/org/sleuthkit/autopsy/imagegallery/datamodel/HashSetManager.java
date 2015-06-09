package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.Set;

/**
 *
 */
public class HashSetManager {

    private DrawableDB db = null;

    public void setDb(DrawableDB db) {
        this.db = db;
        hashSetCache.invalidateAll();
    }
    private final LoadingCache<Long, Set<String>> hashSetCache = CacheBuilder.newBuilder().build(CacheLoader.from(this::getHashSetsForFileHelper));

    private Set<String> getHashSetsForFileHelper(Long id) {
        return db.getHashSetsForFile(id);
    }

    public boolean isInHashSet(Long id) {
        return hashSetCache.getUnchecked(id).isEmpty() == false;
    }

    public Set<String> getHashSetsForFile(Long id) {
        return hashSetCache.getUnchecked(id);
    }

    public void invalidateHashSetsForFile(Long id) {
        hashSetCache.invalidate(id);
    }
}
