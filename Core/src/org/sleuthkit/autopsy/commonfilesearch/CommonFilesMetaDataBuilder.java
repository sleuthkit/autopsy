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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Generates a List<CommonFilesMetaData> when collateFiles() is called, which
 * organizes AbstractFiles by md5 to prepare to display in viewer.
 *
 * This entire thing runs on a background thread where exceptions are handled.
 */
abstract class CommonFilesMetaDataBuilder {

    private final Map<Long, String> dataSourceIdToNameMap;
    private final boolean filterByMedia;
    private final boolean filterByDoc;
    private final String filterByMimeTypesWhereClause = " and mime_type in (%s)"; // where %s is csv list of mime_types to filter on

     /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_MEDIA.
     */
    private static final Set<String> MEDIA_PICS_VIDEO_MIME_TYPES = Stream.of(
            "image/bmp",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/x-ms-bmp",
            "image/x-icon",
            "video/webm",
            "video/3gpp",
            "video/3gpp2",
            "video/ogg",
            "video/mpeg",
            "video/x-msvideo"
            ).collect(Collectors.toSet());
    
     /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_TEXT_FILES.
     */
    private static final Set<String> TEXT_FILES_MIME_TYPES = Stream.of(
            "text/plain",
            "application/rtf",
            "application/pdf",
            "text/css",
            "text/html",
            "text/csv",
            "application/json",
            "application/javascript",
            "application/xml",
            "text/calendar",
            "application/msword", //NON-NLS
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
            "application/vnd.ms-powerpoint", //NON-NLS
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
            "application/vnd.ms-excel", //NON-NLS
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" //NON-NLS
            ).collect(Collectors.toSet());

    CommonFilesMetaDataBuilder(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType) {
        dataSourceIdToNameMap = dataSourceIdMap;
        filterByMedia = filterByMediaMimeType;
        filterByDoc = filterByDocMimeType;
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

    protected static String SELECT_PREFIX = "SELECT obj_id, md5 from tsk_files where";
    
    /**
     * Should build a SQL SELECT statement to be passed to
     * SleuthkitCase.executeQuery(sql) which will select the desired 
     * file ids and MD5 hashes.
     * 
     * The statement should select obj_id and md5 in that order.
     *
     * @return sql string select statement
     */
    protected abstract String buildSqlSelectStatement();

    private void collateParentChildRelationships(List<AbstractFile> files, Map<String, List<AbstractFile>> parentNodes, Map<String, Set<String>> md5ToDataSourcesStringMap) {
        for (AbstractFile file : files) {

            String currentMd5 = file.getMd5Hash();
            if ((currentMd5 == null) || (HashUtility.isNoDataMd5(currentMd5))) {
                continue;
            }
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

    private Map<String, List<Long>> findCommonFiles() throws TskCoreException, NoCurrentCaseException, SQLException {
        
        Map<String, List<Long>> md5ToObjIdMap = new HashMap<>();
        
        SleuthkitCase sleuthkitCase = Case.getOpenCase().getSleuthkitCase();
        String selectStatement = this.buildSqlSelectStatement();
        
        try (CaseDbQuery query = sleuthkitCase.executeQuery(selectStatement)){
            ResultSet resultSet = query.getResultSet();
            while(resultSet.next()){
                String md5 = resultSet.getString(1);
                Long objectId = resultSet.getLong(2);
                
                if(md5ToObjIdMap.containsKey(md5)){
                    md5ToObjIdMap.get(md5).add(objectId);
                } else {
                    List<Long> objectIds = new ArrayList<>();
                    md5ToObjIdMap.put(md5, objectIds);
                }
            }
        }        
        
        return md5ToObjIdMap;
    }
    
    String determineMimeTypeFilter() {
        StringBuilder mimeTypeFilter = new StringBuilder();
        Set<String> mimeTypesToFilterOn = new HashSet<>();
        String mimeTypeString = "";
        if(filterByMedia) {
            mimeTypesToFilterOn.addAll(MEDIA_PICS_VIDEO_MIME_TYPES);
        }
        if(filterByDoc) {
            mimeTypesToFilterOn.addAll(TEXT_FILES_MIME_TYPES);
        }
        if(mimeTypesToFilterOn.size() > 0) {
           for (String mimeType : mimeTypesToFilterOn) {
               mimeTypeFilter.append("\"" + mimeType + "\",");
           }
           mimeTypeString = mimeTypeFilter.toString().substring(0, mimeTypeFilter.length() - 1);
        }
        return String.format(filterByMimeTypesWhereClause, new Object[]{mimeTypeString});
    }
}
