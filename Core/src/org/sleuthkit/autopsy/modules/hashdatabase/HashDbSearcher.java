/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Searches by MD5 hash to find all files with the same hash, and subsequently
 * the same content.
 */
class HashDbSearcher {
    private static final Logger logger = Logger.getLogger(HashDbSearcher.class.getName());
    /**
     * Given a string hash value, find all files with that hash.
     *
     * @param md5Hash hash value to match files with
     *
     * @return a List of all FsContent with the given hash
     */
    static List<AbstractFile> findFilesByMd5(String md5Hash) throws NoCurrentCaseException {
        final Case currentCase = Case.getOpenCase();
        final SleuthkitCase skCase = currentCase.getSleuthkitCase();
        return skCase.findFilesByMd5(md5Hash);
    }

    /**
     * Given a list of string hash values, returns a map of md5 hashes to the
     * list of files hit.
     *
     * @param md5Hash hash values to match files with
     *
     * @return a Map of md5 hashes mapped to the list of files hit
     */
    static Map<String, List<AbstractFile>> findFilesBymd5(List<String> md5Hash) throws NoCurrentCaseException {
        Map<String, List<AbstractFile>> map = new LinkedHashMap<String, List<AbstractFile>>();
        for (String md5 : md5Hash) {
            List<AbstractFile> files = findFilesByMd5(md5);
            if (!files.isEmpty()) {
                map.put(md5, files);
            }
        }
        return map;
    }

    // Same as above, but with a given ProgressHandle to accumulate and StringWorker to check if cancelled

    static Map<String, List<AbstractFile>> findFilesBymd5(List<String> md5Hash, ProgressHandle progress, SwingWorker<Object, Void> worker) throws NoCurrentCaseException {
        Map<String, List<AbstractFile>> map = new LinkedHashMap<String, List<AbstractFile>>();
        if (!worker.isCancelled()) {
            progress.switchToDeterminate(md5Hash.size());
            int size = 0;
            for (String md5 : md5Hash) {
                if (worker.isCancelled()) {
                    break;
                }
                List<AbstractFile> files = findFilesByMd5(md5);
                if (!files.isEmpty()) {
                    map.put(md5, files);
                }
                size++;
                if (!worker.isCancelled()) {
                    progress.progress(size);
                }
            }
        }
        return map;
    }

    /**
     * Given a file, returns a list of all files with the same hash as the given
     * file.
     *
     * @param file file with which to match hash values with
     *
     * @return a List of all FsContent with the same hash as file
     */
    static List<AbstractFile> findFiles(FsContent file) {
        String md5;
        try {
            if ((md5 = file.getMd5Hash()) != null) {
                return findFilesByMd5(md5);
            } else {
                return Collections.<AbstractFile>emptyList();
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return Collections.<AbstractFile>emptyList();
        }
    }

    /**
     * Checks if the search feature is ready/enabled. Does so by checking if
     * there are no Fs files in tsk_files that have and empty md5.
     *
     * @return true if the search feature is ready.
     */
    static boolean allFilesMd5Hashed() throws NoCurrentCaseException {
        final Case currentCase = Case.getOpenCase();
        final SleuthkitCase skCase = currentCase.getSleuthkitCase();
        return skCase.allFilesMd5Hashed();
    }

    /**
     * Counts the number of FsContent in the database that have an MD5
     *
     * @return the number of files with an MD5
     */
    static int countFilesMd5Hashed() throws NoCurrentCaseException {
        final Case currentCase = Case.getOpenCase();
        final SleuthkitCase skCase = currentCase.getSleuthkitCase();
        return skCase.countFilesMd5Hashed();
    }
}
