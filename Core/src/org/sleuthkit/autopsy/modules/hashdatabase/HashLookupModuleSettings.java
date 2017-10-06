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
import java.io.IOException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDatabase;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Ingest job settings for the hash lookup module.
 */
final class HashLookupModuleSettings implements IngestModuleIngestJobSettings {

    private static final long serialVersionUID = 1L;
    private boolean shouldCalculateHashes = true;
    
    // These should no longer be used. They are present only for upgrading to the
    // newer format (enabled/disabled status saved in the HashDatabase object)
    private HashSet<String> namesOfEnabledKnownHashSets = null;
    private HashSet<String> namesOfDisabledKnownHashSets = null;    // Added in version 1.1
    private HashSet<String> namesOfEnabledKnownBadHashSets = null;
    private HashSet<String> namesOfDisabledKnownBadHashSets = null; // Added in version 1.1
    
    private List<HashLookupSettings.HashDbInfo> databaseInfoList;

    HashLookupModuleSettings(boolean shouldCalculateHashes, List<HashDatabase> hashDbList){
        this.shouldCalculateHashes = shouldCalculateHashes;
        try{
            databaseInfoList = HashLookupSettings.convertHashSetList(hashDbList);
        } catch (HashLookupSettings.HashLookupSettingsException ex){
            Logger.getLogger(HashLookupModuleSettings.class.getName()).log(Level.SEVERE, "Error creating hash database settings.", ex); //NON-NLS
            databaseInfoList = new ArrayList<>();
        }
    }
    
    /**
     * This overrides the default deserialization code so we can 
     * copy the enabled/disabled status into the DatabaseType objects
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
     * @param shouldCalculateHashes          Whether or not hashes should be
     *                                       calculated.
     * @param namesOfEnabledKnownHashSets    A list of enabled known hash sets.
     * @param namesOfEnabledKnownBadHashSets A list of enabled notable hash
     *                                       sets.
     */
    //HashLookupModuleSettings(boolean shouldCalculateHashes,
    //        List<String> namesOfEnabledKnownHashSets,
    //        List<String> namesOfEnabledKnownBadHashSets) {
    //    this(shouldCalculateHashes, namesOfEnabledKnownHashSets, namesOfEnabledKnownBadHashSets, new ArrayList<>(), new ArrayList<>());
    //}

    /**
     * Constructs ingest job settings for the hash lookup module.
     *
     * @param shouldCalculateHashes           Whether or not hashes should be
     *                                        calculated.
     * @param namesOfEnabledKnownHashSets     A list of enabled known hash sets.
     * @param namesOfEnabledKnownBadHashSets  A list of enabled notable hash
     *                                        sets.
     * @param namesOfDisabledKnownHashSets    A list of disabled known hash
     *                                        sets.
     * @param namesOfDisabledKnownBadHashSets A list of disabled notable hash
     *                                        sets.
     */
    //HashLookupModuleSettings(boolean shouldCalculateHashes,
    //        List<String> namesOfEnabledKnownHashSets,
    //        List<String> namesOfEnabledKnownBadHashSets,
    //        List<String> namesOfDisabledKnownHashSets,
    //        List<String> namesOfDisabledKnownBadHashSets) {
    //    this.shouldCalculateHashes = shouldCalculateHashes;
    //    this.namesOfEnabledKnownHashSets = new HashSet<>(namesOfEnabledKnownHashSets);
    //    this.namesOfEnabledKnownBadHashSets = new HashSet<>(namesOfEnabledKnownBadHashSets);
    //    this.namesOfDisabledKnownHashSets = new HashSet<>(namesOfDisabledKnownHashSets);
    //    this.namesOfDisabledKnownBadHashSets = new HashSet<>(namesOfDisabledKnownBadHashSets);
    //}

    /**
     * @inheritDoc
     */
    @Override
    public long getVersionNumber() {
        //this.upgradeFromOlderVersions();
        return HashLookupModuleSettings.serialVersionUID;
    }

    /**
     * Checks the setting that specifies whether or not hashes are to be
     * calculated.
     *
     * @return True if hashes are to be calculated, false otherwise.
     */
    boolean shouldCalculateHashes() {
        //this.upgradeFromOlderVersions();
        return this.shouldCalculateHashes;
    }

    /**
     * Checks whether or not a hash set is enabled. If there is no setting for
     * the requested hash set, it is deemed to be enabled.
     *
     * @param hashSetName The name of the hash set to check.
     *
     * @return True if the hash set is enabled, false otherwise.
     */
    //boolean isHashSetEnabled(String hashSetName) {
    //    this.upgradeFromOlderVersions();
    //    return !(this.namesOfDisabledKnownHashSets.contains(hashSetName) || this.namesOfDisabledKnownBadHashSets.contains(hashSetName));
    //}

    /**
     * Get the names of all explicitly enabled known files hash sets.
     *
     * @return The list of names.
     */
    //List<String> getNamesOfEnabledKnownHashSets() {
    //    this.upgradeFromOlderVersions();
    //    return new ArrayList<>(this.namesOfEnabledKnownHashSets);
    //}

    /**
     * Get the names of all explicitly disabled known files hash sets.
     *
     * @return The list of names.
     */
    //List<String> getNamesOfDisabledKnownHashSets() {
    //    this.upgradeFromOlderVersions();
    //    return new ArrayList<>(namesOfDisabledKnownHashSets);
    //}

    /**
     * Get the names of all explicitly enabled notable files hash sets.
     *
     * @return The list of names.
     */
    //List<String> getNamesOfEnabledKnownBadHashSets() {
    //    this.upgradeFromOlderVersions();
    //    return new ArrayList<>(this.namesOfEnabledKnownBadHashSets);
    //}

    /**
     * Get the names of all explicitly disabled notable files hash sets.
     *
     * @return The list of names.
     */
    //List<String> getNamesOfDisabledKnownBadHashSets() {
    //    this.upgradeFromOlderVersions();
    //    return new ArrayList<>(this.namesOfDisabledKnownBadHashSets);
    //}

    /**
     * Initialize fields set to null when an instance of a previous, but still
     * compatible, version of this class is de-serialized.
     */
    private void upgradeFromOlderVersions() {
        //if (null == this.namesOfDisabledKnownHashSets) {
        //    this.namesOfDisabledKnownHashSets = new HashSet<>();
        //}
        //if (null == this.namesOfDisabledKnownBadHashSets) {
        //    this.namesOfDisabledKnownBadHashSets = new HashSet<>();
        //}

        System.out.println("upgradeFromOlderVersions");

        if(databaseInfoList != null){
            System.out.println("  No upgrade needed");
            return;
        }
        
        try{
            databaseInfoList = HashLookupSettings.convertHashSetList(HashDbManager.getInstance().getAllHashSetsNew());
        } catch (HashLookupSettings.HashLookupSettingsException ex){
            Logger.getLogger(HashLookupModuleSettings.class.getName()).log(Level.SEVERE, "Error updating hash database settings.", ex); //NON-NLS
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
            if(disabledHashSetNames.contains(db.getHashSetName())){
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
