/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Generates a <code>List<CommonFilesMetadata></code> when
 * <code>findMatchesByCount()</code> is called, which organizes files by md5 to
 * prepare to display in viewer.
 *
 * This entire thing runs on a background thread where exceptions are handled.
 */
@SuppressWarnings("PMD.AbstractNaming")
public abstract class IntraCaseCommonAttributeSearcher extends AbstractCommonAttributeSearcher {

    private static final String FILTER_BY_MIME_TYPES_WHERE_CLAUSE = " and mime_type in (%s)"; //NON-NLS // where %s is csv list of mime_types to filter on

    private final Map<Long, String> dataSourceIdToNameMap;

    /**
     * Subclass this to implement different algorithms for getting common files.
     *
     * @param dataSourceIdMap       a map of obj_id to datasource name
     * @param filterByMediaMimeType match only on files whose mime types can be
     *                              broadly categorized as media types
     * @param filterByDocMimeType   match only on files whose mime types can be
     *                              broadly categorized as document types
     */
    IntraCaseCommonAttributeSearcher(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType, int percentageThreshold) {
        super(filterByMediaMimeType, filterByDocMimeType, percentageThreshold);
        this.dataSourceIdToNameMap = dataSourceIdMap;
    }

    Map<Long, String> getDataSourceIdToNameMap() {
        return Collections.unmodifiableMap(this.dataSourceIdToNameMap);
    }

    /**
     * Use this as a prefix when building the SQL select statement.
     *
     * <ul>
     * <li>You only have to specify the WHERE clause if you use this.</li>
     * <li>If you do not use this string, you must use at least the columns
     * selected below, in that order.</li>
     * </ul>
     */
    static final String SELECT_PREFIX = "SELECT obj_id, md5, data_source_obj_id from tsk_files where"; //NON-NLS

    /**
     * Should build a SQL SELECT statement to be passed to
     * SleuthkitCase.executeQuery(sql) which will select the desired file ids
     * and MD5 hashes.
     *
     * The statement should select obj_id, md5, data_source_obj_id in that
     * order.
     *
     * @return sql string select statement
     */
    protected abstract String buildSqlSelectStatement();

    /**
     * Generate a meta data object which encapsulates everything need to add the
     * tree table tab to the top component.
     *
     * @return a data object with all of the matched files in a hierarchical
     *         format
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     */
    @Override
    public CommonAttributeCountSearchResults findMatchesByCount() throws TskCoreException, NoCurrentCaseException, SQLException {
        Map<String, CommonAttributeValue> commonFiles = new HashMap<>();

        final Case currentCase = Case.getCurrentCaseThrows();
        final String caseName = currentCase.getDisplayName();

        SleuthkitCase sleuthkitCase = currentCase.getSleuthkitCase();

        String selectStatement = this.buildSqlSelectStatement();

        try (
                CaseDbQuery query = sleuthkitCase.executeQuery(selectStatement);
                ResultSet resultSet = query.getResultSet()) {

            while (resultSet.next()) {
                Long objectId = resultSet.getLong(1);
                String md5 = resultSet.getString(2);
                Long dataSourceId = resultSet.getLong(3);
                String dataSource = this.getDataSourceIdToNameMap().get(dataSourceId);

                if (md5 == null || HashUtility.isNoDataMd5(md5)) {
                    continue;
                }

                if (commonFiles.containsKey(md5)) {
                    final CommonAttributeValue commonAttributeValue = commonFiles.get(md5);
                    commonAttributeValue.addInstance(new CaseDBCommonAttributeInstance(objectId, dataSource, caseName, md5));
                } else {
                    final CommonAttributeValue commonAttributeValue = new CommonAttributeValue(md5);
                    commonAttributeValue.addInstance(new CaseDBCommonAttributeInstance(objectId, dataSource, caseName, md5));
                    commonFiles.put(md5, commonAttributeValue);
                }
            }
        }

        Map<Integer, CommonAttributeValueList> instanceCollatedCommonFiles = collateMatchesByNumberOfInstances(commonFiles);

        return new CommonAttributeCountSearchResults(instanceCollatedCommonFiles, this.frequencyPercentageThreshold);
    }

    @Override
    public CommonAttributeCaseSearchResults findMatchesByCase() throws TskCoreException, NoCurrentCaseException, SQLException, CentralRepoException {
        throw new CentralRepoException("Not Supported at the moment");
    }

    /**
     * Should be used by subclasses, in their
     * <code>buildSqlSelectStatement()</code> function to create an SQL boolean
     * expression which will filter our matches based on mime type. The
     * expression will be conjoined to base query with an AND operator.
     *
     * @return sql fragment of the form: 'and "mime_type" in ( [comma delimited
     *         list of mime types] )' or empty string in the event that no types
     *         to filter on were given.
     */
    String determineMimeTypeFilter() {

        Set<String> mimeTypesToFilterOn = new HashSet<>();
        String mimeTypeString = "";
        if (isFilterByMedia()) {
            mimeTypesToFilterOn.addAll(MEDIA_PICS_VIDEO_MIME_TYPES);
        }
        if (isFilterByDoc()) {
            mimeTypesToFilterOn.addAll(TEXT_FILES_MIME_TYPES);
        }
        StringBuilder mimeTypeFilter = new StringBuilder(mimeTypesToFilterOn.size());
        if (!mimeTypesToFilterOn.isEmpty()) {
            for (String mimeType : mimeTypesToFilterOn) {
                mimeTypeFilter.append(SINGLE_QUOTE).append(mimeType).append(SINGLE_QUTOE_COMMA);
            }
            mimeTypeString = mimeTypeFilter.toString().substring(0, mimeTypeFilter.length() - 1);
            mimeTypeString = String.format(FILTER_BY_MIME_TYPES_WHERE_CLAUSE, new Object[]{mimeTypeString});
        }
        return mimeTypeString;
    }
    static final String SINGLE_QUTOE_COMMA = "',";
    static final String SINGLE_QUOTE = "'";
}
