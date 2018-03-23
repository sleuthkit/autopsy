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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility and wrapper around data required for Common Files Search results.
 * Subclass this to implement different selections of files from the case.
 */
abstract class CommonFilesMetaData {
        
    private final Map<String, List<AbstractFile>> parentNodes;
    private final Map<Long, String> dataSourceIdToNameMap;

    private final SleuthkitCase sleuthkitCase;

    CommonFilesMetaData() throws TskCoreException, SQLException, NoCurrentCaseException {
        parentNodes = new HashMap<>();
        dataSourceIdToNameMap = new HashMap<>();

        this.sleuthkitCase = Case.getOpenCase().getSleuthkitCase();

        this.loadDataSourcesMap();        
    }

    //TODO chopping block - this will be passed in through the constructor eventually
    private void loadDataSourcesMap() throws SQLException, TskCoreException {

        try (
                SleuthkitCase.CaseDbQuery query = this.sleuthkitCase.executeQuery("select obj_id, name from tsk_files where obj_id in (SELECT obj_id FROM tsk_objects WHERE obj_id in (select obj_id from data_source_info))");
                ResultSet resultSet = query.getResultSet()) {

            while (resultSet.next()) {
                Long objectId = resultSet.getLong(1);
                String dataSourceName = resultSet.getString(2);
                this.dataSourceIdToNameMap.put(objectId, dataSourceName);
            }
        }
    }

    Map<String, List<AbstractFile>> getFilesMap() {
        return Collections.unmodifiableMap(this.parentNodes);
    }

    Map<Long, String> getDataSourceIdToNameMap() {
        return Collections.unmodifiableMap(dataSourceIdToNameMap);
    }

    /**
     * Sorts files in selection into a parent/child hierarchy where actual files
     * are nested beneath a parent node which represents the common match.
     * 
     * @return returns a reference to itself for ease of use.
     * @throws TskCoreException 
     */
    CommonFilesMetaData collateFiles() throws TskCoreException {

        List<AbstractFile> files = this.sleuthkitCase.findAllFilesWhere(getSqlWhereClause());

        for (AbstractFile file : files) {

            String currentMd5 = file.getMd5Hash();
            
            if(parentNodes.containsKey(currentMd5)){
                parentNodes.get(currentMd5).add(file);
            } else {
                List<AbstractFile> children = new ArrayList<>();
                children.add(file);
                parentNodes.put(currentMd5, children);
            }
        }
        
        return this;
    }

    /**
     * Implement this in order to specify which files are selected into this 
     * CommonFilesMetaData and passed along to the view.
     * 
     * No SQL-side de-duping should be performed.  Results should be ordered by MD5.
     * 
     * @return a SQL WHERE clause to be used in common files selection
     */
    protected abstract String getSqlWhereClause();

    /**
     * 
     * @param t
     * @return 
     */
    List<AbstractFile> getChildrenForFile(String md5) {
        return this.parentNodes.get(md5);
    }
}
