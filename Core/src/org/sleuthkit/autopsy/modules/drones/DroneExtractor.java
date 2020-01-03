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
package org.sleuthkit.autopsy.modules.drones;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Abstract base class for all Drone file extractors.
 */
abstract class DroneExtractor {

    static private final String TEMP_FOLDER_NAME = "DroneExtractor";
    private final Case currentCase;

    /**
     * Common constructor. Subclasses should call super in their constructor.
     *
     * @throws DroneIngestException
     */
    protected DroneExtractor() throws DroneIngestException {
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new DroneIngestException("Unable to create drone extractor, no open case.", ex);
        }
    }

    abstract void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) throws DroneIngestException;

    abstract String getName();

    /**
     * Return the current case object.
     *
     * @return Current case
     */
    final protected Case getCurrentCase() {
        return currentCase;
    }

    /**
     * Return the current SleuthkitCase.
     *
     * @return Current sleuthkit case
     */
    final protected SleuthkitCase getSleuthkitCase() {
        return currentCase.getSleuthkitCase();
    }

    /**
     * Return the Blackboard object.
     *
     * @return Current Case blackboard object.
     */
    final protected Blackboard getBlackboard() {
        return currentCase.getSleuthkitCase().getBlackboard();
    }

    /**
     * Method to post a list of BlackboardArtifacts to the blackboard.
     *
     * @param artifacts A list of artifacts. IF list is empty or null, the
     *                  function will return.
     */
    void postArtifacts(Collection<BlackboardArtifact> artifacts) throws DroneIngestException {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }

        try {
            getBlackboard().postArtifacts(artifacts, getName());
        } catch (Blackboard.BlackboardException ex) {
            throw new DroneIngestException(String.format("Failed to post Drone artifacts to blackboard."), ex);
        }
    }

    /**
     * Create a TSK_WAYPOINT artifact with the given list of attributes.
     *
     * @param DATFile    DAT file
     * @param attributes List of BlackboardAttributes
     *
     * @return TSK_WAYPOINT BlackboardArtifact
     *
     * @throws DroneIngestException
     */
    protected BlackboardArtifact makeWaypointArtifact(AbstractFile DATFile, Collection<BlackboardAttribute> attributes) throws DroneIngestException {
        try {
            BlackboardArtifact artifact = DATFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WAYPOINT);
            artifact.addAttributes(attributes);
            return artifact;
        } catch (TskCoreException ex) {
            throw new DroneIngestException(String.format("Failed to post Drone artifacts to blackboard."), ex);
        }
    }

    /**
     * Returns a list of BlackboardAttributes for the given parameters.
     * 
     * Throws exception of longitude or latitude are null.
     *
     * @param latitude         Waypoint latitude, must be non-null
     * @param longitude        waypoint longitude, must be non-null
     * @param altitude         Waypoint altitude\height
     * @param dateTime         Timestamp the waypoint was created (Java epoch
     *                         seconds)
     * @param velocity         Velocity
     * @param distanceHP       Distance from home point
     * @param distanceTraveled Total distance the drone has traveled
     *
     * @return Collection of BlackboardAttributes
     *
     * @throws DroneIngestException
     */
    protected Collection<BlackboardAttribute> makeWaypointAttributes(Double latitude, Double longitude, Double altitude, Long dateTime, Double velocity, Double distanceHP, Double distanceTraveled) throws DroneIngestException {
        Collection<BlackboardAttribute> attributes = new ArrayList<>();

        if (latitude == null || longitude == null) {
            throw new DroneIngestException("Invalid list of waypoint attributes, longitude or latitude was null");
        }

        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE,
                DroneIngestModuleFactory.getModuleName(), latitude));

        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE,
                DroneIngestModuleFactory.getModuleName(), longitude));

        if (altitude != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE,
                    DroneIngestModuleFactory.getModuleName(), altitude));
        }

        if (dateTime != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME,
                    DroneIngestModuleFactory.getModuleName(), dateTime));
        }

        if (velocity != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_VELOCITY,
                    DroneIngestModuleFactory.getModuleName(), velocity));
        }

        if (distanceHP != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DRONE_HP_DISTANCE,
                    DroneIngestModuleFactory.getModuleName(), velocity));
        }

        if (distanceTraveled != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DRONE_DISTANCE_TRAVELED,
                    DroneIngestModuleFactory.getModuleName(), velocity));
        }

        return attributes;
    }

    /**
     * Build the temp path and create the directory if it does not currently
     * exist.
     *
     * @param currentCase   Currently open case
     * @param extractorName Name of extractor
     *
     * @return Path of the temp directory for this module
     */
    protected Path getExtractorTempPath() {
        Path path = Paths.get(currentCase.getTempDirectory(), TEMP_FOLDER_NAME, this.getClass().getCanonicalName());
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return path;
    }

    /**
     * Create a copy of file in the case temp directory.
     * 
     * @param context Current ingest context
     * @param file File to be copied
     * 
     * @return File copy.
     * 
     * @throws DroneIngestException 
     */
    protected File getTemporaryFile(IngestJobContext context, AbstractFile file) throws DroneIngestException {
        String tempFileName = file.getName() + file.getId() + file.getNameExtension();

        Path tempFilePath = Paths.get(getExtractorTempPath().toString(), tempFileName);

        try {
            ContentUtils.writeToFile(file, tempFilePath.toFile(), context::dataSourceIngestIsCancelled);
        } catch (IOException ex) {
            throw new DroneIngestException(String.format("Unable to create temp file %s for abstract file %s", tempFilePath.toString(), file.getName()), ex);
        }

        return tempFilePath.toFile();
    }

}
