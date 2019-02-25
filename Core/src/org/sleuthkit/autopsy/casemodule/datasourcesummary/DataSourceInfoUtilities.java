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
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;

/**
 * Utilities for getting information about a data source or all data sources
 * from the case database.
 */
final class DataSourceInfoUtilities {

    private static final Logger logger = Logger.getLogger(DataSourceInfoUtilities.class.getName());

    /**
     * Get a map containing the TSK_DATA_SOURCE_USAGE description attributes
     * associated with each data source in the current case.
     *
     * @return Collection which maps datasource id to a String which displays a
     *         comma seperated list of values of data source usage types
     *         expected to be in the datasource
     */
    static Map<Long, String> getDataSourceTypes() {
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
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
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get types of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of files in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         files in the datasource, will only contain entries for
     *         datasources which have at least 1 file
     */
    static Map<Long, Long> getCountsOfFiles() {
        try {
            final String countFilesQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countFilesQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of artifacts in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         artifacts in the datasource, will only contain entries for
     *         datasources which have at least 1 artifact
     */
    static Map<Long, Long> getCountsOfArtifacts() {
        try {
            final String countArtifactsQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM blackboard_artifacts WHERE review_status_id !=" + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countArtifactsQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of artifacts for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of tags which have been applied in each
     * data source in the current case. Not necessarily the same as the number
     * of items tagged, as an item can have any number of tags.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         tags which have been applied in the datasource, will only contain
     *         entries for datasources which have at least 1 item tagged.
     */
    static Map<Long, Long> getCountsOfTags() {
        try {
            final String countFileTagsQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM content_tags as content_tags, tsk_files as tsk_files"
                    + " WHERE content_tags.obj_id = tsk_files.obj_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            //new hashmap so it can be modifiable
            Map<Long, Long> tagCountMap = new HashMap<>(getValuesMap(countFileTagsQuery));
            final String countArtifactTagsQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM blackboard_artifact_tags as artifact_tags,  blackboard_artifacts AS arts"
                    + " WHERE artifact_tags.artifact_id = arts.artifact_id"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
            //combine the results from the count artifact tags query into the copy of the mapped results from the count file tags query
            getValuesMap(countArtifactTagsQuery).forEach((key, value) -> tagCountMap.merge(key, value, (value1, value2) -> value1 + value2));
            return tagCountMap;
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of tags for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the names of operating systems joined in a comma
     * seperated list to the Data Source they exist on in the current case. No
     * item will exist in the map for data sources which do not contain
     * TS_OS_INFO artifacts which have a program name.
     *
     * @return Collection which maps datasource id to a String which is a comma
     *         seperated list of Operating system names found on the data
     *         source.
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

    /**
     * Get the number of files in the case database for the current data source
     * which have the specified mimetypes.
     *
     * @param currentDataSource the data source which we are finding a file
     *                          count
     *
     * @param setOfMimeTypes    the set of mime types which we are finding the
     *                          number of occurences of
     *
     * @return a Long value which represents the number of occurrences of the
     *         specified mime types in the current case for the specified data
     *         source, null if no count was retrieved
     */
    static Long getCountOfFilesForMimeTypes(DataSource currentDataSource, Set<String> setOfMimeTypes) {
        if (currentDataSource != null) {
            try {
                String inClause = String.join("', '", setOfMimeTypes);
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                return skCase.countFilesWhere("data_source_obj_id=" + currentDataSource.getId()
                        + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                        + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                        + " AND mime_type IN ('" + inClause + "')"
                        + " AND name<>''");
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to get count of files for specified mime types", ex);
                //unable to get count of files for the specified mimetypes cell will be displayed as empty
            }
        }
        return null;
    }

    /**
     * Get a map containing the number of unallocated files in each data source
     * in the current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         unallocated files in the datasource, will only contain entries
     *         for datasources which have at least 1 unallocated file
     */
    static Map<Long, Long> getCountsOfUnallocatedFiles() {
        try {
            final String countUnallocatedFilesQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND dir_flags=" + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countUnallocatedFilesQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of unallocated files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the total size of unallocated files in each data
     * source in the current case.
     *
     * @return Collection which maps datasource id to a total size in bytes of
     *         unallocated files in the datasource, will only contain entries
     *         for datasources which have at least 1 unallocated file
     */
    static Map<Long, Long> getSizeOfUnallocatedFiles() {
        try {
            final String countUnallocatedFilesQuery = "data_source_obj_id, sum(size) AS value"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND dir_flags=" + TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countUnallocatedFilesQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get size of unallocated files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of directories in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         directories in the datasource, will only contain entries for
     *         datasources which have at least 1 directory
     */
    static Map<Long, Long> getCountsOfDirectories() {
        try {
            final String countDirectoriesQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM tsk_files WHERE type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND meta_type=" + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countDirectoriesQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of directories for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing the number of slack files in each data source in the
     * current case.
     *
     * @return Collection which maps datasource id to a count for the number of
     *         slack files in the datasource, will only contain entries for
     *         datasources which have at least 1 slack file
     */
    static Map<Long, Long> getCountsOfSlackFiles() {
        try {
            final String countSlackFilesQuery = "data_source_obj_id, COUNT(*) AS value"
                    + " FROM tsk_files WHERE type=" + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND name<>'' GROUP BY data_source_obj_id"; //NON-NLS
            return getValuesMap(countSlackFilesQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of slack files for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Get a map containing maps which map artifact type to the number of times
     * it exosts in each data source in the current case.
     *
     * @return Collection which maps datasource id to maps of artifact display
     *         name to number of occurences in the datasource, will only contain
     *         entries for artifacts which have at least one occurence in the
     *         data source.
     */
    static Map<Long, Map<String, Long>> getCountsOfArtifactsByType() {
        try {
            final String countArtifactsQuery = "blackboard_artifacts.data_source_obj_id, blackboard_artifact_types.display_name AS label, COUNT(*) AS value"
                    + " FROM blackboard_artifacts, blackboard_artifact_types"
                    + " WHERE blackboard_artifacts.artifact_type_id = blackboard_artifact_types.artifact_type_id"
                    + " GROUP BY blackboard_artifacts.data_source_obj_id, blackboard_artifact_types.display_name";
            return getLabeledValuesMap(countArtifactsQuery);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to get counts of all artifact types for all datasources, providing empty results", ex);
            return Collections.emptyMap();
        }
    }

    /**
     * Helper method to execute a select query with a
     * DataSourceLabeledValueCallback.
     *
     * @param query the portion of the query which should follow the SELECT
     *
     * @return a map of datasource object IDs to maps of String labels to the
     *         values associated with them.
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     */
    private static Map<Long, Map<String, Long>> getLabeledValuesMap(String query) throws TskCoreException, NoCurrentCaseException {
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        DataSourceLabeledValueCallback callback = new DataSourceLabeledValueCallback();
        skCase.getCaseDbAccessManager().select(query, callback);
        return callback.getMapOfLabeledValues();
    }

    /**
     * Helper method to execute a select query with a
     * DataSourceSingleValueCallback.
     *
     * @param query the portion of the query which should follow the SELECT
     *
     * @return a map of datasource object ID to a value of type Long
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     */
    private static Map<Long, Long> getValuesMap(String query) throws TskCoreException, NoCurrentCaseException {
        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        DataSourceSingleValueCallback callback = new DataSourceSingleValueCallback();
        skCase.getCaseDbAccessManager().select(query, callback);
        return callback.getMapOfValues();
    }

    /**
     * Empty private constructor
     */
    private DataSourceInfoUtilities() {
    }
}
