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
package org.sleuthkit.autopsy.filequery;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.ArrayList;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

final class DiscoveryEvents {

    private final static EventBus discoveryEventBus = new EventBus();

    static EventBus getDiscoveryEventBus() {
        return discoveryEventBus;
    }

    private DiscoveryEvents() {
    }

    static final class SearchCompleteEvent {

        private final SearchResults results;
        private final FileType fileType;

        SearchCompleteEvent(FileType type, SearchResults results) {
            this.fileType = type;
            this.results = results;
        }

        SearchResults getSearchResults() {
            return results;
        }
        
        FileType getFileType(){
            return fileType;
        }
        
    }

    static final class GroupSelectedEvent {

        private final List<AbstractFile> files;
        private final FileType type;

        GroupSelectedEvent(FileType type, List<AbstractFile> files) {
            this.type = type;
            this.files = files;
        }
        
        FileType getType(){
            return type;
        }

        List<AbstractFile> getFiles() {
            if (files != null && !files.isEmpty()) {
                return files;
            } else {
                return new ArrayList<>();
            }
        }
    }

    static final class FileSelectedEvent {

        private final AbstractFile file;

        FileSelectedEvent(AbstractFile file) {
            this.file = file;
        }

        AbstractFile getSelectedFile() {
            return file;
        }
    }
}
