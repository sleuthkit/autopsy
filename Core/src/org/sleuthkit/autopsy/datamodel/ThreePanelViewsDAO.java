/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.BaseRowResultDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.BaseSearchResultsDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.ColumnKey;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.SearchResultsDTO;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

@Messages({"ThreePanelViewsDAO.fileColumns.nameColLbl=Name",
    "ThreePanelViewsDAO.fileColumns.originalName=Original Name",
    "ThreePanelViewsDAO.fileColumns.scoreName=S",
    "ThreePanelViewsDAO.fileColumns.commentName=C",
    "ThreePanelViewsDAO.fileColumns.countName=O",
    "ThreePanelViewsDAO.fileColumns.locationColLbl=Location",
    "ThreePanelViewsDAO.fileColumns.modifiedTimeColLbl=Modified Time",
    "ThreePanelViewsDAO.fileColumns.changeTimeColLbl=Change Time",
    "ThreePanelViewsDAO.fileColumns.accessTimeColLbl=Access Time",
    "ThreePanelViewsDAO.fileColumns.createdTimeColLbl=Created Time",
    "ThreePanelViewsDAO.fileColumns.sizeColLbl=Size",
    "ThreePanelViewsDAO.fileColumns.flagsDirColLbl=Flags(Dir)",
    "ThreePanelViewsDAO.fileColumns.flagsMetaColLbl=Flags(Meta)",
    "ThreePanelViewsDAO.fileColumns.modeColLbl=Mode",
    "ThreePanelViewsDAO.fileColumns.useridColLbl=UserID",
    "ThreePanelViewsDAO.fileColumns.groupidColLbl=GroupID",
    "ThreePanelViewsDAO.fileColumns.metaAddrColLbl=Meta Addr.",
    "ThreePanelViewsDAO.fileColumns.attrAddrColLbl=Attr. Addr.",
    "ThreePanelViewsDAO.fileColumns.typeDirColLbl=Type(Dir)",
    "ThreePanelViewsDAO.fileColumns.typeMetaColLbl=Type(Meta)",
    "ThreePanelViewsDAO.fileColumns.knownColLbl=Known",
    "ThreePanelViewsDAO.fileColumns.md5HashColLbl=MD5 Hash",
    "ThreePanelViewsDAO.fileColumns.sha256HashColLbl=SHA-256 Hash",
    "ThreePanelViewsDAO.fileColumns.objectId=Object ID",
    "ThreePanelViewsDAO.fileColumns.mimeType=MIME Type",
    "ThreePanelViewsDAO.fileColumns.extensionColLbl=Extension",
    "ThreePanelViewsDAO.fileColumns.noDescription=No Description"})
public class ThreePanelViewsDAO {

    private static final String FILE_VIEW_EXT_TYPE_ID = "FILE_VIEW_BY_EXT";
    
