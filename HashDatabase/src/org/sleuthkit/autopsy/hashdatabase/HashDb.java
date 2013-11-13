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
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this class represent hash databases used to classify files as
 * known or know bad. 
 */
public class HashDb {
    /**
     * Property change events published by hash database objects.
     */
    public enum Event {
        INDEXING_DONE
    }
    
    /**
     * The classification to apply to files whose hashes are stored in the 
     * hash database.  
     */
    public enum KnownFilesType{
        KNOWN("Known"), 
        KNOWN_BAD("Known Bad");
        
        private String displayName;
        
        private KnownFilesType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }

    private int handle;
    private KnownFilesType knownFilesType;
    private String databasePath;
    private String indexPath;
    private String hashSetName;
    private boolean useForIngest;
    private boolean sendHitMessages;
    private boolean indexing;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);    
        
    /**
     * Opens an existing hash database. 
     * @param hashSetName Name used to represent the hash database in user interface components. 
     * @param selectedFilePath Full path to either a hash database file or a hash database index file.
     * @param useForIngest A flag indicating whether or not the hash database should be used during ingest.
     * @param sendHitMessages A flag indicating whether hash set hit messages should be sent to the application in box.
     * @param knownFilesType The classification to apply to files whose hashes are stored in the hash database. 
     * @return A HashDb object representation of the new hash database.
     * @throws TskCoreException 
     */
    public static HashDb openHashDatabase(String hashSetName, String selectedFilePath, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType) throws TskCoreException {
        int handle = SleuthkitJNI.openHashDatabase(selectedFilePath);
        return new HashDb(handle, SleuthkitJNI.getHashDatabasePath(handle), SleuthkitJNI.getHashDatabaseIndexPath(handle), hashSetName, useForIngest, sendHitMessages, knownFilesType);
    }
    
    /**
     * Creates a new hash database. 
     * @param hashSetName Hash set name used to represent the hash database in user interface components. 
     * @param databasePath Full path to the database file to be created. The file name component of the path must have a ".kdb" extension.
     * @param useForIngest A flag indicating whether or not the data base should be used during the file ingest process.
     * @param showInboxMessages A flag indicating whether messages indicating lookup hits should be sent to the application in box.
     * @param hashSetType The type of hash set to associate with the database. 
     * @return A HashDb object representation of the opened hash database.
     * @throws TskCoreException 
     */
    public static HashDb createHashDatabase(String hashSetName, String databasePath, boolean useForIngest, boolean showInboxMessages, KnownFilesType knownFilesType) throws TskCoreException {
        int handle = SleuthkitJNI.createHashDatabase(databasePath);
        return new HashDb(handle, SleuthkitJNI.getHashDatabasePath(handle), SleuthkitJNI.getHashDatabaseIndexPath(handle), hashSetName, useForIngest, showInboxMessages, knownFilesType);
    }    

    private HashDb(int handle, String databasePath, String indexPath, String name, boolean useForIngest, boolean sendHitMessages, KnownFilesType knownFilesType) {
        this.databasePath = databasePath;
        this.indexPath = indexPath;
        this.hashSetName = name;
        this.useForIngest = useForIngest;
        this.sendHitMessages = sendHitMessages;
        this.knownFilesType = knownFilesType;
        this.handle = handle;
        this.indexing = false;
    }

    /**
     * Adds a listener for the events defined in HashDb.Event. 
     */
    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        propertyChangeSupport.addPropertyChangeListener(pcl);
    }
    
    /**
     * Removes a listener for the events defined in HashDb.Event. 
     */
    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        propertyChangeSupport.removePropertyChangeListener(pcl);
    }

    public String getHashSetName() {
        return hashSetName;
    }
    
    public String getDatabasePath() {
        return databasePath;
    }
    
    public String getIndexPath() {
        return indexPath;
    }
        
    public KnownFilesType getKnownFilesType() {
        return knownFilesType;
    }
        
    public boolean getUseForIngest() {
        return useForIngest;
    }

    void setUseForIngest(boolean useForIngest) {
        this.useForIngest = useForIngest;
    }
        
    public boolean getShowInboxMessages() {
        return sendHitMessages;
    }
    
    void setShowInboxMessages(boolean showInboxMessages) {
        this.sendHitMessages = showInboxMessages;
    }

    // RJCTODO: Add comments
    public boolean hasLookupIndex() throws TskCoreException {
        return SleuthkitJNI.hashDatabaseHasLookupIndex(handle);        
    }
        
    public boolean canBeReindexed() throws TskCoreException {
        return SleuthkitJNI.hashDatabaseCanBeReindexed(handle);
    }
    
    public boolean hasIndexOnly() throws TskCoreException {
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
                result = SleuthkitJNI.lookupInHashDatabase(file.getMd5Hash(), handle);
            }
        }         
        return result;
     }
                    
    boolean isIndexing() {
        return indexing;
    }

    // Tries to index the database (overwrites any existing index) using a 
    // SwingWorker.
    void createIndex(boolean deleteIndexFile) {
        CreateIndex creator = new CreateIndex(deleteIndexFile);
        creator.execute();
    }

    private class CreateIndex extends SwingWorker<Object,Void> {
        private ProgressHandle progress;
        private boolean deleteIndexFile;
        
        CreateIndex(boolean deleteIndexFile) {
            this.deleteIndexFile = deleteIndexFile;
        };

        @Override
        protected Object doInBackground() {
            indexing = true;
            progress = ProgressHandleFactory.createHandle("Indexing " + hashSetName);
            progress.start();
            progress.switchToIndeterminate();
            try {
                SleuthkitJNI.createLookupIndexForHashDatabase(handle, deleteIndexFile);
                indexPath = SleuthkitJNI.getHashDatabaseIndexPath(handle);
            }
            catch (TskCoreException ex) {
                Logger.getLogger(HashDb.class.getName()).log(Level.SEVERE, "Error indexing hash database", ex);                
                JOptionPane.showMessageDialog(null, "Error indexing hash database for " + getHashSetName() + ".", "Hash Database Index Error", JOptionPane.ERROR_MESSAGE);                
            }
            return null;
        }

        @Override
        protected void done() {
            indexing = false;
            progress.finish();
            propertyChangeSupport.firePropertyChange(Event.INDEXING_DONE.toString(), null, hashSetName);
        }
    }
    
    public void close() throws TskCoreException {
        SleuthkitJNI.closeHashDatabase(handle);
    }
}