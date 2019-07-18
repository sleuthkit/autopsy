/*
 * Autopsy
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
package org.sleuthkit.autopsy.filequery;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Class to handle envent bus and events for file discovery tool
 */
final class DiscoveryEvents {

    private final static EventBus discoveryEventBus = new EventBus();

    /**
     * Get the file discovery event bus
     *
     * @return the file discovery event bus
     */
    static EventBus getDiscoveryEventBus() {
        return discoveryEventBus;
    }

    private DiscoveryEvents() {
    }

    /**
     * Event to signal the start of a search being performed
     */
    static final class SearchStartedEvent {

        private final FileType fileType;

        /**
         * Construct a new SearchStartedEvent
         *
         * @param type the type of file the search event is for
         */
        SearchStartedEvent(FileType type) {
            this.fileType = type;
        }

        /**
         * Get the type of file the search is being performed for
         *
         * @return the type of files being searched for
         */
        FileType getType() {
            return fileType;
        }
    }

    /**
     * Event to signal the completion of a search being performed
     */
    static final class SearchCompleteEvent {

        private final SearchResults results;

        /**
         * Construct a new SearchCompleteEvent
         *
         * @param results the results which were found by the search
         */
        SearchCompleteEvent(SearchResults results) {
            this.results = results;
        }

        /**
         * Get the results of the search
         *
         * @return the results of the search
         */
        SearchResults getSearchResults() {
            return results;
        }

    }

    /**
     * Event to signal the the selection of a group from the search results
     */
    static final class GroupSelectedEvent {

        private final List<AbstractFile> files;
        private final FileType type;

        /**
         * Construct a new GroupSelectedEvent
         *
         * @param type  the type of files which exist in the group
         * @param files the files in the group
         */
        GroupSelectedEvent(FileType type, List<AbstractFile> files) {
            this.type = type;
            this.files = files;
        }

        /**
         * Get the type of files which exist in the group
         *
         * @return the type of files in the group
         */
        FileType getType() {
            return type;
        }

        /**
         * Get the files in the group selected
         *
         * @return the list of AbstractFiles in the group selected
         */
        List<AbstractFile> getFiles() {
            if (files != null && !files.isEmpty()) {
                return Collections.unmodifiableList(files);
            } else {
                return new ArrayList<>();
            }
        }
    }
}
