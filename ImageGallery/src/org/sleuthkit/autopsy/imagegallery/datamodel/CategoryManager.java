/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides a cached view of the number of files per category, and fires
 * {@link CategoryChangeEvent}s when files are categorized.
 *
 * To receive CategoryChangeEvents, a listener must register itself, and
 * implement a public method annotated with {@link Subscribe} that accepts one
 * argument of type CategoryChangeEvent
 *
 * TODO: currently these two functions (cached counts and events) are separate
 * although they are related. Can they be integrated more?
 *
 */
public class CategoryManager {

    private static final Logger LOGGER = Logger.getLogger(CategoryManager.class.getName());

    private final ImageGalleryController controller;

    /**
     * the DrawableDB that backs the category counts cache. The counts are
     * initialized from this, and the counting of CAT-0 is always delegated to
     * this db.
     */
    private DrawableDB db;

    /**
     * Used to distribute {@link CategoryChangeEvent}s
     */
    private final EventBus categoryEventBus = new AsyncEventBus(Executors.newSingleThreadExecutor(
            new BasicThreadFactory.Builder().namingPattern("Category Event Bus").uncaughtExceptionHandler((Thread t, Throwable e) -> { //NON-NLS
                LOGGER.log(Level.SEVERE, "Uncaught exception in category event bus handler", e); //NON-NLS
            }).build()
    ));

    /**
     * For performance reasons, keep current category counts in memory. All of
     * the count related methods go through this cache, which loads initial
     * values from the database if needed.
     */
    private final LoadingCache<Category, LongAdder> categoryCounts =
            CacheBuilder.newBuilder().build(CacheLoader.from(this::getCategoryCountHelper));
    /**
     * cached TagNames corresponding to Categories, looked up from
     * autopsyTagManager at initial request or if invalidated by case change.
     */
    private final LoadingCache<Category, TagName> catTagNameMap =
            CacheBuilder.newBuilder().build(CacheLoader.from(
                            cat -> getController().getTagsManager().getTagName(cat)
                    ));

    public CategoryManager(ImageGalleryController controller) {
        this.controller = controller;
    }

    private ImageGalleryController getController() {
        return controller;
    }

    /**
     * assign a new db. the counts cache is invalidated and all subsequent db
     * lookups go to the new db.
     *
     * Also clears the Category TagNames
     *
     * @param db
     */
    synchronized public void setDb(DrawableDB db) {
        this.db = db;
        invalidateCaches();
    }

    synchronized public void invalidateCaches() {
        categoryCounts.invalidateAll();
        catTagNameMap.invalidateAll();
        fireChange(Collections.emptyList(), null);
    }

    /**
     * get the number of file with the given {@link Category}
     *
     * @param cat get the number of files with Category = cat
     *
     * @return the number of files with the given Category
     */
    synchronized public long getCategoryCount(Category cat) {
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
    synchronized public void incrementCategoryCount(Category cat) {
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
    synchronized public void decrementCategoryCount(Category cat) {
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
    synchronized private LongAdder getCategoryCountHelper(Category cat) {
        LongAdder longAdder = new LongAdder();
        longAdder.decrement();
        try {
            longAdder.add(db.getCategoryCount(cat));
            longAdder.increment();
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Case closed while getting files"); //NON-NLS
        }
        return longAdder;
    }

    /**
     * fire a CategoryChangeEvent with the given fileIDs
     *
     * @param fileIDs
     */
    public void fireChange(Collection<Long> fileIDs, Category newCategory) {
        categoryEventBus.post(new CategoryChangeEvent(fileIDs, newCategory));
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

        try {
            categoryEventBus.unregister(listener);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("missing event subscriber for an annotated method. Is " + listener + " registered?")) { //NON-NLS
                /*
                 * We don't fully understand why we are getting this exception
                 * when the groups should all be registered. To avoid cluttering
                 * the logs we have disabled recording this exception. This
                 * documented in issues 738 and 802.
                 */
                //LOGGER.log(Level.WARNING, "Attempted to unregister {0} for category change events, but it was not registered.", listener.toString()); //NON-NLS
            } else {
                throw e;
            }
        }
    }

    /**
     * get the TagName used to store this Category in the main autopsy db.
     *
     * @return the TagName used for this Category
     */
    synchronized public TagName getTagName(Category cat) {
        return catTagNameMap.getUnchecked(cat);

    }

    public static Category categoryFromTagName(TagName tagName) {
        return Category.fromDisplayName(tagName.getDisplayName());
    }

    public static boolean isCategoryTagName(TagName tName) {
        return Category.isCategoryName(tName.getDisplayName());
    }

    public static boolean isNotCategoryTagName(TagName tName) {
        return Category.isNotCategoryName(tName.getDisplayName());

    }

    @Subscribe
    public void handleTagAdded(ContentTagAddedEvent event) {
        final ContentTag addedTag = event.getAddedTag();
        if (isCategoryTagName(addedTag.getName())) {
            final DrawableTagsManager tagsManager = controller.getTagsManager();
            try {
                //remove old category tag(s) if necessary
                for (ContentTag ct : tagsManager.getContentTags(addedTag.getContent())) {
                    if (ct.getId() != addedTag.getId()
                            && CategoryManager.isCategoryTagName(ct.getName())) {
                        try {
                            tagsManager.deleteContentTag(ct);
                        } catch (TskCoreException tskException) {
                            LOGGER.log(Level.SEVERE, "Failed to delete content tag. Unable to maintain categories in a consistent state.", tskException); //NON-NLS
                        }
                    }
                }
            } catch (TskCoreException tskException) {
                LOGGER.log(Level.SEVERE, "Failed to get content tags for content.  Unable to maintain category in a consistent state.", tskException); //NON-NLS
            }
            Category newCat = CategoryManager.categoryFromTagName(addedTag.getName());
            if (newCat != Category.ZERO) {
                incrementCategoryCount(newCat);
            }

            fireChange(Collections.singleton(addedTag.getContent().getId()), newCat);
        }
    }

    @Subscribe
    public void handleTagDeleted(ContentTagDeletedEvent event) {
        final ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = event.getDeletedTagInfo();
        TagName tagName = deletedTagInfo.getName();
        if (isCategoryTagName(tagName)) {

            Category deletedCat = CategoryManager.categoryFromTagName(tagName);
            if (deletedCat != Category.ZERO) {
                decrementCategoryCount(deletedCat);
            }
            fireChange(Collections.singleton(deletedTagInfo.getContentID()), null);
        }
    }

    /**
     * Event broadcast to various UI components when one or more files' category
     * has been changed
     */
    @Immutable
    public static class CategoryChangeEvent {

        private final ImmutableSet<Long> fileIDs;
        private final Category newCategory;

        public CategoryChangeEvent(Collection<Long> fileIDs, Category newCategory) {
            super();
            this.fileIDs = ImmutableSet.copyOf(fileIDs);
            this.newCategory = newCategory;
        }

        public Category getNewCategory() {
            return newCategory;
        }

        /**
         * @return the fileIDs of the files whose categories have changed
         */
        public ImmutableSet<Long> getFileIDs() {
            return fileIDs;
        }
    }
}
