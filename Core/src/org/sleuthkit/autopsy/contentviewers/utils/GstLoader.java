/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

package org.sleuthkit.autopsy.contentviewers.utils;

import java.util.logging.Level;
import org.freedesktop.gstreamer.Gst;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A utility class that loads the gstreamer bindings.
 */
public final class GstLoader {

    private static final Logger logger = Logger.getLogger(GstLoader.class.getName());
    private static GstStatus status;

    /**
     * Attempts to load the gstreamer bindings. Only one attempt will be
     * performed per Autopsy process. Clients should not attempt to interact
     * with the gstreamer bindings unless the load was successful.
     *
     * @return Status - SUCCESS or FAILURE
     */
    public synchronized static GstStatus tryLoad() {
        // Null is our 'unknown' status. Prior to the first call, the status
        // is unknown.
        if (status != null) {
            return status;
        }

        try {
            // Setting the following property causes the GST
            // Java bindings to call dispose() on the GST 
            // service thread instead of running it in the GST
            // Native Object Reaper thread.
            System.setProperty("glib.reapOnEDT", "true");
            Gst.init();
            status = GstStatus.SUCCESS;
        } catch (Throwable ex) {
            status = GstStatus.FAILURE;
            logger.log(Level.WARNING, "Failed to load gsteamer bindings", ex);
        }

        return status;
    }

    /**
     * The various init statuses that tryLoad can return.
     */
    public enum GstStatus {
        SUCCESS, FAILURE
    }
    
    private GstLoader() {
        
    }
}
