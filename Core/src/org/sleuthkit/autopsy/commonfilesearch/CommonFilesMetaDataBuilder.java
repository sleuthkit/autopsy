/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Generates a <code>List<CommonFilesMetaData></code> when 
 * <code>findCommonFiles()</code> is called, which
 * organizes files by md5 to prepare to display in viewer.
 *
 * This entire thing runs on a background thread where exceptions are handled.
 */
abstract class CommonFilesMetaDataBuilder {

    private final Map<Long, String> dataSourceIdToNameMap;

    CommonFilesMetaDataBuilder(Map<Long, String> dataSourceIdMap) {
        dataSourceIdToNameMap = dataSourceIdMap;
    }

    /**
     * Use this as a prefix when building the SQL select statement.
     * 
     * <ul>
     * <li>You only have to specify the WHERE clause if you use this.</li>
     * <li>If you do not use this string, you must use at least the columns selected below, in that order.</li>
     * </ul>
     */
    protected static String SELECT_PREFIX = "SELECT obj_id, md5, data_source_obj_id from tsk_files where";
    
    /**
     * Should build a SQL SELECT statement to be passed to
     * SleuthkitCase.executeQuery(sql) which will select the desired 
     * file ids and MD5 hashes.
     * 
     * The statement should select obj_id,  md5, data_source_obj_id in that order.
     *
     * @return sql string select statement
     */
    protected abstract String buildSqlSelectStatement();

    /**
     * Generate a meta data object which encapsulates everything need to 
     * add the tree table tab to the top component.
     * @return a data object with all of the matched files in a hierarchical 
     * format
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException 
     */
    public CommonFilesMetaData findCommonFiles() throws TskCoreException, NoCurrentCaseException, SQLException {
        
        Map<String, Md5MetaData> commonFiles = new HashMap<>();
        
        SleuthkitCase sleuthkitCase = Case.getOpenCase().getSleuthkitCase();
        String selectStatement = this.buildSqlSelectStatement();
        
        try (CaseDbQuery query = sleuthkitCase.executeQuery(selectStatement)){
            ResultSet resultSet = query.getResultSet();
            while(resultSet.next()){
                Long objectId = resultSet.getLong(1);
                String md5 = resultSet.getString(2);
                Long dataSourceId = resultSet.getLong(3);
                String dataSource = this.dataSourceIdToNameMap.get(dataSourceId);
                
                if(commonFiles.containsKey(md5)){
                    final Md5MetaData md5MetaData = commonFiles.get(md5);
                    md5MetaData.addFileInstanceMetaData(new FileInstanceMetaData(objectId, dataSource));
                } else {
                    final List<FileInstanceMetaData> fileInstances = new ArrayList<>();
                    fileInstances.add(new FileInstanceMetaData(objectId, dataSource));
                    Md5MetaData md5MetaData = new Md5MetaData(md5, fileInstances);
                    commonFiles.put(md5, md5MetaData);
                }
            }        
        }
        
        return new CommonFilesMetaData(commonFiles, this.dataSourceIdToNameMap);
    }
}
