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
package org.sleuthkit.autopsy.ingest;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;

/**
 * IngestManager sets up and manages ingest services
 * runs them in a background thread
 * notifies services when work is complete or should be interrupted
 * processes messages from services in postMessage() and posts them to GUI
 * 
 */
public class IngestManager {
    
    private static final Logger logger = Logger.getLogger(IngestManager.class.getName());
    private IngestTopComponent tc;
    private IngestManagerStats stats;
    private int updateFrequency;
    
    

    /**
     * 
     * @param tc handle to Ingest top component
     */
    IngestManager(IngestTopComponent tc) {
        this.tc = tc;
        stats = new IngestManagerStats();
    }

    /**
     * IngestManager entry point, enqueues image to be processed.
     * Spawns background thread which enumerates all sorted files and executes chosen services per file in a pre-determined order.
     * Notifies services when work is complete or should be interrupted using complete() and stop() calls.
     * Does not block and can be called multiple times to enqueue more work to already running background process.
     */
    void execute(Collection<IngestServiceAbstract> services, Image image) {
    }
    
    /**
     * returns the current minimal update frequency setting
     * Services should call this between processing iterations to get current setting
     * and use the setting to change notification and data refresh intervals
     */
    public synchronized int getUpdateFrequency() {
        return updateFrequency;
    }
    
    /**
     * set new minimal update frequency services should use
     * @param frequency 
     */
    synchronized void setUpdateFrequency(int frequency) {
        this.updateFrequency = frequency;
    }

    /**
     * returns ingest summary report (how many files ingested, any errors, etc)
     */
    String getReport() {
        return stats.toString();
    }

    /**
     * Service publishes message using InegestManager handle
     * Does not block.
     * The message gets enqueued in the GUI thread and displayed in a widget
     * IngestService should make an attempt not to publish the same message multiple times.
     * Viewer will attempt to identify duplicate messages and filter them out (slower)
     */
    public synchronized void postMessage(final IngestMessage message) {
        
        SwingUtilities.invokeLater(new Runnable() {
            
            @Override
            public void run() {
                tc.displayMessage(message);
            }
        });
    }

    /**
     * helper to return all image services managed (using Lookup API)
     */
    public static Collection<IngestServiceImage> enumerateImageServices() {
        return (Collection<IngestServiceImage>) Lookup.getDefault().lookupAll(IngestServiceImage.class);
    }

    /**
     * helper to return all file/dir services managed (using Lookup API)
     */
    public static Collection<IngestServiceFsContent> enumerateFsContentServices() {
        return (Collection<IngestServiceFsContent>) Lookup.getDefault().lookupAll(IngestServiceFsContent.class);
    }

    /**
     * get next file/dir to process
     * the queue of FsContent to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private synchronized FsContent getNextFsContent() {
        return null;
    }
    
    private synchronized boolean hasNextFsContent() {
        return true;
    }

    /**
     * get next Image to process
     * the queue of Images to process is maintained internally 
     * and could be dynamically sorted as data comes in
     */
    private synchronized Image getNextImage() {
        return null;
    }
    
    private synchronized boolean hasNextImage() {
        return true;
    }

    /**
     * collects IngestManager statistics during runtime
     */
    private static class IngestManagerStats {

        Date startTime;
        Date endTime;
        int errorsTotal;
        Map<IngestServiceAbstract, Integer> errors;
        private static DateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        IngestManagerStats() {
            errors = new HashMap<IngestServiceAbstract, Integer>();
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (startTime != null) {
                sb.append("Start time: ").append(dateFormatter.format(startTime));
            }
            if (endTime != null) {
                sb.append("End time: ").append(dateFormatter.format(endTime));
            }
            sb.append("Total ingest time: ").append(getTotalTime());
            sb.append("Total errors: ").append(errorsTotal);
            if (errorsTotal > 0) {
                sb.append("Errors per service:");
                for (IngestServiceAbstract service : errors.keySet()) {
                    final int errorsService = errors.get(service);
                    sb.append("\t").append(service.getName()).append(": ").append(errorsService);
                }
            }
            return sb.toString();
        }
        
        void start() {
            startTime = new Date();
        }

        void end() {
            endTime = new Date();
        }
        
        long getTotalTime() {
            if (startTime == null || endTime == null) {
                return 0;
            }
            return endTime.getTime() - startTime.getTime();
        }
        
        void addError(IngestServiceAbstract source) {
            ++errorsTotal;
            int curServiceError = errors.get(source);
            errors.put(source, curServiceError + 1);
        }
    }
}
