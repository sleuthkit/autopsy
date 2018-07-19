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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Generates a <code>List<CommonFilesMetadata></code> when
 * <code>findCommonFiles()</code> is called, which organizes files by md5 to
 * prepare to display in viewer.
 *
 * This entire thing runs on a background thread where exceptions are handled.
 */
@SuppressWarnings("PMD.AbstractNaming")
public abstract class CommonFilesMetadataBuilder {

    private final Map<Long, String> dataSourceIdToNameMap;
    private final boolean filterByMedia;
    private final boolean filterByDoc;
    private static final String FILTER_BY_MIME_TYPES_WHERE_CLAUSE = " and mime_type in (%s)"; //NON-NLS // where %s is csv list of mime_types to filter on

    /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_MEDIA.
     * ".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff", ".bmp", ".tec"
     * ".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v", //NON-NLS
     * ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv", ".flv", ".swf"
     */
    private static final Set<String> MEDIA_PICS_VIDEO_MIME_TYPES = Stream.of(
            "image/bmp", //NON-NLS
            "image/gif", //NON-NLS
            "image/jpeg", //NON-NLS
            "image/png", //NON-NLS
            "image/tiff", //NON-NLS
            "image/vnd.adobe.photoshop", //NON-NLS
            "image/x-raw-nikon", //NON-NLS
            "image/x-ms-bmp", //NON-NLS
            "image/x-icon", //NON-NLS
            "video/webm", //NON-NLS
            "video/3gpp", //NON-NLS
            "video/3gpp2", //NON-NLS
            "video/ogg", //NON-NLS
            "video/mpeg", //NON-NLS
            "video/mp4", //NON-NLS
            "video/quicktime", //NON-NLS
            "video/x-msvideo", //NON-NLS
            "video/x-flv", //NON-NLS
            "video/x-m4v", //NON-NLS
            "video/x-ms-wmv", //NON-NLS
            "application/vnd.ms-asf", //NON-NLS
            "application/vnd.rn-realmedia", //NON-NLS
            "application/x-shockwave-flash" //NON-NLS
    ).collect(Collectors.toSet());

    /*
     * The set of the MIME types that will be checked for extension mismatches
     * when checkType is ONLY_TEXT_FILES.
     * ".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx"
     * ".txt", ".rtf", ".log", ".text", ".xml"
     * ".html", ".htm", ".css", ".js", ".php", ".aspx"
     * ".pdf"
     */
    private static final Set<String> TEXT_FILES_MIME_TYPES = Stream.of(
            "text/plain", //NON-NLS
            "application/rtf", //NON-NLS
            "application/pdf", //NON-NLS
            "text/css", //NON-NLS
            "text/html", //NON-NLS
            "text/csv", //NON-NLS
            "application/json", //NON-NLS
            "application/javascript", //NON-NLS
            "application/xml", //NON-NLS
            "text/calendar", //NON-NLS
            "application/x-msoffice", //NON-NLS
            "application/x-ooxml", //NON-NLS
            "application/msword", //NON-NLS
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
            "application/vnd.ms-powerpoint", //NON-NLS
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
            "application/vnd.ms-excel", //NON-NLS
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", //NON-NLS
            "application/vnd.oasis.opendocument.presentation", //NON-NLS
            "application/vnd.oasis.opendocument.spreadsheet", //NON-NLS
            "application/vnd.oasis.opendocument.text" //NON-NLS
    ).collect(Collectors.toSet());

