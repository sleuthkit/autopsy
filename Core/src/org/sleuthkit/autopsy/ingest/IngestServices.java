/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Singleton class that provides services for ingest modules. These exist to
 * make it easier to write modules. Use the getDefault() method to get the
 * singleton instance.
 */
public final class IngestServices {
    public static final int DISK_FREE_SPACE_UNKNOWN = -1; // RJCTODO: Move this back to the monitor or ingest manager? It is used here...

    private static final Logger logger = Logger.getLogger(IngestServices.class.getName());
    private IngestManager manager;
    private static IngestServices instance;

    private IngestServices() {
        this.manager = IngestManager.getDefault();
    }

    /**
     * Get the ingest services.
     *
     * @return The ingest services singleton.
     */
    public static synchronized IngestServices getDefault() {
        if (instance == null) {
            instance = new IngestServices();
        }
        return instance;
    }

    /**
     * Get the current Autopsy case. 
     *
     * @return The current case.
     */
    public Case getCurrentCase() {
        return Case.getCurrentCase();
    }

    /**
     * Get the current SleuthKit case. The SleuthKit case is the case database.
     *
     * @return The current case database.
     */
    public SleuthkitCase getCurrentSleuthkitCaseDb() {
        return Case.getCurrentCase().getSleuthkitCase();
    }

   /**
     * Get a logger that incorporates the display name of an ingest module in 
     * messages written to the Autopsy log files.
     * 
     * @param moduleClassName The display name of the ingest module.
     * @return The custom logger for the ingest module. 
     */
    public Logger getLogger(String moduleDisplayName) {
        return Logger.getLogger(moduleDisplayName);
    }
        
    /**
     * Post message to the ingest messages in box.
     *
     * @param message An ingest message
     */
    public void postMessage(final IngestMessage message) {
        manager.postIngestMessage(message);
    }

    /**
     * Fire module event to notify registered module event listeners
     *
     * @param eventType the event type, defined in
     * IngestManager.IngestManagerEvents
     * @param moduleName the module name
     */
    public void fireModuleEvent(String eventType, String moduleName) {
        IngestManager.fireModuleEvent(eventType, moduleName);
    }

    /**
     * Fire module data event to notify registered module data event listeners
     * that there is new data of a given type from a module
     *
     * @param moduleDataEvent module data event, encapsulating blackboard
     * artifact data
     */
    public void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        IngestManager.fireModuleDataEvent(moduleDataEvent);
    }

    /**
     * Fire module content event to notify registered module content event
     * listeners that there is new content (from ZIP file contents, carving,
     * etc.)
     *
     * @param moduleContentEvent module content event, encapsulating content
     * changed
     */
    public void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        IngestManager.fireModuleContentEvent(moduleContentEvent);
    }

    // RJCTODO: This can stay in the context since it is context (pipeline) specific
    /**
     * Schedule a new file for ingest with the same settings as the file being
     * analyzed. This is used, for example, when opening an archive file. File
     * needs to have already been added to the database.
     *
     * @param file file to be scheduled
     * @param pipelineContext the ingest context for the file ingest pipeline
     */
    public void scheduleFile(long dataSourceTaskId, AbstractFile file) {
        logger.log(Level.INFO, "Scheduling file: {0}", file.getName());
        manager.scheduleFileTask(dataSourceTaskId, file);
    }

    /**
     * Get free disk space of a drive where ingest data are written to That
     * drive is being monitored by IngestMonitor thread when ingest is running.
     *
     * @return amount of disk space, -1 if unknown
     */
    public long getFreeDiskSpace() {
        return manager.getFreeDiskSpace();
    }    
 }
