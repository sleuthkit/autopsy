package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class CategoryManager {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(CategoryManager.class.getName());
    private DrawableDB db;

    public void setDb(DrawableDB db) {
        this.db = db;
        categoryCounts.invalidateAll();
        Category.clearTagNames();
    }

    /**
     * For performance reasons, keep current category counts in memory
     */
    private final LoadingCache<Category, LongAdder> categoryCounts = CacheBuilder.newBuilder().build(CacheLoader.from(this::getCategoryCountHelper));

    /**
     *
     * @param cat        the value of cat
     * @param drawableDB the value of drawableDB
     *
     * @return the long
     *
     * @throws TskCoreException
     */
    public long getCategoryCount(Category cat) throws TskCoreException {
        if (cat == Category.ZERO) {
            // Keeping track of the uncategorized files is a bit tricky while ingest
            // is going on, so always use the list of file IDs we already have along with the
            // other category counts instead of trying to track it separately.
            long allOtherCatCount = getCategoryCount(Category.ONE) + getCategoryCount(Category.TWO) + getCategoryCount(Category.THREE) + getCategoryCount(Category.FOUR) + getCategoryCount(Category.FIVE);
            return db.getNumberOfImageFilesInList() - allOtherCatCount;
        } else {
            return categoryCounts.getUnchecked(cat).sum();
        }
    }

    /**
     *
     * @param cat        the value of cat
     * @param drawableDB the value of drawableDB
     *
     * @throws TskCoreException
     */
    public void incrementCategoryCount(Category cat) {
        if (cat != Category.ZERO) {
            categoryCounts.getUnchecked(cat).increment();
        }
    }

    /**
     *
     * @param cat        the value of cat
     * @param drawableDB the value of drawableDB
     *
     * @throws TskCoreException
     */
    public void decrementCategoryCount(Category cat) {
        if (cat != Category.ZERO) {
            categoryCounts.getUnchecked(cat).decrement();
        }
    }

    /**
     *
     * @param t          the value of t
     * @param drawableDB the value of drawableDB
     */
    private LongAdder getCategoryCountHelper(Category t) {
        LongAdder longAdder = new LongAdder();
        longAdder.decrement();
        try {
            longAdder.add(db.getCategoryCount(t));
            longAdder.increment();
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Case closed while getting files");
        }
        return longAdder;
    }
    @GuardedBy("listeners")
    private final Set<CategoryListener> listeners = new HashSet<>();

    public void fireChange(Collection<Long> ids) {
        Set<CategoryListener> listenersCopy = new HashSet<>();
        synchronized (listeners) {
            listenersCopy.addAll(listeners);
        }
        for (CategoryListener list : listenersCopy) {
            list.handleCategoryChanged(ids);
        }

    }

    public void registerListener(CategoryListener aThis) {
        synchronized (listeners) {
            listeners.add(aThis);
        }
    }

    public void unregisterListener(CategoryListener aThis) {
        synchronized (listeners) {
            listeners.remove(aThis);
        }
    }

    public static interface CategoryListener {

        public void handleCategoryChanged(Collection<Long> ids);

    }
}
