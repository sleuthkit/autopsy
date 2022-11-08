/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent.DeletedContentTagInfo;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides a cached view of the number of files per category, and fires
 * CategoryChangeEvents when files are categorized.
 *
 * To receive CategoryChangeEvents, a listener must register itself, and
 * implement a public method annotated with Subscribe that accepts one argument
 * of type CategoryChangeEvent
 *
 * TODO: currently these two functions (cached counts and events) are separate
 * although they are related. Can they be integrated more?
 *
 */
public class CategoryManager {

    private static final Logger LOGGER = Logger.getLogger(CategoryManager.class.getName());

    /**
     * the DrawableDB that backs the category counts cache.
     */
    private final DrawableDB drawableDb;

    private final TagSet categoryTagSet;

    /**
     * Used to distribute CategoryChangeEvents
     */
    private final EventBus categoryEventBus = new AsyncEventBus(Executors.newSingleThreadExecutor(
            new BasicThreadFactory.Builder().namingPattern("Category Event Bus").uncaughtExceptionHandler((Thread thread, Throwable throwable) -> { //NON-NLS
                LOGGER.log(Level.SEVERE, "Uncaught exception in category event bus handler", throwable); //NON-NLS
            }).build()
    ));

    /**
     * For performance reasons, keep current category counts in memory. All of
     * the count related methods go through this cache, which loads initial
     * values from the database if needed.
     */
    private final LoadingCache<TagName, LongAdder> categoryCounts
            = CacheBuilder.newBuilder().build(CacheLoader.from(this::getCategoryCountHelper));

    public CategoryManager(ImageGalleryController controller, TagSet categoryTagSet) throws TskCoreException {
        this.drawableDb = controller.getDrawablesDatabase();
        this.categoryTagSet = categoryTagSet;
    }

    public List<TagName> getCategories() {
        return Collections.unmodifiableList(getSortedTagNames(categoryTagSet.getTagNames()));
    }

    synchronized public void invalidateCaches() {
        categoryCounts.invalidateAll();
        fireChange(Collections.emptyList(), null);
    }

    /**
     * get the number of file with the given tag
     *
     * @param tagName get the number of files with Category = tagName
     *
     * @return the number of files with the given Category
     */
    synchronized public long getCategoryCount(TagName tagName) {
        return categoryCounts.getUnchecked(tagName).sum();
    }

    /**
     * increment the cached value for the number of files with the given tag
     *
     * @param tagName the Category to increment
     */
    synchronized public void incrementCategoryCount(TagName tagName) {
        categoryCounts.getUnchecked(tagName).increment();
    }

    /**
     * decrement the cached value for the number of files with the given tag
     *
     * @param tagName the Category to decrement
     */
    synchronized public void decrementCategoryCount(TagName tagName) {
        categoryCounts.getUnchecked(tagName).decrement();
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
    synchronized private LongAdder getCategoryCountHelper(TagName cat) {
        LongAdder longAdder = new LongAdder();
        longAdder.decrement();
        try {
            longAdder.add(drawableDb.getCategoryCount(cat));
            longAdder.increment();
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Case closed while getting files", ex); //NON-NLS
        }
        return longAdder;
    }

    /**
     * fire a CategoryChangeEvent with the given fileIDs
     *
     * @param fileIDs
     */
    public void fireChange(Collection<Long> fileIDs, TagName tagName) {
        categoryEventBus.post(new CategoryChangeEvent(fileIDs, tagName));
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
            /*
             * We don't fully understand why we are getting this exception when
             * the groups should all be registered. To avoid cluttering the logs
             * we have disabled recording this exception. This documented in
             * issues 738 and 802.
             */

            if (!e.getMessage().contains("missing event subscriber for an annotated method. Is " + listener + " registered?")) { //NON-NLS
                throw e;
            }
        }
    }

    /**
     * Returns true if the given TagName is a category tag.
     *
     * @param tName TagName
     *
     * @return True if tName is a category tag.
     */
    public boolean isCategoryTagName(TagName tName) {
        return categoryTagSet.getTagNames().contains(tName);
    }

    /**
     * Returns true if the given TagName is not a category tag.
     *
     * Keep for use in location were a reference to this function is passed.
     *
     * @param tName TagName
     *
     * @return True if the given tName is not a category tag.
     */
    public boolean isNotCategoryTagName(TagName tName) {
        return !isCategoryTagName(tName);

    }

    /**
     * Returns the category tag set.
     *
     * @return
     */
    TagSet getCategorySet() {
        return categoryTagSet;
    }

    @Subscribe
    public void handleTagAdded(ContentTagAddedEvent event) {
        final ContentTag addedTag = event.getAddedTag();

        List<DeletedContentTagInfo> removedTags = event.getDeletedTags();
        if (removedTags != null) {
            for (DeletedContentTagInfo tagInfo : removedTags) {
                handleDeletedInfo(tagInfo);
            }
        }

        if (isCategoryTagName(addedTag.getName())) {
            incrementCategoryCount(addedTag.getName());
            fireChange(Collections.singleton(addedTag.getContent().getId()), addedTag.getName());
        }
    }

    @Subscribe
    public void handleTagDeleted(ContentTagDeletedEvent event) {
        final ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = event.getDeletedTagInfo();
        handleDeletedInfo(deletedTagInfo);
    }

    private void handleDeletedInfo(DeletedContentTagInfo deletedTagInfo) {
        TagName tagName = deletedTagInfo.getName();
        if (isCategoryTagName(tagName)) {
            decrementCategoryCount(tagName);
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
        private final TagName tagName;

        public CategoryChangeEvent(Collection<Long> fileIDs, TagName tagName) {
            super();
            this.fileIDs = ImmutableSet.copyOf(fileIDs);
            this.tagName = tagName;
        }

        public TagName getNewCategory() {
            return tagName;
        }

        /**
         * @return the fileIDs of the files whose categories have changed
         */
        public ImmutableSet<Long> getFileIDs() {
            return fileIDs;
        }
    }

    /**
     * Returns the a list of the given TagName values sorted by rank.
     *
     * @param tagNames A list of TagNames to be sorted.
     *
     * @return A sorted list of TagName values.
     */
    private List<TagName> getSortedTagNames(List<TagName> tagNames) {
        Comparator<TagName> compareByDisplayName = new Comparator<TagName>() {
            @Override
            public int compare(TagName tagName1, TagName tagName2) {
                return ((Integer) tagName1.getRank()).compareTo(tagName2.getRank());
            }
        };

        List<TagName> sortedTagNames = new ArrayList<>(tagNames);
        sortedTagNames.sort(compareByDisplayName);

        return sortedTagNames;
    }
}
