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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides methods to query for information for all datasources in a case.
 */
public class CaseDataSourcesSummary {

    private static final Logger logger = Logger.getLogger(CaseDataSourcesSummary.class.getName());

    /**
     * Get a map containing the TSK_DATA_SOURCE_USAGE description attributes
     * associated with each data source in the current case.
     *
     * @return Collection which maps datasource id to a String which displays a
     *         comma seperated list of values of data source usage types
     *         expected to be in the datasource
     */
    public static Map<Long, String> getDataSourceTypes() {
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
    public static Map<Long, Long> getCountsOfFiles() {
        try {
            final String countFilesQuery = "data_source_obj_id, COUNT(*) AS value FROM tsk_files"
                    + " WHERE meta_type=" + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue()
                    + " AND type<>" + TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR.getFileType()
                    + " AND dir_type<>" + TskData.TSK_FS_NAME_TYPE_ENUM.VIRT_DIR.getValue()
                    + " AND name<>''"
                    + " GROUP BY data_source_obj_id"; //NON-NLS
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
    public static Map<Long, Long> getCountsOfArtifacts() {
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
    public static Map<Long, Long> getCountsOfTags() {
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

    private CaseDataSourcesSummary() {
    }
}
