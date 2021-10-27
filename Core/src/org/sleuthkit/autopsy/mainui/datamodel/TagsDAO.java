/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import static org.sleuthkit.autopsy.core.UserPreferences.hideKnownFilesInViewsTree;
import static org.sleuthkit.autopsy.core.UserPreferences.hideSlackFilesInViewsTree;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides information to populate the results viewer for data in the views
 * section.
 */
@Messages({"TagsDAO.fileColumns.nameColLbl=Name",
    "TagsDAO.fileColumns.originalName=Original Name",
    "TagsDAO.fileColumns.filePathColLbl=File Path",
    "TagsDAO.fileColumns.commentColLbl=Comment",
    "TagsDAO.fileColumns.modifiedTimeColLbl=Modified Time",
    "TagsDAO.fileColumns.changeTimeColLbl=Changed Time",
    "TagsDAO.fileColumns.accessTimeColLbl=Accessed Time",
    "TagsDAO.fileColumns.createdTimeColLbl=Created Time",
    "TagsDAO.fileColumns.sizeColLbl=Size",
    "TagsDAO.fileColumns.md5HashColLbl=MD5 Hash",
    "TagsDAO.fileColumns.userNameColLbl=User Name",
    "TagsDAO.fileColumns.noDescription=No Description",
    "TagsDAO.tagColumns.sourceNameColLbl=Source Name",
    "TagsDAO.tagColumns.origNameColLbl=Original Name",
    "TagsDAO.tagColumns.sourcePathColLbl=Source File Path",
    "TagsDAO.tagColumns.typeColLbl=Result Type",
    "TagsDAO.tagColumns.commentColLbl=Comment",
    "TagsDAO.tagColumns.userNameColLbl=User Name"})
public class TagsDAO {

