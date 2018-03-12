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

import java.util.Map;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Singleton class that provides services for ingest modules. These exist to
 * make it easier to write modules. Use the getDefault() method to get the
 * singleton instance.
 */
public final class IngestServices {

    private static IngestServices instance = null;
    private final IngestManager manager = IngestManager.getInstance();

    private IngestServices() {
    }

    /**
     * Get the ingest services.
     *
     * @return The ingest services singleton.
     */
    public static synchronized IngestServices getInstance() {
        if (instance == null) {
            instance = new IngestServices();
        }
        return instance;
    }

    /**
     * Get the current Autopsy case.
     *
     * @return The current case.
     * @throws NoCurrentCaseException if there is no open case.
     */
    public Case getOpenCase() throws NoCurrentCaseException {
        return Case.getOpenCase();
    }

    /**
     * Get the current SleuthKit case. The SleuthKit case is the case database.
     *
     * @return The current case database.
     * @throws NoCurrentCaseException if there is no open case.
     */
    public SleuthkitCase getCurrentSleuthkitCaseDb() throws NoCurrentCaseException {
        return Case.getOpenCase().getSleuthkitCase();
    }

    /**
     * Get a logger that incorporates the display name of an ingest module in
     * messages written to the Autopsy log files.
     *
     * @param moduleDisplayName The display name of the ingest module.
     *
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
     * Fire module data event to notify registered module data event listeners
     * that there is new data of a given type from a module.
     *
     * @param moduleDataEvent module data event, encapsulating blackboard
     *                        artifact data
     */
    public void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        IngestManager.getInstance().fireIngestModuleDataEvent(moduleDataEvent);
    }

    /**
     * Fire module content event to notify registered module content event
     * listeners that there is new content (from ZIP file contents, carving,
     * etc.)
     *
     * @param moduleContentEvent module content event, encapsulating content
     *                           changed
     */
    public void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        IngestManager.getInstance().fireIngestModuleContentEvent(moduleContentEvent);
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

    /**
     * Gets a specific name/value configuration setting for a module
     *
     * @param moduleName  moduleName identifier unique to that module
     * @param settingName setting name to retrieve
     *
     * @return setting value for the module / setting name, or null if not found
     */
    public String getConfigSetting(String moduleName, String settingName) {
        return ModuleSettings.getConfigSetting(moduleName, settingName);
    }

    /**
     * Sets a specific name/value configuration setting for a module
     *
     * @param moduleName  moduleName identifier unique to that module
     * @param settingName setting name to set
     * @param settingVal  setting value to set
     */
    public void setConfigSetting(String moduleName, String settingName, String settingVal) {
        ModuleSettings.setConfigSetting(moduleName, settingName, settingVal);
    }

    /**
     * Gets all name/value configuration settings for a module
     *
     * @param moduleName moduleName identifier unique to that module
     *
     * @return settings for the module / setting name
     */
    public Map<String, String> getConfigSettings(String moduleName) {
        return ModuleSettings.getConfigSettings(moduleName);
    }

    /**
     * Sets all name/value configuration setting for a module. Names not in the
     * list will have settings preserved.
     *
     * @param moduleName moduleName identifier unique to that module
     * @param settings   settings to set and replace old settings, keeping
     *                   settings not specified in the map.
     *
     */
    public void setConfigSettings(String moduleName, Map<String, String> settings) {
        ModuleSettings.setConfigSettings(moduleName, settings);
    }
}
