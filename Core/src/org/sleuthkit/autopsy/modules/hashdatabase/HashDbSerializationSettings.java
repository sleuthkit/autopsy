/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.Serializable;
import java.util.List;

/**
 * Class to represent the settings to be serialized for hash databases
 */
class HashDbSerializationSettings implements Serializable {

    private static final long serialVersionUID = 1L;
    private final List<HashDbManager.HashDb> knownHashSets;
    private final List<HashDbManager.HashDb> knownBadHashSets;

    /**
     * Constructs a settings object to be serialized for hash dbs
     * @param knownHashSets
     * @param knownBadHashSets 
     */
    HashDbSerializationSettings(List<HashDbManager.HashDb> knownHashSets, List<HashDbManager.HashDb> knownBadHashSets) {
        this.knownHashSets = knownHashSets;
        this.knownBadHashSets = knownBadHashSets;
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
}