    private static final int CACHE_SIZE = 15; // rule of thumb: 5 entries times number of cached SearchParams sub-types
    private static final long CACHE_DURATION = 2;
    private static final TimeUnit CACHE_DURATION_UNITS = TimeUnit.MINUTES;    
    private final Cache<SearchParams<?>, SearchResultsDTO> searchParamsCache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).expireAfterAccess(CACHE_DURATION, CACHE_DURATION_UNITS).build();
    
    private static final String FILE_TAG_TYPE_ID = "FILE_TAG";
    private static final String RESULT_TAG_TYPE_ID = "RESULT_TAG";

    private static final List<ColumnKey> FILE_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.TagsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_originalName()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_filePathColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_commentColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_modifiedTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_changeTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_accessTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_createdTimeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_sizeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_md5HashColLbl()),
            getFileColumnKey(Bundle.TagsDAO_fileColumns_userNameColLbl()));

    private static final List<ColumnKey> RESULT_TAG_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.TagsDAO_tagColumns_sourceNameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_origNameColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_sourcePathColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_typeColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_commentColLbl()),
            getFileColumnKey(Bundle.TagsDAO_tagColumns_userNameColLbl()));

    private static TagsDAO instance = null;

    synchronized static TagsDAO getInstance() {
        if (instance == null) {
            instance = new TagsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.TagsDAO_fileColumns_noDescription());
    }

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }
    
    static ExtensionMediaType getExtensionMediaType(String ext) {
        if (StringUtils.isBlank(ext)) {
            return ExtensionMediaType.UNCATEGORIZED;
        } else {
            ext = "." + ext;
        }
        if (FileTypeExtensions.getImageExtensions().contains(ext)) {
            return ExtensionMediaType.IMAGE;
        } else if (FileTypeExtensions.getVideoExtensions().contains(ext)) {
            return ExtensionMediaType.VIDEO;
        } else if (FileTypeExtensions.getAudioExtensions().contains(ext)) {
            return ExtensionMediaType.AUDIO;
        } else if (FileTypeExtensions.getDocumentExtensions().contains(ext)) {
            return ExtensionMediaType.DOC;
        } else if (FileTypeExtensions.getExecutableExtensions().contains(ext)) {
            return ExtensionMediaType.EXECUTABLE;
        } else if (FileTypeExtensions.getTextExtensions().contains(ext)) {
            return ExtensionMediaType.TEXT;
        } else if (FileTypeExtensions.getWebExtensions().contains(ext)) {
            return ExtensionMediaType.WEB;
        } else if (FileTypeExtensions.getPDFExtensions().contains(ext)) {
            return ExtensionMediaType.PDF;
        } else if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
            return ExtensionMediaType.ARCHIVE;
        } else {
            return ExtensionMediaType.UNCATEGORIZED;
        }
    }
    
    public SearchResultsDTO getFilesByMime(FileTypeMimeSearchParams key, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (key.getMimeType() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }
        
        SearchParams<FileTypeMimeSearchParams> searchParams = new SearchParams<>(key, startItem, maxCount);
        if (hardRefresh) {
            this.searchParamsCache.invalidate(searchParams);
        }

        return searchParamsCache.get(searchParams, () -> fetchMimeSearchResultsDTOs(key.getMimeType(), key.getDataSourceId(), startItem, maxCount));
    }
    
    private String getFileMimeWhereStatement(String mimeType, Long dataSourceId) {

        String whereClause = "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + " AND (type IN ("
                + TskData.TSK_DB_FILES_TYPE_ENUM.FS.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.CARVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE.ordinal() + ","
                + TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL.ordinal()
                + (hideSlackFilesInViewsTree() ? "" : ("," + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.ordinal()))
                + "))"
                + (dataSourceId != null && dataSourceId > 0 ? " AND data_source_obj_id = " + dataSourceId : " ")
                + (hideKnownFilesInViewsTree() ? (" AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")") : "")
                + " AND mime_type = '" + mimeType + "'";
    
        return whereClause;
    }

    @NbBundle.Messages({"FileTag.name.text=File Tag"})
    private SearchResultsDTO fetchMimeSearchResultsDTOs(String mimeType, Long dataSourceId, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileMimeWhereStatement(mimeType, dataSourceId);
        final String FILE_TAG_DISPLAY_NAME = Bundle.FileTag_name_text();
        return fetchFileViewFiles(whereStatement, FILE_TAG_DISPLAY_NAME, startItem, maxResultCount);
    }

    private SearchResultsDTO fetchFileViewFiles(String originalWhereStatement, String displayName, long startItem, Long maxResultCount) throws NoCurrentCaseException, TskCoreException {
        
        // Add offset and/or paging, if specified
        String modifiedWhereStatement = originalWhereStatement 
                + " ORDER BY obj_id ASC"                
                + (maxResultCount != null && maxResultCount > 0 ? " LIMIT " + maxResultCount : "")
                + (startItem > 0 ? " OFFSET " + startItem : "");

        List<AbstractFile> files = getCase().findAllFilesWhere(modifiedWhereStatement);
        
        long totalResultsCount;
        // get total number of results
        if ( (startItem == 0) // offset is zero AND
                && ( (maxResultCount != null && files.size() < maxResultCount) // number of results is less than max
                    || (maxResultCount == null)) ) { // OR max number of results was not specified
                totalResultsCount = files.size();
        } else {
            // do a query to get total number of results
            totalResultsCount = getCase().countFilesWhere(originalWhereStatement);
        }

        List<RowDTO> fileRows = new ArrayList<>();
        for (AbstractFile file : files) {
            
            List<Object> cellValues = Arrays.asList(
                    file.getName(), // GVDTODO handle . and .. from getContentDisplayName()
                    // GVDTODO translation column
                    null,
                    //GVDTDO replace nulls with SCO
                    null,
                    null,
                    null,
                    file.getUniquePath(),
                    TimeZoneUtils.getFormattedTime(file.getMtime()),
                    TimeZoneUtils.getFormattedTime(file.getCtime()),
                    TimeZoneUtils.getFormattedTime(file.getAtime()),
                    TimeZoneUtils.getFormattedTime(file.getCrtime()),
                    file.getSize(),
                    file.getDirFlagAsString(),
                    file.getMetaFlagsAsString(),
                    // mode,
                    // userid,
                    // groupid,
                    // metaAddr,
                    // attrAddr,
                    // typeDir,
                    // typeMeta,

                    file.getKnown().getName(),
                    StringUtils.defaultString(file.getMd5Hash()),
                    StringUtils.defaultString(file.getSha256Hash()),
                    // objectId,

                    StringUtils.defaultString(file.getMIMEType()),
                    file.getNameExtension()
            );

            fileRows.add(new FileRowDTO(
                    file,
                    file.getId(),
                    file.getName(),
                    file.getNameExtension(),
                    getExtensionMediaType(file.getNameExtension()),
                    file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.ALLOC),
                    file.getType(),
                    cellValues));
        }

        return new BaseSearchResultsDTO(FILE_TAG_TYPE_ID, displayName, FILE_TAG_COLUMNS, fileRows, startItem, totalResultsCount);
    }

}
