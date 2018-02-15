/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org *

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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.io.IOException;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupSettings.HashDbInfo;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;

/**
 * Ingest job settings for the hash lookup module.
 */
final class HashLookupModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private HashSet<String> namesOfEnabledKnownHashSets;    // The four lists of hash set names are only used for upgrading
    private HashSet<String> namesOfDisabledKnownHashSets;   // from older settings files. All data should be stored in
    private HashSet<String> namesOfEnabledKnownBadHashSets; // the databaseInfoList list.
    private HashSet<String> namesOfDisabledKnownBadHashSets;
    private boolean shouldCalculateHashes = true;
    private List<HashDbInfo> databaseInfoList;

    HashLookupModuleSettings(boolean shouldCalculateHashes, List<HashDb> hashDbList){
        this.shouldCalculateHashes = shouldCalculateHashes;
        try{
            databaseInfoList = HashLookupSettings.convertHashSetList(hashDbList);
        } catch (HashLookupSettings.HashLookupSettingsException ex){
            Logger.getLogger(HashLookupModuleSettings.class.getName()).log(Level.SEVERE, "Error creating hash set settings.", ex); //NON-NLS
            databaseInfoList = new ArrayList<>();
        }
    }
    
    /**
     * This overrides the default deserialization code so we can 
     * copy the enabled/disabled status into the DatabaseType objects.
     * Ignore the Netbeans warning that this method is unused.
     * @param stream
     * @throws IOException
     * @throws ClassNotFoundException 
     */
    private void readObject(java.io.ObjectInputStream stream)
        throws IOException, ClassNotFoundException {

        stream.defaultReadObject();
        upgradeFromOlderVersions();
    }

    /**
     * Constructs ingest job settings for the hash lookup module.
     *
     * @param shouldCalculateHashes           Whether or not hashes should be
     *                                        calculated.
     * @param enabledHashSets     A list of enabled hash sets.
     * @param disabledHashSets    A list of disabled hash sets.
     */
    HashLookupModuleSettings(boolean shouldCalculateHashes,
            List<HashDb> enabledHashSets,
            List<HashDb> disabledHashSets) {
        this.shouldCalculateHashes = shouldCalculateHashes;
        
        databaseInfoList = new ArrayList<>();
        for(HashDb db:enabledHashSets){
            try{
                HashDbInfo dbInfo = new HashDbInfo(db);
                dbInfo.setSearchDuringIngest(true);
                databaseInfoList.add(dbInfo);
            } catch (TskCoreException ex){
                Logger.getLogger(HashLookupModuleSettings.class.getName()).log(Level.SEVERE, "Error creating hash set settings for " + db.getHashSetName(), ex); //NON-NLS
            }
        }
        for(HashDb db:disabledHashSets){
            try{
                HashDbInfo dbInfo = new HashDbInfo(db);
                dbInfo.setSearchDuringIngest(false);
                databaseInfoList.add(dbInfo);
            } catch (TskCoreException ex){
                Logger.getLogger(HashLookupModuleSettings.class.getName()).log(Level.SEVERE, "Error creating hash set settings for " + db.getHashSetName(), ex); //NON-NLS
            }
        }        
        
    }

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber() {
        return HashLookupModuleSettings.serialVersionUID;
    }

    /**
     * Checks the setting that specifies whether or not hashes are to be
     * calculated.
     *
     * @return True if hashes are to be calculated, false otherwise.
     */
    boolean shouldCalculateHashes() {
        return this.shouldCalculateHashes;
    }

    /**
     * Checks whether or not a hash set is enabled. If there is no setting for
     * the requested hash set, return the default value
     *
     * @param db The hash set to check.
     *
     * @return True if the hash set is enabled, false otherwise.
     */
    boolean isHashSetEnabled(HashDb db) {
        for(HashDbInfo dbInfo:databaseInfoList){
            if(dbInfo.matches(db)){
                return dbInfo.getSearchDuringIngest();
            }
        }
        
        // We didn't find it, so use the value in the HashDb object
        return db.getSearchDuringIngest();
    }

    /**
     * Initialize fields set to null when an instance of a previous, but still
     * compatible, version of this class is de-serialized.
     */
    private void upgradeFromOlderVersions() {

        if(databaseInfoList != null){
            return;
        }
        
        try{
            databaseInfoList = HashLookupSettings.convertHashSetList(HashDbManager.getInstance().getAllHashSets());
        } catch (HashLookupSettings.HashLookupSettingsException ex){
            Logger.getLogger(HashLookupModuleSettings.class.getName()).log(Level.SEVERE, "Error updating hash set settings.", ex); //NON-NLS
            return;
        }
        
        List<String> disabledHashSetNames = new ArrayList<>();
        if(namesOfDisabledKnownHashSets != null){
            disabledHashSetNames.addAll(namesOfDisabledKnownHashSets);
        }
        if(namesOfDisabledKnownBadHashSets != null){
            disabledHashSetNames.addAll(namesOfDisabledKnownBadHashSets);
        }

        for(HashLookupSettings.HashDbInfo db:databaseInfoList){
            if(db.isFileDatabaseType() && disabledHashSetNames.contains(db.getHashSetName())){
                db.setSearchDuringIngest(false);
            } else {
                db.setSearchDuringIngest(true);
            }
        }
        
        namesOfDisabledKnownHashSets = null;
        namesOfDisabledKnownBadHashSets = null;
        namesOfEnabledKnownHashSets = null;
        namesOfEnabledKnownBadHashSets = null;  
    }

}
