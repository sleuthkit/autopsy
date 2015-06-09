package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.eventbus.EventBus;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class CategoryManager {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(CategoryManager.class.getName());
    private DrawableDB db;
    private final EventBus categoryEventBus = new EventBus("Category Event Bus");

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

    public void fireChange(Collection<Long> ids) {

        categoryEventBus.post(new CategoryChangeEvent(ids));

    }

    public void registerListener(Object aThis) {
        categoryEventBus.register(aThis);
    }

    public void unregisterListener(Object aThis) {
        categoryEventBus.unregister(aThis);
    }

}
