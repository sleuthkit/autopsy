/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
 * Ingest services provides convenience methods for ingest modules to use during
 * to access the Autopsy case, the case database, fire events, etc.
 */
public final class IngestServices {

    private static IngestServices instance = null;

    /**
     * Constructs an ingest services object that provides convenience methods
     * for ingest modules to use to access the Autopsy case, the case database,
     * fire events, etc.
     */
    private IngestServices() {
    }

    /**
     * Gets the ingest services singleton that provides convenience methods for
     * ingest modules to use to access the Autopsy case, the case database, fire
     * events,
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
     * Gets the current open Autopsy case.
     *
     * @return The current open case.
     *
     * @throws NoCurrentCaseException if there is no open case.
     */
    public Case getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows();
    }

    /**
     * Gets the case database of the current open Autopsy case.
     *
     * @return The current case database.
     *
     * @throws NoCurrentCaseException if there is no open case.
     */
    public SleuthkitCase getCaseDatabase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    /**
     * Gets a logger that incorporates the display name of an ingest module in
     * messages written to the Autopsy log files.
     *
     * @param moduleDisplayName The display name of the ingest module.
     *
     * @return A logger for the ingest module.
     */
    public Logger getLogger(String moduleDisplayName) {
        return Logger.getLogger(moduleDisplayName);
    }

    /**
     * Posts a message to the ingest messages in box.
     *
     * @param message An ingest message
     */
    public void postMessage(final IngestMessage message) {
        IngestManager.getInstance().postIngestMessage(message);
    }

    /**
     * Fires an event to notify registered listeners that a new artifact has
     * been posted to the blackboard.
     *
     * @param moduleDataEvent A module data event, i.e., an event that
     *                        encapsulates artifact data.
     *
     * @deprecated use org.sleuthkit.datamodel.Blackboard.postArtifact instead.
     */
    @Deprecated
    public void fireModuleDataEvent(ModuleDataEvent moduleDataEvent) {
        IngestManager.getInstance().fireIngestModuleDataEvent(moduleDataEvent);
    }

    /**
     * Fires an event to notify registered listeners that there is new content
     * added to the case. (e.g., files extracted from an archive file, carved
     * files, etc.)
     *
     * @param moduleContentEvent A module content event, i.e., an event that
     *                           encapsulates new content data.
     */
    public void fireModuleContentEvent(ModuleContentEvent moduleContentEvent) {
        IngestManager.getInstance().fireIngestModuleContentEvent(moduleContentEvent);
    }

    /**
     * Gets the free disk space of the drive where data is written during
     * ingest. Can be used by ingest modules to determine if there is enough
     * disk space before writing data is attmepted.
     *
     * @return Amount of free disk space, in bytes, or -1 if unknown.
     */
    public long getFreeDiskSpace() {
        return IngestManager.getInstance().getFreeDiskSpace();
    }

    /**
     * Gets a global configuration setting for an ingest module.
     *
     * @param moduleName  A unique identifier for the module.
     * @param settingName The name of the setting.
     *
     * @return setting The value of the setting, or null if not found.
     */
    public String getConfigSetting(String moduleName, String settingName) {
        return ModuleSettings.getConfigSetting(moduleName, settingName);
    }

    /**
     * Sets a global configuration setting for an ingest module.
     *
     * @param moduleName  A unique identifier for the module.
     * @param settingName The name of the setting.
     * @param setting     The value of the setting.
     */
    public void setConfigSetting(String moduleName, String settingName, String setting) {
        ModuleSettings.setConfigSetting(moduleName, settingName, setting);
    }

    /**
     * Gets all of the global configuration settings for an ingest module.
     *
     * @param moduleName A unique identifier for the module.
     *
     * @return A mapping of setting names to setting values.
     */
    public Map<String, String> getConfigSettings(String moduleName) {
        return ModuleSettings.getConfigSettings(moduleName);
    }

    /**
     * Sets all of the global configuration settings for an ingest module.
     *
     * @param moduleName A unique identifier for the module.
     * @param settings   A mapping of setting names to setting values.
     */
    public void setConfigSettings(String moduleName, Map<String, String> settings) {
        ModuleSettings.setConfigSettings(moduleName, settings);
    }

    /**
     * Gets the current SleuthKit case. The SleuthKit case is the case database.
     *
     * @return The current case database.
     *
     * @deprecated Use getCaseDatabase instead.
     */
    @Deprecated
    public SleuthkitCase getCurrentSleuthkitCaseDb() {
        return Case.getCurrentCase().getSleuthkitCase();
    }

    /**
     * Get the current open case.
     *
     * @return The current case.
     *
     * @deprecated Use getCase instead.
     */
    @Deprecated
    public Case getCurrentCase() {
        return Case.getCurrentCase();
    }

}
