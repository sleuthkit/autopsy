/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
 */package org.sleuthkit.autopsy.imagegallery.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages a cache of hashset hits as a map from fileID to hashset names.
 * Initial/invalid values are loaded from the backing DrawableDB
 */
public class HashSetManager {

    /**
     * The db that initial values are loaded from.
     */
    private final DrawableDB drawableDB;

    public HashSetManager(DrawableDB drawableDB) {
        this.drawableDB = drawableDB;
    }

    /**
     * the internal cache from fileID to a set of hashset names.
     */
    private final LoadingCache<Long, Set<String>> hashSetCache = CacheBuilder.newBuilder().build(CacheLoader.from(this::getHashSetsForFileHelper));

    /**
     * helper method to load hashset hits for the given fileID from the db
     *
     * @param fileID
     *
     * @return the names of the hashsets the given fileID is in
     */
    private Set<String> getHashSetsForFileHelper(long fileID) {
        try {
            return drawableDB.getHashSetsForFile(fileID);
        } catch (TskCoreException ex) {
            Logger.getLogger(HashSetManager.class.getName()).log(Level.SEVERE, String.format("Failed to get hash sets for file (id=%d)", fileID), ex); //NON-NLS
            return Collections.emptySet();
        }
    }

    /**
     * is the given fileID in any hashset
     *
     * @param fileID
     *
     * @return true if the file is in any hashset
     */
    public boolean isInAnyHashSet(long fileID) {
        return getHashSetsForFile(fileID).isEmpty() == false;
    }

    /**
     * get the names of the hash sets the given fileId is in
     *
     * @param fileID
     *
     * @return a set containging the names of the hash sets for the given file
     */
    public Set<String> getHashSetsForFile(long fileID) {
        return hashSetCache.getUnchecked(fileID);
    }

    /**
     * invalidate the cached hashset names for the given fileID
     *
     * @param fileID the fileID to invalidate in the cache
     */
    public void invalidateHashSetsCacheForFile(long fileID) {
        hashSetCache.invalidate(fileID);
    }
}
