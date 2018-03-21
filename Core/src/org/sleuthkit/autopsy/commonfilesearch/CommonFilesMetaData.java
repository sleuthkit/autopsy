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
 * Utility and wrapper around data required for Common Files Search results
 */
public class CommonFilesMetaData {

    private final Map<AbstractFile, List<AbstractFile>> parentNodes;
    private final Map<Long, String> dataSourceIdToNameMap;

    private final SleuthkitCase sleuthkitCase;

    CommonFilesMetaData() throws TskCoreException, SQLException, NoCurrentCaseException {
        parentNodes = new HashMap<>();
        dataSourceIdToNameMap = new HashMap<>();

        this.sleuthkitCase = Case.getOpenCase().getSleuthkitCase();

        this.loadDataSourcesMap();

        this.collateFiles();
    }

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

    public Map<AbstractFile, List<AbstractFile>> getFilesMap() {
        return Collections.unmodifiableMap(this.parentNodes);
    }

    public Map<Long, String> getDataSourceIdToNameMap() {
        return Collections.unmodifiableMap(dataSourceIdToNameMap);
    }

    private void collateFiles() throws TskCoreException {

        List<AbstractFile> files = this.sleuthkitCase.findAllFilesWhere(getSqlWhereClause());

        AbstractFile previousFile = null;
        List<AbstractFile> children = new ArrayList<>();

        String previousMd5 = "";

        for (AbstractFile file : files) {

            String currentMd5 = file.getMd5Hash();
            if (currentMd5.equals(previousMd5)) {
                children.add(file);
            } else {
                if (previousFile != null) {
                    this.parentNodes.put(previousFile, children);
                }
                previousFile = file;
                children.clear();
                children.add(file);
            }
        }
    }

    //TODO subclass this type and make this abstract
    protected String getSqlWhereClause() {
        return "md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL) GROUP BY  md5 HAVING  COUNT(*) > 1) order by md5";
    }

    public List<AbstractFile> getChildrenForFile(AbstractFile t) {
        return this.parentNodes.get(t);
    }
}