    private static final List<ColumnKey> FILE_COLUMNS = Arrays.asList(
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_nameColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_originalName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_scoreName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_commentName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_countName()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_locationColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_modifiedTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_changeTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_accessTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_createdTimeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_sizeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_flagsDirColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_flagsMetaColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_modeColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_useridColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_groupidColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_metaAddrColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_attrAddrColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_typeDirColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_typeMetaColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_knownColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_md5HashColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_sha256HashColLbl()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_objectId()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_mimeType()),
            getFileColumnKey(Bundle.ThreePanelViewsDAO_fileColumns_extensionColLbl()));

    private static ThreePanelViewsDAO instance = null;

    public synchronized static ThreePanelViewsDAO getInstance() {
        if (instance == null) {
            instance = new ThreePanelViewsDAO();
        }

        return instance;
    }

    private static ColumnKey getFileColumnKey(String name) {
        return new ColumnKey(name, name, Bundle.ThreePanelViewsDAO_fileColumns_noDescription());
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

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    private final Cache<FileTypeExtensionsKeyv2, SearchResultsDTO<FileRowDTO>> fileTypeByExtensionCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    
    public SearchResultsDTO<FileRowDTO> getFilesByExtension(FileTypeExtensionsKeyv2 key) throws ExecutionException, IllegalArgumentException {
        if (key.getFilter() == null) {
            throw new IllegalArgumentException("Must have non-null filter");
        } else if (key.getDataSourceId() != null && key.getDataSourceId() <= 0) {
            throw new IllegalArgumentException("Data source id must be greater than 0 or null");
        }
        
        return fileTypeByExtensionCache.get(key, () -> fetchFileViewFiles(key.getFilter(), key.getDataSourceId(), key.isKnownShown()));
    }
    
//    private ViewFileTableSearchResultsDTO fetchFilesForTable(ViewFileCacheKey cacheKey) throws NoCurrentCaseException, TskCoreException {
//
//    }
//
//    public ViewFileTableSearchResultsDTO getFilewViewForTable(BlackboardArtifact.Type artType, Long dataSourceId) throws ExecutionException, IllegalArgumentException {
//        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT) {
//            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
//                    + "Artifact type must be non-null and data artifact.  "
//                    + "Received {0}", artType));
//        }
//
//        ViewFileCacheKey cacheKey = new ViewFileCacheKey(artType, dataSourceId);
//        return dataArtifactCache.get(cacheKey, () -> fetchFilesForTable(cacheKey));
//    }
    private Map<Integer, Long> fetchFileViewCounts(List<SearchFilterInterface> filters, Long dataSourceId, boolean showKnown) throws NoCurrentCaseException, TskCoreException {
        Map<Integer, Long> counts = new HashMap<>();
        for (SearchFilterInterface filter : filters) {
            String whereClause = getFileWhereStatement(filter, dataSourceId, showKnown);
            long count = getCase().countFilesWhere(whereClause);
            counts.put(filter.getId(), count);
        }

        return counts;
    }

    private String getFileWhereStatement(SearchFilterInterface filter, Long dataSourceId, boolean showKnown) {
        String whereClause = "(dir_type = " + TskData.TSK_FS_NAME_TYPE_ENUM.REG.getValue() + ")"
                + (showKnown
                        ? " "
                        : " AND (known IS NULL OR known != " + TskData.FileKnown.KNOWN.getFileKnownValue() + ")")
                + (dataSourceId != null && dataSourceId > 0
                        ? " AND data_source_obj_id = " + dataSourceId
                        : " ")
                + " AND (extension IN (" + filter.getFilter().stream()
                        .map(String::toLowerCase)
                        .map(s -> "'" + StringUtils.substringAfter(s, ".") + "'")
                        .collect(Collectors.joining(", ")) + "))";
        return whereClause;
    }

    private SearchResultsDTO<FileRowDTO> fetchFileViewFiles(SearchFilterInterface filter, Long dataSourceId, boolean showKnown) throws NoCurrentCaseException, TskCoreException {
        String whereStatement = getFileWhereStatement(filter, dataSourceId, showKnown);
        List<AbstractFile> files = getCase().findAllFilesWhere(whereStatement);

        List<FileRowDTO> fileRows = new ArrayList<>();
        for (AbstractFile file : files) {

            boolean encryptionDetected = FileTypeExtensions.getArchiveExtensions().contains("." + file.getNameExtension().toLowerCase())
                    && file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0;

            List<Object> cellValues = Arrays.asList(
                    file.getName(), // GVDTODO handle . and .. from getContentDisplayName()
                    // GVDTODO translation column
                    //GVDTDO replace nulls with SCO
                    null,
                    null,
                    null,
                    TimeZoneUtils.getFormattedTime(file.getMtime()),
                    TimeZoneUtils.getFormattedTime(file.getCtime()),
                    TimeZoneUtils.getFormattedTime(file.getAtime()),
                    TimeZoneUtils.getFormattedTime(file.getCrtime()),
                    file.getSize(),
                    file.getDirFlagAsString(),
                    file.getMetaFlagsAsString(),
                    file.getKnown().getName(),
                    file.getUniquePath(),
                    StringUtils.defaultString(file.getMd5Hash()),
                    StringUtils.defaultString(file.getSha256Hash()),
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
                    encryptionDetected,
                    cellValues));
        }

        return new BaseSearchResultsDTO<>(FILE_VIEW_EXT_TYPE_ID, filter.getDisplayName(), FILE_COLUMNS, fileRows);
    }

    // root node filters
    @NbBundle.Messages({"FileTypeExtensionFilters.tskDatabaseFilter.text=Databases"})
    public static enum RootFilter implements SearchFilterInterface {

        TSK_IMAGE_FILTER(0, "TSK_IMAGE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskImgFilter.text"),
                FileTypeExtensions.getImageExtensions()),
        TSK_VIDEO_FILTER(1, "TSK_VIDEO_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskVideoFilter.text"),
                FileTypeExtensions.getVideoExtensions()),
        TSK_AUDIO_FILTER(2, "TSK_AUDIO_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskAudioFilter.text"),
                FileTypeExtensions.getAudioExtensions()),
        TSK_ARCHIVE_FILTER(3, "TSK_ARCHIVE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskArchiveFilter.text"),
                FileTypeExtensions.getArchiveExtensions()),
        TSK_DATABASE_FILTER(4, "TSK_DATABASE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskDatabaseFilter.text"),
                FileTypeExtensions.getDatabaseExtensions()),
        TSK_DOCUMENT_FILTER(5, "TSK_DOCUMENT_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskDocumentFilter.text"),
                Arrays.asList(".htm", ".html", ".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf", ".txt", ".rtf")), //NON-NLS
        TSK_EXECUTABLE_FILTER(6, "TSK_EXECUTABLE_FILTER", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.tskExecFilter.text"),
                FileTypeExtensions.getExecutableExtensions()); //NON-NLS

        private final int id;
        private final String name;
        private final String displayName;
        private final List<String> filter;

        private RootFilter(int id, String name, String displayName, List<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public List<String> getFilter() {
            return Collections.unmodifiableList(this.filter);
        }
    }

