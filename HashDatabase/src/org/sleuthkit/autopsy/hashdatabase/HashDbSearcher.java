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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Searches by MD5 hash to find all files with the same hash, and
 * subsequently the same content.
 */
public class HashDbSearcher {
    private static final Case currentCase = Case.getCurrentCase();
    private static final SleuthkitCase skCase = currentCase.getSleuthkitCase();
    private static final Logger logger = Logger.getLogger(HashDbSearcher.class.getName());
    
    /**
     * Given a string hash value, find all files with that hash.
     * @param md5Hash   hash value to match files with
     * @return a List of all FsContent with the given hash
     */
    static List<FsContent> findFilesByMd5(String md5Hash) {
        return skCase.findFilesByMd5(md5Hash);
    }
    
    /**
     * Given a list of string hash values, returns a map of md5 hashes
     * to the list of files hit.
     * @param md5Hash   hash values to match files with
     * @return a Map of md5 hashes mapped to the list of files hit
     */
    static Map<String, List<FsContent>> findFilesBymd5(List<String> md5Hash) {
        Map<String, List<FsContent>> map = new LinkedHashMap<String, List<FsContent>>();
        for(String md5 : md5Hash) {
            List<FsContent> files = findFilesByMd5(md5);
            if(!files.isEmpty()) {
                map.put(md5, files);
            }
        }
        return map;
    }
    
    /**
     * Given a file, returns a list of all files with the same
     * hash as the given file.
     * @param file  file with which to match hash values with
     * @return a List of all FsContent with the same hash as file
     */
    static List<FsContent> findFiles(FsContent file) {
        String md5;
        if((md5 = file.getMd5Hash()) != null) {
            return findFilesByMd5(md5);
        } else {
            return new ArrayList<FsContent>();
        }
    }
    
    /**
     * Checks if the search feature is ready/enabled. Does so by checking
     * if there are no Fs files in tsk_files that have and empty md5.
     * @return true if the search feature is ready.
     */
    static boolean isReady() {
        return skCase.md5HashFinished();
    }
}
