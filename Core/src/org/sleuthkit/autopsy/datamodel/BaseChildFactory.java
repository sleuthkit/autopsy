/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.Content;

/**
 * Abstract child factory that provides paging and filtering functionality to
 * subclasses.
 *
 * @param <T>
 */
public abstract class BaseChildFactory<T extends Content> extends ChildFactory.Detachable<T> {

    private static final Logger logger = Logger.getLogger(BaseChildFactory.class.getName());

    private Predicate<T> filter;
    private boolean isPageChangeEvent;
    private boolean isPageSizeChangeEvent;

    private final PagingSupport pagingSupport;

    /**
     * This static map is used to facilitate communication between the UI and
     * the child factory.
     */
    private static Map<String, EventBus> nodeNameToEventBusMap = new ConcurrentHashMap<>();

    @Messages({
        "# {0} - node name", "BaseChildFactory.NoSuchEventBusException.message=No event bus for node: {0}"
    })
    public static class NoSuchEventBusException extends Exception {

        public NoSuchEventBusException(String nodeName) {
            super(Bundle.BaseChildFactory_NoSuchEventBusException_message(nodeName));
        }
    }

    /**
     * Register the given subscriber for the given node name. Will create the
     * event bus for the given node name if it does not exist.
     *
     * @param nodeName   The name of the node.
     * @param subscriber The subscriber to register.
     */
    public static void register(String nodeName, Object subscriber) {
        EventBus bus = nodeNameToEventBusMap.get(nodeName);
        if (bus == null) {
            bus = new EventBus(nodeName);
            nodeNameToEventBusMap.put(nodeName, bus);
        }
        bus.register(subscriber);
    }

    /**
     * Post the given event for the given node name.
     *
     * @param nodeName The name of the node.
     * @param event    The event to post.
     *
     * @throws
     * org.sleuthkit.autopsy.datamodel.BaseChildFactory.NoSuchEventBusException
     */
    public static void post(String nodeName, Object event) throws NoSuchEventBusException {
        EventBus bus = nodeNameToEventBusMap.get(nodeName);
        if (bus == null) {
            throw new NoSuchEventBusException(nodeName);
        }
        bus.post(event);
    }

    public BaseChildFactory(String nodeName) {
        /**
         * Initialize a no-op filter that always returns true.
         */
        this(nodeName, x -> true);
    }

    public BaseChildFactory(String nodeName, Predicate<T> filter) {
        pagingSupport = new PagingSupport(nodeName);
        pagingSupport.initialize();
        isPageChangeEvent = false;
        isPageSizeChangeEvent = false;
        this.filter = filter;
    }
    
    @Override
    protected void addNotify() {
        onAdd();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        onRemove();
    }

    /**
     * Subclasses implement this to construct a collection of keys.
     *
     * @return
     */
    protected abstract List<T> makeKeys();

    /**
     * Subclasses implement this to initialize any required resources.
     */
    protected abstract void onAdd();

    /**
     * Subclasses implement this to clean up any resources they acquired in
     * onAdd()
     */
    protected abstract void onRemove();

    @Override
    protected boolean createKeys(List<T> toPopulate) {
        /**
         * For page change events and page size change events we simply return
         * the previously calculated set of keys, otherwise we make a new set of
         * keys.
         */
        if (!isPageChangeEvent && !isPageSizeChangeEvent) {
            List<T> allKeys = makeKeys();

            pagingSupport.splitKeysIntoPages(allKeys.stream().filter(filter).collect(Collectors.toList()));
        }

        toPopulate.addAll(pagingSupport.getCurrentPage());

        // Reset page change and page size change event flags
        isPageChangeEvent = false;
        isPageSizeChangeEvent = false;

        return true;
    }

    /**
     * Event used to trigger recreation of the keys.
     */
    public static class RefreshKeysEvent {
    }

    /**
     * Event used to let subscribers know that the user has navigated to a
     * different page.
     */
    public static class PageChangeEvent {

        private final int pageNumber;

        public PageChangeEvent(int newPageNumber) {
            pageNumber = newPageNumber;
        }

        public int getPageNumber() {
            return pageNumber;
        }
    }