// document sub-node filters
    public static enum DocumentFilter implements SearchFilterInterface {

        AUT_DOC_HTML(0, "AUT_DOC_HTML", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocHtmlFilter.text"),
                Arrays.asList(".htm", ".html")), //NON-NLS
        AUT_DOC_OFFICE(1, "AUT_DOC_OFFICE", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocOfficeFilter.text"),
                Arrays.asList(".doc", ".docx", ".odt", ".xls", ".xlsx", ".ppt", ".pptx")), //NON-NLS
        AUT_DOC_PDF(2, "AUT_DOC_PDF", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autoDocPdfFilter.text"),
                Arrays.asList(".pdf")), //NON-NLS
        AUT_DOC_TXT(3, "AUT_DOC_TXT", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocTxtFilter.text"),
                Arrays.asList(".txt")), //NON-NLS
        AUT_DOC_RTF(4, "AUT_DOC_RTF", //NON-NLS
                NbBundle.getMessage(FileTypesByExtension.class, "FileTypeExtensionFilters.autDocRtfFilter.text"),
                Arrays.asList(".rtf")); //NON-NLS

        private final int id;
        private final String name;
        private final String displayName;
        private final List<String> filter;

        private DocumentFilter(int id, String name, String displayName, List<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public List<String> getFilter() {
            return Collections.unmodifiableList(this.filter);
        }
    }

// executable sub-node filters
    public static enum ExecutableFilter implements SearchFilterInterface {

        ExecutableFilter_EXE(0, "ExecutableFilter_EXE", ".exe", Arrays.asList(".exe")), //NON-NLS
        ExecutableFilter_DLL(1, "ExecutableFilter_DLL", ".dll", Arrays.asList(".dll")), //NON-NLS
        ExecutableFilter_BAT(2, "ExecutableFilter_BAT", ".bat", Arrays.asList(".bat")), //NON-NLS
        ExecutableFilter_CMD(3, "ExecutableFilter_CMD", ".cmd", Arrays.asList(".cmd")), //NON-NLS
        ExecutableFilter_COM(4, "ExecutableFilter_COM", ".com", Arrays.asList(".com")); //NON-NLS

        private final int id;
        private final String name;
        private final String displayName;
        private final List<String> filter;

        private ExecutableFilter(int id, String name, String displayName, List<String> filter) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
            this.filter = filter;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getId() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return this.displayName;
        }

        @Override
        public List<String> getFilter() {
            return Collections.unmodifiableList(this.filter);
        }
    }

    public interface SearchFilterInterface {

        public String getName();

        public int getId();

        public String getDisplayName();

        public List<String> getFilter();

    }

    public enum ExtensionMediaType {
        IMAGE,
        VIDEO,
        AUDIO,
        DOC,
        EXECUTABLE,
        TEXT,
        WEB,
        PDF,
        ARCHIVE,
        UNCATEGORIZED
    }

    public static class FileRowDTO extends BaseRowResultDTO {

        private final AbstractFile abstractFile;
        private final String fileName;

        private final String extension;
        private final ExtensionMediaType extensionMediaType;

        private final boolean allocated;
        private final TskData.TSK_DB_FILES_TYPE_ENUM fileType;
        private final boolean encryptionDetected;

        public FileRowDTO(AbstractFile abstractFile, long id, String fileName,
                String extension, ExtensionMediaType extensionMediaType,
                boolean allocated,
                TskData.TSK_DB_FILES_TYPE_ENUM fileType,
                boolean encryptionDetected, List<Object> cellValues) {
            super(cellValues, id);
            this.abstractFile = abstractFile;
            this.fileName = fileName;
            this.extension = extension;
            this.extensionMediaType = extensionMediaType;
            this.allocated = allocated;
            this.fileType = fileType;
            this.encryptionDetected = encryptionDetected;
        }

        public ExtensionMediaType getExtensionMediaType() {
            return extensionMediaType;
        }

        public boolean getAllocated() {
            return allocated;
        }

        public TskData.TSK_DB_FILES_TYPE_ENUM getFileType() {
            return fileType;
        }

        public AbstractFile getAbstractFile() {
            return abstractFile;
        }

        public String getExtension() {
            return extension;
        }

        public boolean isEncryptionDetected() {
            return encryptionDetected;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
