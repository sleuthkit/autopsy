/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Collection;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.TagUtils;

/**
 * Provides a cached view of the number of files per category, and fires
 * {@link CategoryChangeEvent}s when files are categorized.
 *
 * To receive CategoryChangeEvents, a listener must register itself, and
 * implement a method annotated with {@link Subscribe} that accepts one argument
 * of type CategoryChangeEvent
 *
 * TODO: currently these two functions (cached counts and events) are separate
 * although they are related. Can they be integrated more?
 *
 */
public class CategoryManager {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(CategoryManager.class.getName());

    /**
     * the DrawableDB that backs the category counts cache. The counts are
     * initialized from this, and the counting of CAT-0 is always delegated to
     * this db.
     */
    private DrawableDB db;

    /**
     * Used to distribute {@link CategoryChangeEvent}s
     */
    private final EventBus categoryEventBus = new EventBus("Category Event Bus");

    /**
     * For performance reasons, keep current category counts in memory. All of
     * the count related methods go through this cache, which loads initial
     * values from the database if needed.
     */
    private final LoadingCache<Category, LongAdder> categoryCounts
            = CacheBuilder.newBuilder().build(CacheLoader.from(this::getCategoryCountHelper));

    /**
     * assign a new db. the counts cache is invalidated and all subsequent db
     * lookups go to the new db.
     *
     * Also clears the Category TagNames (should this happen here?)
     *
     * @param db
     */
    public void setDb(DrawableDB db) {
        this.db = db;
        categoryCounts.invalidateAll();
        Category.clearTagNames();
        TagUtils.clearFollowUpTagName();
    }

    /**
     * get the number of file with the given {@link Category}
     *
     * @param cat get the number of files with Category = cat
     *
     * @return the long the number of files with the given Category
     */
    public long getCategoryCount(Category cat) {
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
     * increment the cached value for the number of files with the given
     * {@link Category}
     *
     * @param cat the Category to increment
     */
    public void incrementCategoryCount(Category cat) {
        if (cat != Category.ZERO) {
            categoryCounts.getUnchecked(cat).increment();
        }
    }

    /**
     * decrement the cached value for the number of files with the given
     * {@link Category}
     *
     * @param cat the Category to decrement
     */
    public void decrementCategoryCount(Category cat) {
        if (cat != Category.ZERO) {
            categoryCounts.getUnchecked(cat).decrement();
        }
    }

    /**
     * helper method that looks up the number of files with the given Category
     * from the db and wraps it in a long adder to use in the cache
     *
     *
     * @param cat the Category to count
     *
     * @return a LongAdder whose value is set to the number of file with the
     *         given Category
     */
    private LongAdder getCategoryCountHelper(Category cat) {
        LongAdder longAdder = new LongAdder();
        longAdder.decrement();
        try {
            longAdder.add(db.getCategoryCount(cat));
            longAdder.increment();
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Case closed while getting files");
        }
        return longAdder;
    }

    /**
     * fire a CategoryChangeEvent with the given fileIDs
     *
     * @param fileIDs
     */
    public void fireChange(Collection<Long> fileIDs) {
        categoryEventBus.post(new CategoryChangeEvent(fileIDs));
    }

    /**
     * register an object to receive CategoryChangeEvents
     *
     * @param listner
     */
    public void registerListener(Object listner) {
        categoryEventBus.register(listner);
    }

    /**
     * unregister an object from receiving CategoryChangeEvents
     *
     * @param listener
     */
    public void unregisterListener(Object listener) {
        categoryEventBus.unregister(listener);
    }

}
