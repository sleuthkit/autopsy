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
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this class represent the hash databases underlying known files 
 * hash sets. 
 */
public class HashDb implements Comparable<HashDb> {
    public enum Event {
        INDEXING_DONE
    }
    
    public enum KnownFilesType{
        NSRL("NSRL"), 
        KNOWN_BAD("Known Bad");
        
        private String displayName;
        
        private KnownFilesType(String displayName) {
            this.displayName = displayName;
        }
        
        String getDisplayName() {
            return this.displayName;
        }
    }
    
    /**
     * Opens an existing hash database. 
     * @param hashSetName Hash set name used to represent the hash database in user interface components. 
     * @param databasePath Full path to the database file to be created. The file name component of the path must have a ".kdb" extension.
     * @param useForIngest A flag indicating whether or not the hash database should be used during ingest.
     * @param showInboxMessages A flag indicating whether hash set hit messages should be sent to the application inbox.
     * @param knownType The known files type of the database. 
     * @return A HashDb object representation of the new hash database.
     * @throws TskCoreException 
     */
    public static HashDb openHashDatabase(String hashSetName, String databasePath, boolean useForIngest, boolean showInboxMessages, KnownFilesType knownType) throws TskCoreException {
        return new HashDb(SleuthkitJNI.openHashDatabase(databasePath), hashSetName, databasePath, useForIngest, showInboxMessages, knownType);
    }
    
    /**
     * Creates a new hash database. 
     * @param hashSetName Name used to represent the database in user interface components. 
     * @param databasePath Full path to the database file to be created. The file name component of the path must have a ".kdb" extension.
     * @param useForIngest A flag indicating whether or not the data base should be used during the file ingest process.
     * @param showInboxMessages A flag indicating whether messages indicating lookup hits should be sent to the application in box.
     * @param knownType The known files type of the database. 
     * @return A HashDb object representation of the opened hash database.
     * @throws TskCoreException 
     */
    public static HashDb createHashDatabase(String hashSetName, String databasePath, boolean useForIngest, boolean showInboxMessages, KnownFilesType type) throws TskCoreException {
        return new HashDb(SleuthkitJNI.createHashDatabase(databasePath), hashSetName, databasePath, useForIngest, showInboxMessages, type);
    }    
    
    private static final String INDEX_FILE_EXTENSION = ".kdb";
    private static final String LEGACY_INDEX_FILE_EXTENSION = "-md5.idx";
    
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);    
    private String displayName;
    private String databasePath;
    private boolean useForIngest;
    private boolean showInboxMessages;
    private KnownFilesType type;
    private int handle;
    private boolean indexing;
    
    HashDb(int handle, String name, String databasePath, boolean useForIngest, boolean showInboxMessages, KnownFilesType type) {
        this.displayName = name;
        this.databasePath = databasePath;
        this.useForIngest = useForIngest;
        this.showInboxMessages = showInboxMessages;
        this.type = type;
        this.handle = handle;
        this.indexing = false;
    }

    @Override
    public int compareTo(HashDb o) {
        return this.displayName.compareTo(o.displayName);
    }
        
    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        propertyChangeSupport.addPropertyChangeListener(pcl);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        propertyChangeSupport.removePropertyChangeListener(pcl);
    }

    public String getDisplayName() {
        return displayName;
    }
    
    public String getDatabasePath() {
        return databasePath;
    }
    
    public KnownFilesType getKnownFilesType() {
        return type;
    }
        
    public boolean getUseForIngest() {
        return useForIngest;
    }

    void setUseForIngest(boolean useForIngest) {
        this.useForIngest = useForIngest;
    }
        
    public boolean getShowInboxMessages() {
        return showInboxMessages;
    }
    
    void setShowInboxMessages(boolean showInboxMessages) {
        this.showInboxMessages = showInboxMessages;
    }
        
    public boolean hasLookupIndex() {
        try {
            return SleuthkitJNI.hashDatabaseHasLookupIndex(handle);        
        }
        catch (TskCoreException ex) {
            // RJCTODO
            return false;
        }
    }
        
    public boolean hasTextLookupIndexOnly() throws TskCoreException {
        return SleuthkitJNI.hashDatabaseHasLegacyLookupIndexOnly(handle);        
    }
    
    /**
     * Indicates whether the hash database accepts updates.
     * @return True if the database accepts updates, false otherwise.
     */
    public boolean isUpdateable() throws TskCoreException {
        return SleuthkitJNI.isUpdateableHashDatabase(this.handle);        
    }
    
    /**
     * Adds hashes of content (if calculated) to the hash database. 
     * @param content The content for which the calculated hashes, if any, are to be added to the hash database.
     * @throws TskCoreException 
     */
    public void add(Content content) throws TskCoreException {
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
        
     public TskData.FileKnown lookUp(Content content) throws TskCoreException {         
        TskData.FileKnown result = TskData.FileKnown.UKNOWN; 
         // TODO: This only works for AbstractFiles at present. Change when Content can be queried for hashes.
        assert content instanceof AbstractFile;
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile)content;
            // TODO: Add support for SHA-1 and SHA-256 hashes.
            if (null != file.getMd5Hash()) {
                if (type == KnownFilesType.NSRL) {
                    result = SleuthkitJNI.lookupInNSRLDatabase(file.getMd5Hash());
                }
                else {
                    result = SleuthkitJNI.lookupInHashDatabase(file.getMd5Hash(), handle);
                }
            }
        }         
        return result;
     }
        
    /**
     * Derives index path from an database path by appending the suffix.
     * @param databasePath
     * @return 
     */
     // RJCTODO: Thought I got rid of this...
    static String toIndexPath(String databasePath) {
        return databasePath.concat(INDEX_FILE_EXTENSION);
    }
            
    boolean isIndexing() {
        return indexing;
    }

    public IndexStatus getStatus() throws TskCoreException {
        IndexStatus status = IndexStatus.NO_INDEX;
        
        if (indexing) {
            status = IndexStatus.INDEXING;
        }
        else if (hasLookupIndex()) {
            if (hasTextLookupIndexOnly()) {
                status = IndexStatus.INDEX_ONLY;
            }
            else {
                status = IndexStatus.INDEXED;
            }
        }
        
        return status;        
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
            SleuthkitJNI.createLookupIndexForHashDatabase(handle); // RJCTODO: There is nobody to catch, fix this.
            return null;
        }

        @Override
        protected void done() {
            indexing = false;
            progress.finish();
            propertyChangeSupport.firePropertyChange(Event.INDEXING_DONE.toString(), null, displayName);
        }
    }
}