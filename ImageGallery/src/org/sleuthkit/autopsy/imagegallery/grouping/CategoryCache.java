package org.sleuthkit.autopsy.imagegallery.grouping;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
class CategoryCache {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(CategoryCache.class.getName());
    private final DrawableDB db;

    public CategoryCache(DrawableDB db) {
        this.db = db;
    }

    /**
     * For performance reasons, keep current category counts in memory
     */
    private final ConcurrentHashMap<Category, LongAdder> categoryCounts = new ConcurrentHashMap<>();

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
            return categoryCounts.computeIfAbsent(cat, this::getCategoryCountHelper).sum();
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
            categoryCounts.computeIfAbsent(cat, this::getCategoryCountHelper).increment();
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
            categoryCounts.computeIfAbsent(cat, this::getCategoryCountHelper).decrement();
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
}
