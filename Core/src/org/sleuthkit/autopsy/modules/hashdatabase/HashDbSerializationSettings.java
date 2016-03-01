/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class to represent the settings to be serialized for hash databases
 */
class HashDbSerializationSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private final List<HashDbManager.HashDb> knownHashSets;
    private final List<HashDbManager.HashDb> knownBadHashSets;
    private final Map<HashDbManager.HashDb, String> knownPathMap = new HashMap<>();
    private final Map<HashDbManager.HashDb, String> knownBadPathMap = new HashMap<>();

    /**
     * Constructs a settings object to be serialized for hash dbs
     * @param knownHashSets
     * @param knownBadHashSets 
     */
    HashDbSerializationSettings(List<HashDbManager.HashDb> knownHashSets, List<HashDbManager.HashDb> knownBadHashSets) throws TskCoreException {
        this.knownHashSets = knownHashSets;
        this.knownBadHashSets = knownBadHashSets;
        for(HashDbManager.HashDb hashDb: this.knownHashSets) {
            knownPathMap.put(hashDb, hashDb.getDatabasePath());
        }
        
        for(HashDbManager.HashDb hashDb: this.knownBadHashSets) {
            knownBadPathMap.put(hashDb, hashDb.getDatabasePath());
        }
    }

    List<HashDbManager.HashDb> getKnownHashSets() {
        return this.knownHashSets;
    }

    /**
     * @return the knownBadHashSets
     */
    public List<HashDbManager.HashDb> getKnownBadHashSets() {
        return knownBadHashSets;
    }

    /**
     * @return the knownPathMap
     */
    public Map<HashDbManager.HashDb, String> getKnownPathMap() {
        return knownPathMap;
    }

    /**
     * @return the knownBadPathMap
     */
    public Map<HashDbManager.HashDb, String> getKnownBadPathMap() {
        return knownBadPathMap;
    }
}
