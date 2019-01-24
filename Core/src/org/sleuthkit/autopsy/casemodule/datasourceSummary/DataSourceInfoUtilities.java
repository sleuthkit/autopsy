/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.Collections;
import java.util.HashMap;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardAttribute;

class DataSourceInfoUtilities {

    private static final Logger logger = Logger.getLogger(DataSourceInfoUtilities.class.getName());

    /**
     * Get a map containing the TSK_DATA_SOURCE_USAGE description attributes
     * associated with each data source in the current case.
     *
     * @param skCase the current SluethkitCase
     *
     * @return Collection which maps datasource id to a String which displays a
     *         comma seperated list of values of data source usage types
     *         expected to be in the datasource
     */
    static Map<Long, String> getDataSourceTypes(SleuthkitCase skCase) {
        try {
            List<BlackboardArtifact> listOfArtifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE);
            Map<Long, String> typeMap = new HashMap<>();
            for (BlackboardArtifact typeArtifact : listOfArtifacts) {
                BlackboardAttribute descriptionAttr = typeArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION));
                if (typeArtifact.getDataSource() != null && descriptionAttr != null) {
                    long dsId = typeArtifact.getDataSource().getId();
                    String type = typeMap.get(typeArtifact.getDataSource().getId());
                    if (type == null) {
                        type = descriptionAttr.getValueString();
                    } else {
                        type = type + ", " + descriptionAttr.getValueString();
                    }
                    typeMap.put(dsId, type);
                }
            }
            return typeMap;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get types of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of files in each data source in the
     * current case.
     *
     * @param skCase the current SluethkitCase
     *
     * @return Collection which maps datasource id to a count for the number of
     *         files in the datasource, will only contain entries for
     *         datasources which have at least 1 file
     */
    static Map<Long, Long> getCountsOfFiles(SleuthkitCase skCase) {
        try {
            DataSourceCountsCallback callback = new DataSourceCountsCallback();
            final String countFilesQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countFilesQuery, callback);
            return callback.getMapOfCounts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get counts of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of artifacts in each data source in the
     * current case.
     *
     * @param skCase the current SluethkitCase
     *
     * @return Collection which maps datasource id to a count for the number of
     *         artifacts in the datasource, will only contain entries for
     *         datasources which have at least 1 artifact
     */
    static Map<Long, Long> getCountsOfArtifacts(SleuthkitCase skCase) {
        try {
            DataSourceCountsCallback callback = new DataSourceCountsCallback();
            final String countArtifactsQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM blackboard_artifacts WHERE review_status_id !=" + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countArtifactsQuery, callback);
            return callback.getMapOfCounts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get counts of artifacts for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of tags which have been applied in each
     * data source in the current case. Not necessarily the same as the number
     * of items tagged, as an item can have any number of tags.
     *
     * @param skCase the current SluethkitCase
     *
     * @return Collection which maps datasource id to a count for the number of
     *         tags which have been applied in the datasource, will only contain
     *         entries for datasources which have at least 1 item tagged.
     */
    static Map<Long, Long> getCountsOfTags(SleuthkitCase skCase) {
        try {
            DataSourceCountsCallback fileCountcallback = new DataSourceCountsCallback();
            final String countFileTagsQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM content_tags as content_tags, tsk_files as tsk_files"
                    + " WHERE content_tags.obj_id = tsk_files.obj_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countFileTagsQuery, fileCountcallback);
            Map<Long, Long> tagCountMap = new HashMap<>(fileCountcallback.getMapOfCounts());
            DataSourceCountsCallback artifactCountcallback = new DataSourceCountsCallback();
            final String countArtifactTagsQuery = "data_source_obj_id, COUNT(*) AS count"
                    + " FROM blackboard_artifact_tags as artifact_tags,  blackboard_artifacts AS arts"
                    + " WHERE artifact_tags.artifact_id = arts.artifact_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            skCase.getCaseDbAccessManager().select(countArtifactTagsQuery, artifactCountcallback);
            //combine the results from the count artifact tags query into the copy of the mapped results from the count file tags query
            artifactCountcallback.getMapOfCounts().forEach((key, value) -> tagCountMap.merge(key, value, (value1, value2) -> value1 + value2));
            return tagCountMap;
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get counts of tags for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Map the names of operating systems joined in a comma seperated list to
     * the Data Source they exist on.
     *
     */
    static Map<Long, String> getOperatingSystems() {
        Map<Long, String> osDetailMap = new HashMap<>();
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            ArrayList<BlackboardArtifact> osInfoArtifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO);
            for (BlackboardArtifact osInfo : osInfoArtifacts) {
                BlackboardAttribute programName = osInfo.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));
                if (programName != null) {
                    String currentOsString = osDetailMap.get(osInfo.getDataSource().getId());
                    if (currentOsString == null || currentOsString.isEmpty()) {
                        currentOsString = programName.getValueString();
                    } else {
                        currentOsString = currentOsString + ", " + programName.getValueString();
                    }
                    osDetailMap.put(osInfo.getDataSource().getId(), currentOsString);
                }
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to load OS info artifacts.", ex);
        } 
        return osDetailMap;
    }

    private DataSourceInfoUtilities() {
    }
}