    /**
     * Subclass this to implement different algorithms for getting common files.
     *
     * @param dataSourceIdMap a map of obj_id to datasource name
     * @param filterByMediaMimeType match only on files whose mime types can be
     * broadly categorized as media types
     * @param filterByDocMimeType match only on files whose mime types can be
     * broadly categorized as document types
     */
    CommonFilesMetadataBuilder(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType) {
        dataSourceIdToNameMap = dataSourceIdMap;
        filterByMedia = filterByMediaMimeType;
        filterByDoc = filterByDocMimeType;
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
     * format
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     * @throws SQLException
     */
    public CommonFilesMetadata findCommonFiles() throws TskCoreException, NoCurrentCaseException, SQLException {

        Map<String, Md5Metadata> commonFiles = new HashMap<>();

        SleuthkitCase sleuthkitCase = Case.getCurrentCaseThrows().getSleuthkitCase();
        String selectStatement = this.buildSqlSelectStatement();

        try (
                CaseDbQuery query = sleuthkitCase.executeQuery(selectStatement);
                ResultSet resultSet = query.getResultSet()) {

            while (resultSet.next()) {
                Long objectId = resultSet.getLong(1);
                String md5 = resultSet.getString(2);
                Long dataSourceId = resultSet.getLong(3);
                String dataSource = this.dataSourceIdToNameMap.get(dataSourceId);

                if (md5 == null || HashUtility.isNoDataMd5(md5)) {
                    continue;
                }

                if (commonFiles.containsKey(md5)) {
                    final Md5Metadata md5Metadata = commonFiles.get(md5);
                    md5Metadata.addFileInstanceMetadata(new FileInstanceMetadata(objectId, dataSource));
                } else {
                    final List<FileInstanceMetadata> fileInstances = new ArrayList<>();
                    fileInstances.add(new FileInstanceMetadata(objectId, dataSource));
                    Md5Metadata md5Metadata = new Md5Metadata(md5, fileInstances);
                    commonFiles.put(md5, md5Metadata);
                }
            }
        }
        
        Map<Integer, List<Md5Metadata>> instanceCollatedCommonFiles = new TreeMap<>();

        for(Md5Metadata md5Metadata : commonFiles.values()){
            Integer size = md5Metadata.size();
            
            if(instanceCollatedCommonFiles.containsKey(size)){
                instanceCollatedCommonFiles.get(size).add(md5Metadata);
            } else {
                ArrayList<Md5Metadata> value = new ArrayList<>();
                value.add(md5Metadata);
                instanceCollatedCommonFiles.put(size, value);
            }
        }
        
        return new CommonFilesMetadata(instanceCollatedCommonFiles);
    }

    /**
     * Should be used by subclasses, in their 
     * <code>buildSqlSelectStatement()</code> function to create an SQL boolean 
     * expression which will filter our matches based on mime type.  The 
     * expression will be conjoined to base query with an AND operator.
     * 
     * @return sql fragment of the form:
     *      'and "mime_type" in ( [comma delimited list of mime types] )'
     *      or empty string in the event that no types to filter on were given.
     */
    String determineMimeTypeFilter() {

        Set<String> mimeTypesToFilterOn = new HashSet<>();
        String mimeTypeString = "";
        if (filterByMedia) {
            mimeTypesToFilterOn.addAll(MEDIA_PICS_VIDEO_MIME_TYPES);
        }
        if (filterByDoc) {
            mimeTypesToFilterOn.addAll(TEXT_FILES_MIME_TYPES);
        }
        StringBuilder mimeTypeFilter = new StringBuilder(mimeTypesToFilterOn.size());
        if (!mimeTypesToFilterOn.isEmpty()) {
            for (String mimeType : mimeTypesToFilterOn) {
                mimeTypeFilter.append("'").append(mimeType).append("',");
            }
            mimeTypeString = mimeTypeFilter.toString().substring(0, mimeTypeFilter.length() - 1);
            mimeTypeString = String.format(FILTER_BY_MIME_TYPES_WHERE_CLAUSE, new Object[]{mimeTypeString});
        }
        return mimeTypeString;
    }

    @NbBundle.Messages({
        "CommonFilesMetadataBuilder.buildTabTitle.titleAll=Common Files (All Data Sources, %s)",
        "CommonFilesMetadataBuilder.buildTabTitle.titleSingle=Common Files (Match Within Data Source: %s, %s)"
    })
    protected abstract String buildTabTitle();
  
    @NbBundle.Messages({
        "CommonFilesMetadataBuilder.buildCategorySelectionString.doc=Documents",
        "CommonFilesMetadataBuilder.buildCategorySelectionString.media=Media",
        "CommonFilesMetadataBuilder.buildCategorySelectionString.all=All File Categories"
    })

    protected String buildCategorySelectionString() {
        if (!this.filterByDoc && !this.filterByMedia) {
            return Bundle.CommonFilesMetadataBuilder_buildCategorySelectionString_all();
        } else {
            List<String> filters = new ArrayList<>();
            if (this.filterByDoc) {
                filters.add(Bundle.CommonFilesMetadataBuilder_buildCategorySelectionString_doc());
            }
            if (this.filterByMedia) {
                filters.add(Bundle.CommonFilesMetadataBuilder_buildCategorySelectionString_media());
            }
            return String.join(", ", filters);
        }
    }
}
