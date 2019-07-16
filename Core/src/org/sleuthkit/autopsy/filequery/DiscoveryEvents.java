/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filequery;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.ArrayList;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author wschaefer
 */
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
                return new ArrayList<AbstractFile>();
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
