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


package org.sleuthkit.autopsy.ingest;

import java.util.HashMap;

/**
 * A utility class that modules can use to keep track of whether they are the 
 * first/last instance for a particular job.
 */
public class ModuleReferenceCounter {
    // Maps a JobId to the count of instances
    private static HashMap<Long, Long> moduleRefCount = new HashMap<>(); 

    public static synchronized long moduleRefCountIncrementAndGet(long jobId) {
        long count = moduleRefCount.containsKey(jobId) ? moduleRefCount.get(jobId) : 0;
        long nextCount = count + 1;
        moduleRefCount.put(jobId, nextCount);
        return nextCount;
    }

    public static synchronized long moduleRefCountDecrementAndGet(long jobId) {
        if (moduleRefCount.containsKey(jobId)) {
            long count = moduleRefCount.get(jobId);
            moduleRefCount.put(jobId, --count);
            return count;
        } else {
            return 0;
        }
    }    
}
