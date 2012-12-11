/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
import java.io.File;
import java.util.List;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskException;

/**
 * Hash database representation of NSRL and Known Bad hash databases
 * with indexing capability
 * 
 */
public class HashDb implements Comparable<HashDb> {

    enum EVENT {INDEXING_DONE };
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    
    public enum DBType{
        NSRL("NSRL"), KNOWN_BAD("Known Bad");
        
        private String displayName;
        
        private DBType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return this.displayName;
        }
    }
    
    // Suffix added to the end of a database name to get its index file
    private static final String INDEX_SUFFIX = "-md5.idx";
    
    private String name;
    private List<String> databasePaths; // TODO: Length limited to one for now...
    private boolean useForIngest;
    private boolean showInboxMessages;
    private boolean indexing;
    private DBType type;
    
    public HashDb(String name, List<String> databasePaths, boolean useForIngest, boolean showInboxMessages, DBType type) {
        this.name = name;
        this.databasePaths = databasePaths;
        this.useForIngest = useForIngest;
        this.showInboxMessages = showInboxMessages;
        this.type = type;
        this.indexing = false;
    }
    
    void addPropertyChangeListener(PropertyChangeListener pcl) {
        pcs.addPropertyChangeListener(pcl);
    }
    
    void removePropertyChangeListener(PropertyChangeListener pcl) {
        pcs.removePropertyChangeListener(pcl);
    }
    
    boolean getUseForIngest() {
        return useForIngest;
    }
    
    boolean getShowInboxMessages() {
        return showInboxMessages;
    }
    
    DBType getDbType() {
        return type;
    }
    
    String getName() {
        return name;
    }
    
    List<String> getDatabasePaths() {
        return databasePaths;
    }
    
    void setUseForIngest(boolean useForIngest) {
        this.useForIngest = useForIngest;
    }
    
    void setShowInboxMessages(boolean showInboxMessages) {
        this.showInboxMessages = showInboxMessages;
    }
    
    void setName(String name) {
        this.name = name;
    }
    
    void setDatabasePaths(List<String> databasePaths) {
        this.databasePaths = databasePaths;
    }
    
    void setDbType(DBType type) {
        this.type = type;
    }
    
    /**
     * Checks if the database exists.
     * @return true if a file exists at the database path, else false
     */
    boolean databaseExists() {
        return databaseFile().exists();
    }
    
    /**
     * Checks if Sleuth Kit can open the index for the database path.
     * @return true if the index was found and opened successfully, else false
     */
    boolean indexExists() {
        try {
            return hasIndex(databasePaths.get(0)); // TODO: support multiple paths
        } catch (TskException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error checking if index exists.", ex);
            return false;
        }
    }

    /**
     * Gets the database file.
     * @return a File initialized with the database path
     */
    File databaseFile() {
        return new File(databasePaths.get(0)); // TODO: support multiple paths
    }
    
    /**
     * Gets the index file
     * @return a File initialized with an index path derived from the database
     * path
     */
    File indexFile() {
        return new File(toIndexPath(databasePaths.get(0))); // TODO: support multiple paths
    }

    /**
     * Checks if the index file is older than the database file
     * @return true if there is are files at the index path and the database
     * path, and the index file has an older modified-time than the database
     * file, else false
     */
    boolean isOutdated() {
        File i = indexFile();
        File db = databaseFile();

        return i.exists() && db.exists() && isOlderThan(i, db);
    }
    
    /**
     * Checks if the database is being indexed
     */
    boolean isIndexing() {
        return indexing;
    }

    /**
     * Returns the status of the HashDb as determined from indexExists(),
     * databaseExists(), and isOutdated()
     * @return IndexStatus enum according to their definitions
     */
    IndexStatus status() {
        boolean i = this.indexExists();
        boolean db = this.databaseExists();

        if(indexing)
            return IndexStatus.INDEXING;
        if (i) {
            if (db) {
                return this.isOutdated() ? IndexStatus.INDEX_OUTDATED : IndexStatus.INDEX_CURRENT;
            } else {
                return IndexStatus.NO_DB;
            }
        } else {
            return db ? IndexStatus.NO_INDEX : IndexStatus.NONE;
        }
    }

    /**
     * Tries to index the database (overwrites any existing index)
     * @throws TskException if an error occurs in the SleuthKit bindings 
     */
    void createIndex() throws TskException {
        indexing = true;
        CreateIndex creator = new CreateIndex();
        creator.execute();
    }

    /**
     * Checks if one file is older than an other
     * @param a first file
     * @param b second file
     * @return true if the first file's last modified data is before the second
     * file's last modified date
     */
    private static boolean isOlderThan(File a, File b) {
        return a.lastModified() < b.lastModified();
    }

    /**
     * Determines if a path points to an index by checking the suffix
     * @param path
     * @return true if index
     */
    static boolean isIndexPath(String path) {
        return path.endsWith(INDEX_SUFFIX);
    }

    /**
     * Derives database path from an image path by removing the suffix.
     * @param indexPath
     * @return 
     */
    static String toDatabasePath(String indexPath) {
        return indexPath.substring(0, indexPath.lastIndexOf(INDEX_SUFFIX));
    }

    /**
     * Derives image path from an database path by appending the suffix.
     * @param databasePath
     * @return 
     */
    static String toIndexPath(String databasePath) {
        return databasePath.concat(INDEX_SUFFIX);
    }

    /**
     * Calls Sleuth Kit method via JNI to determine whether there is an
     * index for the given path
     * @param databasePath path Path for the database the index is of
     * (database doesn't have to actually exist)'
     * @return true if index exists
     * @throws TskException if  there is an error in the JNI call 
     */
    static boolean hasIndex(String databasePath) throws TskException {
        return SleuthkitJNI.lookupIndexExists(databasePath);
    }
    
    @Override
    public int compareTo(HashDb o) {
        return this.name.compareTo(o.name);
    }
    
    /* Thread that creates a database's index */
    private class CreateIndex extends SwingWorker<Object,Void> {

        private ProgressHandle progress;
        
        CreateIndex(){};

        @Override
        protected Object doInBackground() throws Exception {
            progress = ProgressHandleFactory.createHandle("Indexing " + name);

        /** We need proper cancel support in TSK to make the task cancellable
         new Cancellable() {
                Override
                public boolean cancel() {
                    return CreateIndex.this.cancel(true);
                }
            });
            */
            progress.start();
            progress.switchToIndeterminate();
            SleuthkitJNI.createLookupIndex(databasePaths.get(0));
            return null;
        }

        /* clean up or start the worker threads */
        @Override
        protected void done() {
            indexing = false;
            progress.finish();
            pcs.firePropertyChange(EVENT.INDEXING_DONE.toString(), null, name);
        }
    }
}