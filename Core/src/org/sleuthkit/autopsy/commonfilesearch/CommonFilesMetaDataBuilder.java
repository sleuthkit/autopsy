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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Generates a List<CommonFilesMetaData> when collateFiles() is called, which organizes
 * AbstractFiles by md5 to prepare to display in viewer.
 */
class CommonFilesMetaDataBuilder {

    private final Long selectedDataSourceId;
    private final Map<Long, String> dataSourceIdToNameMap;
    //TODO subclass this class to specify where clause and/or additional algorithms.
    private final String singleDataSourceWhereClause = "md5 in (select md5 from tsk_files where data_source_obj_id=%s and (known != 1 OR known IS NULL) GROUP BY  md5 HAVING  COUNT(*) > 1) AND data_source_obj_id=%s order by md5";
    private final String allDataSourcesWhereClause = "md5 in (select md5 from tsk_files where (known != 1 OR known IS NULL) GROUP BY  md5 HAVING  COUNT(*) > 1) order by md5";

    CommonFilesMetaDataBuilder(Long dataSourceId, Map<Long, String> dataSourceIdMap) {
        selectedDataSourceId = dataSourceId;
        dataSourceIdToNameMap = dataSourceIdMap;
    }

    private void addDataSource(Set<String> dataSources, AbstractFile file, Map<Long, String> dataSourceIdToNameMap) {
        long datasourceId = file.getDataSourceObjectId();
        String dataSourceName = dataSourceIdToNameMap.get(datasourceId);
        dataSources.add(dataSourceName);
    }

    /**
     * Sorts files in selection into a parent/child hierarchy where actual files
     * are nested beneath a parent node which represents the common match.
     *
     * @return returns a reference to itself for ease of use.
     * @throws TskCoreException
     */
    List<CommonFilesMetaData> collateFiles() throws TskCoreException, SQLException {
        List<CommonFilesMetaData> metaDataModels = new ArrayList<>();
        Map<String, Set<String>> md5ToDataSourcesStringMap = new HashMap<>();

        try {
            List<AbstractFile> files = findCommonFiles();

            Map<String, List<AbstractFile>> parentNodes = new HashMap<>();

            collateParentChildRelationships(files, parentNodes, md5ToDataSourcesStringMap);
            for (String key : parentNodes.keySet()) {
                metaDataModels.add(new CommonFilesMetaData(key, parentNodes.get(key), String.join(", ", md5ToDataSourcesStringMap.get(key)), dataSourceIdToNameMap));
            }
        } catch (NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
        }

        return metaDataModels;
    }

    private void collateParentChildRelationships(List<AbstractFile> files, Map<String, List<AbstractFile>> parentNodes, Map<String, Set<String>> md5ToDataSourcesStringMap) {
        for (AbstractFile file : files) {
            
            String currentMd5 = file.getMd5Hash();
            
            if (parentNodes.containsKey(currentMd5)) {
                parentNodes.get(currentMd5).add(file);
                Set<String> currentDataSources = md5ToDataSourcesStringMap.get(currentMd5);
                addDataSource(currentDataSources, file, dataSourceIdToNameMap);
                md5ToDataSourcesStringMap.put(currentMd5, currentDataSources);
                
            } else {
                List<AbstractFile> children = new ArrayList<>();
                Set<String> dataSources = new HashSet<>();
                children.add(file);
                parentNodes.put(currentMd5, children);
                addDataSource(dataSources, file, dataSourceIdToNameMap);
                md5ToDataSourcesStringMap.put(currentMd5, dataSources);
            }
            
        }
    }

    private List<AbstractFile> findCommonFiles() throws TskCoreException, NoCurrentCaseException {
        SleuthkitCase sleuthkitCase;
        sleuthkitCase = Case.getOpenCase().getSleuthkitCase();
        String whereClause = allDataSourcesWhereClause;
        if (selectedDataSourceId != 0L) {
            Object[] args = new String[]{Long.toString(selectedDataSourceId), Long.toString(selectedDataSourceId)};
            whereClause = String.format(singleDataSourceWhereClause, args);
        }
        List<AbstractFile> files = sleuthkitCase.findAllFilesWhere(whereClause);
        return files;
    }

}
