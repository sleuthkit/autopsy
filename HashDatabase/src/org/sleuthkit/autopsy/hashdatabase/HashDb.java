/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.hashdatabase;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class represent known file hash set databases. 
 */
// TODO: Make this an inner class of a rewritten HashDbXML, and give it a private constructor.
public class HashDb implements Comparable<HashDb> {
    enum EVENT {INDEXING_DONE};
    
    enum KNOWN_FILES_HASH_SET_TYPE{
        NSRL("NSRL"), 
        KNOWN_BAD("Known Bad");
        
        private String displayName;
        
        private KNOWN_FILES_HASH_SET_TYPE(String displayName) {
            this.displayName = displayName;
        }
        
        String getDisplayName() {
            return this.displayName;
        }
    }
    
    private static final String INDEX_FILE_EXTENSION = ".kdb";
    private static final String LEGACY_INDEX_FILE_EXTENSION = "-md5.idx";
    
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);    
    private String displayName;
    private String databasePath;
    private boolean useForIngest;
    private boolean showInboxMessages;
    private KNOWN_FILES_HASH_SET_TYPE type;
    private int handle;
    private boolean indexing;
    private boolean acceptsUpdates = false;
    
    HashDb(int handle, String name, String databasePath, boolean useForIngest, boolean showInboxMessages, KNOWN_FILES_HASH_SET_TYPE type) {
        this.displayName = name;
        this.databasePath = databasePath;
        this.useForIngest = useForIngest;
        this.showInboxMessages = showInboxMessages;
        this.type = type;
        this.handle = handle;
        this.indexing = false;
        
        try {
            acceptsUpdates = SleuthkitJNI.isUpdateableHashDatabase(this.handle);        
        }
        catch (TskCoreException ex) {
            // RJCTODO
            acceptsUpdates = false;
        }
    }

    @Override
    public int compareTo(HashDb o) {
        return this.displayName.compareTo(o.displayName);
    }
        
    /**
     * Indicates whether the hash database accepts updates.
     * @return True if the database accepts updates, false otherwise.
     */
    boolean isUpdateable() {
        return acceptsUpdates;
    }
    
    /**
     * Adds hashes of content (if calculated) to the hash database. 
     * @param content The content for which the calculated hashes, if any, are to be added to the hash database.
     * @throws TskCoreException 
     */
    public void addToHashDatabase(Content content) throws TskCoreException {
        // TODO: This only works for AbstractFiles at present. Change when Content
        // can be queried for hashes.
        assert content instanceof AbstractFile;
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile)content;
            // TODO: Add support for SHA-1 and SHA-256 hashes.
            if (null != file.getMd5Hash()) {
                SleuthkitJNI.addToHashDatabase(file.getName(), file.getMd5Hash(), "", "", handle);
            }
        }
    }
    
    void addPropertyChangeListener(PropertyChangeListener pcl) {
        pcs.addPropertyChangeListener(pcl);
    }
    
    void removePropertyChangeListener(PropertyChangeListener pcl) {
        pcs.removePropertyChangeListener(pcl);
    }

    String getDisplayName() {
        return displayName;
    }
    
    String getDatabasePath() {
        return databasePath;
    }
    
    KNOWN_FILES_HASH_SET_TYPE getKnownType() {
        return type;
    }
        
    boolean getUseForIngest() {
        return useForIngest;
    }

    void setUseForIngest(boolean useForIngest) {
        this.useForIngest = useForIngest;
    }
        
    boolean getShowInboxMessages() {
        return showInboxMessages;
    }
    
    void setShowInboxMessages(boolean showInboxMessages) {
        this.showInboxMessages = showInboxMessages;
    }
        
    boolean hasLookupIndex() {
        try {
            return SleuthkitJNI.hashDatabaseHasLookupIndex(handle);        
        }
        catch (TskCoreException ex) {
            // RJCTODO
            return false;
        }
    }
        
    boolean hasLegacyLookupIndexOnly() throws TskCoreException {
        return SleuthkitJNI.hashDatabaseHasLegacyLookupIndexOnly(handle);        
    }
    
    // TODO: This is a temporary expedient until HashDb becomes an inner class of HashDbXML.
    void setAcceptsUpdates(boolean acceptsUpdates) {
        this.acceptsUpdates = acceptsUpdates;
    }
    
    /**
     * Determines if a path points to an index by checking the suffix
     * @param path
     * @return true if index
     */
    static boolean isIndexPath(String path) {
        return (path.endsWith(INDEX_FILE_EXTENSION) || path.endsWith(LEGACY_INDEX_FILE_EXTENSION));
    }

    /**
     * Derives database path from an image path by removing the suffix.
     * @param indexPath
     * @return 
     */
    static String toDatabasePath(String indexPath) {
        if (indexPath.endsWith(LEGACY_INDEX_FILE_EXTENSION)) {
            return indexPath.substring(0, indexPath.lastIndexOf(LEGACY_INDEX_FILE_EXTENSION));
        } else {
            return indexPath.substring(0, indexPath.lastIndexOf(INDEX_FILE_EXTENSION));
        }
    }

    /**
     * Derives index path from an database path by appending the suffix.
     * @param databasePath
     * @return 
     */
    static String toIndexPath(String databasePath) {
        return databasePath.concat(INDEX_FILE_EXTENSION);
    }
            
    boolean isIndexing() {
        return indexing;
    }

    IndexStatus getStatus() {
        // RJCTODO: Fix this using new API
        
        if (indexing) {
            return IndexStatus.INDEXING;
        }
        
//        return new File(databasePath).exists();

        
//        return new File(toIndexPath(databasePath));
        
        
//        if (SleuthkitJNI.hashDatabaseIsLookupIndexOnly(handle)) {
//            return databaseExists() ? IndexStatus.NO_INDEX : IndexStatus.NONE;
//        }
                
//        return databasePath.concat(LEGACY_INDEX_FILE_EXTENSION);
        
        
        // Outdated applies only to legacy databases where the legacy index file exists and is older
//        File i = indexFile();
//        File db = new File(databasePath);
//
//        return i.exists() && db.exists() && isOlderThan(i, db);
//        
//        if (indexExists()) {
//            if (databaseSourceFileExists()) {
//                return this.isOutdated() ? IndexStatus.INDEX_OUTDATED : IndexStatus.INDEX_CURRENT;
//            } 
//            else {
//                return IndexStatus.NO_DB;
//            }
//        } else {
//            return databaseSourceFileExists() ? IndexStatus.NO_INDEX : IndexStatus.NONE;
//        }
        return IndexStatus.INDEXING;        
    }

    // Tries to index the database (overwrites any existing index) using a 
    // SwingWorker.
    void createIndex() throws TskCoreException {
        CreateIndex creator = new CreateIndex();
        creator.execute();
    }

    private class CreateIndex extends SwingWorker<Object,Void> {
        private ProgressHandle progress;
        
        CreateIndex() {
        };

        @Override
        protected Object doInBackground() throws Exception {
            indexing = true;
            progress = ProgressHandleFactory.createHandle("Indexing " + displayName);
            progress.start();
            progress.switchToIndeterminate();
            SleuthkitJNI.createLookupIndexForHashDatabase(handle);
            return null;
        }

        @Override
        protected void done() {
            indexing = false;
            progress.finish();
            pcs.firePropertyChange(EVENT.INDEXING_DONE.toString(), null, displayName);
        }
    }
}