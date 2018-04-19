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
 * Generates a <code>List<CommonFilesMetaData></code> when
 * <code>findCommonFiles()</code> is called, which organizes files by md5 to
 * prepare to display in viewer.
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
     * ".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff", ".bmp", ".tec"
     * ".aaf", ".3gp", ".asf", ".avi", ".m1v", ".m2v", //NON-NLS
     * ".m4v", ".mp4", ".mov", ".mpeg", ".mpg", ".mpe", ".mp4", ".rm", ".wmv", ".mpv", ".flv", ".swf"
     */
    private static final Set<String> MEDIA_PICS_VIDEO_MIME_TYPES = Stream.of(
            "image/bmp",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/tiff",
            "image/vnd.adobe.photoshop",
            "image/x-raw-nikon",
            "image/x-ms-bmp",
            "image/x-icon",
            "video/webm",
            "video/3gpp",
            "video/3gpp2",
            "video/ogg",
            "video/mpeg",
            "video/mp4",
            "video/quicktime",
            "video/x-msvideo",
            "video/x-flv",
            "video/x-m4v",
            "video/x-ms-wmv",
            "application/vnd.ms-asf",
            "application/vnd.rn-realmedia",
            "application/x-shockwave-flash"
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
            "application/x-msoffice",
            "application/x-ooxml",
            "application/msword", //NON-NLS
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
            "application/vnd.ms-powerpoint", //NON-NLS
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
            "application/vnd.ms-excel", //NON-NLS
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.text"//NON-NLS
    ).collect(Collectors.toSet());

    CommonFilesMetaDataBuilder(Map<Long, String> dataSourceIdMap, boolean filterByMediaMimeType, boolean filterByDocMimeType) {
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
    static final String SELECT_PREFIX = "SELECT obj_id, md5, data_source_obj_id from tsk_files where";

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
    public CommonFilesMetaData findCommonFiles() throws TskCoreException, NoCurrentCaseException, SQLException {

        Map<String, Md5MetaData> commonFiles = new HashMap<>();

        SleuthkitCase sleuthkitCase = Case.getOpenCase().getSleuthkitCase();
        String selectStatement = this.buildSqlSelectStatement();

        try (CaseDbQuery query = sleuthkitCase.executeQuery(selectStatement)) {
            ResultSet resultSet = query.getResultSet();
            while (resultSet.next()) {
                Long objectId = resultSet.getLong(1);
                String md5 = resultSet.getString(2);
                Long dataSourceId = resultSet.getLong(3);
                String dataSource = this.dataSourceIdToNameMap.get(dataSourceId);

                if (md5 == null || HashUtility.isNoDataMd5(md5)) {
                    continue;
                }

                if (commonFiles.containsKey(md5)) {
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

        return new CommonFilesMetaData(commonFiles);
    }

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
        if (mimeTypesToFilterOn.size() > 0) {
            for (String mimeType : mimeTypesToFilterOn) {
                mimeTypeFilter.append("\"").append(mimeType).append("\",");
            }
            mimeTypeString = mimeTypeFilter.toString().substring(0, mimeTypeFilter.length() - 1);
            mimeTypeString = String.format(filterByMimeTypesWhereClause, new Object[]{mimeTypeString});
        }
        return mimeTypeString;
    }

    @NbBundle.Messages({
        "CommonFilesMetaDataBuilder.buildTabTitle.titleAll=Common Files (All Data Sources, %s)",
        "CommonFilesMetaDataBuilder.buildTabTitle.titleSingle=Common Files (Match Within Data Source: %s, %s)"
    })
    protected abstract String buildTabTitle();

    @NbBundle.Messages({
        "CommonFilesMetaDataBuilder.buildCategorySelectionString.doc=Documents",
        "CommonFilesMetaDataBuilder.buildCategorySelectionString.media=Media",
        "CommonFilesMetaDataBuilder.buildCategorySelectionString.all=All File Categories"
    })
    protected String buildCategorySelectionString() {
        if (!this.filterByDoc && !this.filterByMedia) {
            return Bundle.CommonFilesMetaDataBuilder_buildCategorySelectionString_all();
        } else {
            List<String> filters = new ArrayList<>();
            if (this.filterByDoc) {
                filters.add(Bundle.CommonFilesMetaDataBuilder_buildCategorySelectionString_doc());
            }
            if (this.filterByMedia) {
                filters.add(Bundle.CommonFilesMetaDataBuilder_buildCategorySelectionString_media());
            }
            return String.join(", ", filters);
        }
    }
}