    /**
     * Event used to let subscribers know that the number of pages has changed.
     */
    public static class PageCountChangeEvent {

        private final int pageCount;

        public PageCountChangeEvent(int newPageCount) {
            pageCount = newPageCount;
        }

        public int getPageCount() {
            return pageCount;
        }
    }

    /**
     * Event used to let subscribers know that the page size has changed.
     */
    public static class PageSizeChangeEvent {

        private final int pageSize;

        public PageSizeChangeEvent(int newPageSize) {
            pageSize = newPageSize;
        }

        public int getPageSize() {
            return pageSize;
        }
    }

    /**
     * Class that supplies paging related functionality to the base child
     * factory class.
     */
    private class PagingSupport {

        private final String nodeName;
        private int pageSize;
        private int currentPage;
        private List<List<T>> pages;

        /**
         * Construct PagingSupport instance for the given node name.
         *
         * @param nodeName Name of the node in the tree for which results are
         *                 being displayed. The node name is used to allow
         *                 communication between the UI and the ChildFactory via
         *                 an EventBus.
         */
        PagingSupport(String nodeName) {
            currentPage = 1;
            pageSize = UserPreferences.getResultsTablePageSize();
            pages = new ArrayList<>();
            this.nodeName = nodeName;
        }

        void initialize() {
            /**
             * Set up a change listener so we know when the user changes the
             * page size.
             */
            UserPreferences.addChangeListener((PreferenceChangeEvent evt) -> {
                if (evt.getKey().equals(UserPreferences.RESULTS_TABLE_PAGE_SIZE)) {
                    pageSize = UserPreferences.getResultsTablePageSize();
                }
            });

            register(nodeName, this);
        }

        /**
         * Get the list of keys at the current page.
         *
         * @return List of keys.
         */
        List<T> getCurrentPage() {
            if (!pages.isEmpty()) {
                return pages.get(currentPage - 1);
            }

            return Collections.emptyList();
        }

        /**
         * Split the given collection of keys into pages based on page size.
         *
         * @param keys
         */
        void splitKeysIntoPages(List<T> keys) {
            int oldPageCount = pages.size();

            /**
             * If pageSize is set split keys into pages, otherwise create a
             * single page containing all keys.
             */
            if (keys.isEmpty() && !pages.isEmpty()) {
                /**
                 * If we previously had keys (i.e. pages is not empty) and now
                 * we don't have keys, reset pages to an empty list.
                 * Cannot use a call to List.clear() here because the call to
                 * Lists.partition() below returns an unmodifiable list.
                 */
                pages = new ArrayList<>();
            } else {
                pages = Lists.partition(keys, pageSize > 0 ? pageSize : keys.size());
            }

            if (pages.size() != oldPageCount) {
                try {
                    // Number of pages has changed so we need to send out a notification.
                    post(nodeName, new PageCountChangeEvent(pages.size()));
                } catch (NoSuchEventBusException ex) {
                    logger.log(Level.WARNING, "Failed to post page change event.", ex);
                }
            }
        }

        /**
         * Receives page change events from UI components and triggers a refresh
         * in the child factory.
         *
         * @param event
         */
        @Subscribe
        private void subscribeToPageChange(PageChangeEvent event) {
            if (event != null) {
                currentPage = event.getPageNumber();
                isPageChangeEvent = true;
                refresh(true);
            }
        }

        /**
         * Receives page size change events from UI components and triggers a
         * refresh in the child factory if necessary.
         *
         * @param event
         */
        @Subscribe
        private void subscribeToPageSizeChange(PageSizeChangeEvent event) {
            if (event != null) {
                int newPageSize = event.getPageSize();
                if (pageSize == newPageSize) {
                    // No change...nothing to do.
                    return;
                }

                pageSize = newPageSize;
                splitKeysIntoPages(pages.stream().flatMap(List::stream).collect(Collectors.toList()));

                currentPage = 1;
                isPageSizeChangeEvent = true;
                refresh(true);
            }
        }

        @Subscribe
        private void subscribeToRefreshKeys(RefreshKeysEvent event) {
            if (event != null) {
                refresh(true);
            }
        }
    }
}
