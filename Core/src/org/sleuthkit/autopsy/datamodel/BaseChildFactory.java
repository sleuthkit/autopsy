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
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.Content;

/**
 * Abstract child factory that provides paging and filtering functionality
 * to subclasses.
 * @param <T>
 */
public abstract class BaseChildFactory<T extends Content> extends ChildFactory.Detachable<T> {

    private final Predicate<T> filter;
    private boolean isPageChangeEvent;

    private final PagingSupport pagingSupport;

    /**
     * This static map is used to facilitate communication between the UI
     * and the child factory.
     */
    public static Map<String, EventBus> nodeNameToEventBusMap = new ConcurrentHashMap<>();

    public BaseChildFactory(String nodeName) {
        pagingSupport = new PagingSupport(nodeName);
        isPageChangeEvent = false;
        filter = new KnownAndSlackFilter<>();
    }

    @Override
    protected void addNotify() {
        onAdd();
        pagingSupport.initialize();        
    }
    
    @Override
    protected void removeNotify() {
        onRemove();
        pagingSupport.destroy();
    }

    /**
     * Subclasses implement this to construct a collection of keys.
     * @return 
     */
    protected abstract List<T> makeKeys();

    /**
     * Subclasses implement this to initialize any required resources.
     */
    protected abstract void onAdd();
    
    /**
     * Subclasses implement this to clean up any resources they 
     * acquired in onAdd()
     */
    protected abstract void onRemove();

    @Override
    protected boolean createKeys(List<T> toPopulate) {
        // For page chage events we simply return the previously calculated
        // keys, otherwise we make a new set of keys.
        if (!isPageChangeEvent) {
            List<T> allKeys = makeKeys();

            // Filter keys
            allKeys.stream().filter(filter).collect(Collectors.toList());
            
            pagingSupport.splitKeysIntoPages(allKeys);
        }

        toPopulate.addAll(pagingSupport.getCurrentPage());

        // Reset page change event flag
        isPageChangeEvent = false;

        return true;
    }

    /**
     * Class that supplies paging related functionality to the base
     * child factory class.
     */
    class PagingSupport {

        private final String nodeName;
        private final int pageSize;
        private int currentPage;
        private List<List<T>> pages;
        private EventBus bus;

        /**
         * Construct PagingSupport instance for the given node name.
         * @param nodeName Name of the node in the tree for which results
         * are being displayed. The node name is used to allow communication
         * between the UI and the ChildFactory via an EventBus.
         */
        PagingSupport(String nodeName) {
            pageSize = UserPreferences.getResultsTablePageSize();
            this.currentPage = 1;
            pages = new ArrayList<>();
            this.nodeName = nodeName;
        }
        
        void initialize() {
            if (pageSize > 0) {
                // Only configure an EventBus if paging functionality is enabled.
                bus = new EventBus(nodeName);
                nodeNameToEventBusMap.put(bus.identifier(), bus);
                bus.register(this);
            }            
        }

        void destroy() {
            if (bus != null) {
                nodeNameToEventBusMap.remove(bus.identifier());
                bus.unregister(this);
                bus.post(new PagingDestroyedEvent());
                bus = null;
            }            
        }
        
        /**
         * Get the list of keys at the current page.
         * @return List of keys.
         */
        List<T> getCurrentPage() {
            if (pages.size() > 0) {
                return pages.get(currentPage - 1);
            }
            
            return Collections.emptyList();
        }
        
        /**
         * Split the given collection of keys into pages based on page size.
         * @param keys 
         */
        void splitKeysIntoPages(List<T> keys) {
            int oldPageCount = pages.size();

            /**
             * If pageSize is set split keys into pages,
             * otherwise create a single page containing all keys.
             */
            pages = Lists.partition(keys, pageSize > 0 ? pageSize : keys.size());
            if (pages.size() != oldPageCount) {
                // Number of pages has changed so we need to send out a notification.
                bus.post(new PageCountChangeEvent(pages.size()));
            }
        }

        @Subscribe
        private void subscribeToPageChange(PageChangeEvent event) {
            // Receives page change events from UI components and
            // triggers a refresh in the child factory.
            if (event != null) {
                currentPage = event.getPageNumber();
                isPageChangeEvent = true;
                refresh(true);
            }
        }
    }

    public static class PageChangeEvent {

        private final int pageNumber;

        public PageChangeEvent(int newPageNumber) {
            pageNumber = newPageNumber;
        }

        public int getPageNumber() {
            return pageNumber;
        }
    }

    public static class PageCountChangeEvent {

        private final int pageCount;

        public PageCountChangeEvent(int newPageCount) {
            pageCount = newPageCount;
        }

        public int getPageCount() {
            return pageCount;
        }
    }

    public static class PagingDestroyedEvent {

    }
}
