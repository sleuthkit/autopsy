/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
 * An adapter that provides a default implementation of the IngestModule
 * interface.
 */
public abstract class IngestModuleAdapter implements IngestModule {
    // Maps a JobId to the count of instances
    static HashMap<Long, Long> moduleRefCount = new HashMap<>(); 
    
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

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
    }

    @Override
    public void shutDown(boolean ingestJobCancelled) {
    }
}
