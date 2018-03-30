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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility and wrapper around data required for Common Files Search results.
 * Subclass this to implement different selections of files from the case.
 */
public class CommonFilesMetaData {
        
    private final String parentMd5;
    private final List<AbstractFile> children;
    private final Map<Long, String> dataSourceIdToNameMap;

    private final SleuthkitCase sleuthkitCase;

    CommonFilesMetaData(String md5, List<AbstractFile> childNodes) throws TskCoreException, SQLException, NoCurrentCaseException {
        parentMd5 = md5;
        children = childNodes;
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
    public String getMd5() {
        return parentMd5;
    }
    public List<AbstractFile> getChildren() {
        return Collections.unmodifiableList(this.children);
    }

    public Map<Long, String> getDataSourceIdToNameMap() {
        return Collections.unmodifiableMap(dataSourceIdToNameMap);
    }

    public String selectDataSources() {

        Map<Long, String> dataSources = this.getDataSourceIdToNameMap();

        Set<String> dataSourceStrings = new HashSet<>();

        for (AbstractFile child : getChildren()) {

            String dataSource = dataSources.get(child.getDataSourceObjectId());

            dataSourceStrings.add(dataSource);
        }

        return String.join(", ", dataSourceStrings);
    }


}
