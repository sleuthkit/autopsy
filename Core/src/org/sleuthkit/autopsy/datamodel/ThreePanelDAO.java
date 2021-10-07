/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author gregd
 */
    @Messages({
        "ThreePanelDAO.dataArtifact.columnKeys.srcFile.name=Source Name",
        "ThreePanelDAO.dataArtifact.columnKeys.srcFile.displayName=Source Name",
        "ThreePanelDAO.dataArtifact.columnKeys.srcFile.description=Source Name",
        
        "ThreePanelDAO.dataArtifact.columnKeys.score.name=Score",
        "ThreePanelDAO.dataArtifact.columnKeys.score.displayName=S",
        "ThreePanelDAO.dataArtifact.columnKeys.score.description=Score",
        
        "ThreePanelDAO.dataArtifact.columnKeys.comment.name=Comment",
        "ThreePanelDAO.dataArtifact.columnKeys.comment.displayName=C",
        "ThreePanelDAO.dataArtifact.columnKeys.comment.description=Comment",
        
        "ThreePanelDAO.dataArtifact.columnKeys.occurrences.name=Occurrences",
        "ThreePanelDAO.dataArtifact.columnKeys.occurrences.displayName=O",
        "ThreePanelDAO.dataArtifact.columnKeys.occurrences.description=Occurrences",

        "ThreePanelDAO.dataArtifact.columnKeys.dataSource.name=Data Source",
        "ThreePanelDAO.dataArtifact.columnKeys.dataSource.displayName=Data Source",
        "ThreePanelDAO.dataArtifact.columnKeys.dataSource.description=Data Source"
    })
public class ThreePanelDAO {

    // GVDTODO there is a different standard for normal attr strings and email attr strings
    private static final int STRING_LENGTH_MAX = 160;
    private static final String ELLIPSIS = "...";

    @SuppressWarnings("deprecation")
    private static final Set<Integer> HIDDEN_ATTR_TYPES = ImmutableSet.of(
            ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_ASSOCIATED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_SET_NAME.getTypeID(),
            BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID()
    );
    private static final Set<Integer> HIDDEN_EMAIL_ATTR_TYPES = ImmutableSet.of(
            BlackboardAttribute.Type.TSK_DATETIME_SENT.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CONTENT_HTML.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CONTENT_RTF.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_BCC.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CC.getTypeID(),
            BlackboardAttribute.Type.TSK_HEADERS.getTypeID()
    );
    
    private static final ColumnKey SRC_FILE_COL = new ColumnKey(
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_srcFile_name(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_srcFile_displayName(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_srcFile_description()
    );

    private static final ColumnKey S_COL = new ColumnKey(
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_score_name(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_score_displayName(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_score_description()
    );

    private static final ColumnKey C_COL = new ColumnKey(
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_comment_name(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_comment_displayName(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_comment_description()
    );

    private static final ColumnKey O_COL = new ColumnKey(
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_occurrences_name(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_occurrences_displayName(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_occurrences_description()
    );

    private static final ColumnKey DATASOURCE_COL = new ColumnKey(
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_dataSource_name(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_dataSource_displayName(), 
        Bundle.ThreePanelDAO_dataArtifact_columnKeys_dataSource_description()
    );
    
    
    private static ThreePanelDAO instance = null;

    public synchronized static ThreePanelDAO getInstance() {
        if (instance == null) {
            instance = new ThreePanelDAO();
        }

        return instance;
    }

    private final Cache<DataArtifactCacheKey, DataArtifactTableSearchResultsDTO> dataArtifactCache = CacheBuilder.newBuilder().maximumSize(1000).build();
//    private final Cache<Long, List<FilesContentTableDTO>> filesCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    private DataArtifactTableSearchResultsDTO fetchDataArtifactsForTable(DataArtifactCacheKey cacheKey) throws NoCurrentCaseException, TskCoreException {
        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();

        Long dataSourceId = cacheKey.getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getArtifactType();

        // get data artifacts
        List<DataArtifact> arts = (dataSourceId != null)
                ? blackboard.getDataArtifacts(artType.getTypeID(), dataSourceId)
                : blackboard.getDataArtifacts(artType.getTypeID());

        Map<Long, Map<BlackboardAttribute.Type, Object>> artifactAttributes = new HashMap<>();
        for (DataArtifact art : arts) {
            Map<BlackboardAttribute.Type, Object> attrs = art.getAttributes().stream()
                    .filter(attr -> isRenderedAttr(artType, attr.getAttributeType()))
                    .collect(Collectors.toMap(attr -> attr.getAttributeType(), attr -> getAttrValue(attr)));

            artifactAttributes.put(art.getId(), attrs);
        }

        // NOTE: this has to be in the same order as values are added
        List<BlackboardAttribute.Type> attributeTypeKeys = artifactAttributes.values().stream()
                .flatMap(attrs -> attrs.keySet().stream())
                .distinct()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        List<ColumnKey> columnKeys = new ArrayList<>();
        columnKeys.add(SRC_FILE_COL);
        // GVDTODO translated file name
        columnKeys.add(S_COL);
        // GVDTODO only show if central repository enabled
        columnKeys.add(C_COL);
        columnKeys.add(O_COL);
        columnKeys.addAll(attributeTypeKeys.stream()
                .map(attrType -> new ColumnKey(attrType.getTypeName(), attrType.getDisplayName(), attrType.getDisplayName()))
                .collect(Collectors.toList()));
        columnKeys.add(DATASOURCE_COL);

        // determine all different attribute types present as well as row data for each artifact
        List<DataArtifactTableDTO> rows = new ArrayList<>();

        for (DataArtifact artifact : arts) {
            List<Object> cellValues = new ArrayList<>();

            Content srcContent = artifact.getParent();
            cellValues.add(srcContent.getName());
            // GVDTODO handle translated filename here
            // GVDTODO handle SCO
            cellValues.add(null);
            cellValues.add(null);
            cellValues.add(null);

            long id = artifact.getId();
            Map<BlackboardAttribute.Type, Object> attrValues = artifactAttributes.getOrDefault(id, Collections.emptyMap());
            // NOTE: this has to be in the same order as attribute keys
            for (BlackboardAttribute.Type colAttrType : attributeTypeKeys) {
                cellValues.add(attrValues.get(colAttrType));
            }

            String dataSourceName = getDataSourceName(srcContent);
            cellValues.add(dataSourceName);

            Object linkedId = attrValues.get(BlackboardAttribute.Type.TSK_PATH_ID.getTypeName());
            AbstractFile linkedFile = linkedId instanceof Long && ((Long) linkedId) >= 0
                    ? skCase.getAbstractFileById((Long) linkedId)
                    : null;

            boolean isTimelineSupported = isTimelineSupported(attrValues.keySet());

            rows.add(new DataArtifactTableDTO(artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id));
        }

        return new DataArtifactTableSearchResultsDTO(artType, columnKeys, rows);
    }

    private boolean isRenderedAttr(BlackboardArtifact.Type artType, BlackboardAttribute.Type attrType) {
        if (BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() == artType.getTypeID()) {
            return !HIDDEN_EMAIL_ATTR_TYPES.contains(attrType);
        } else {
            return !HIDDEN_ATTR_TYPES.contains(attrType);
        }
    }

    private String getTruncated(String str) {
        return str.length() > STRING_LENGTH_MAX
                ? str.substring(0, STRING_LENGTH_MAX) + ELLIPSIS
                : str;
    }

    private boolean isTimelineSupported(Collection<BlackboardAttribute.Type> attrTypes) {
        return attrTypes.stream()
                .anyMatch(tp -> BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME.equals(tp.getValueType()));
    }

    private String getDataSourceName(Content srcContent) throws TskCoreException {
        Content dataSource = srcContent.getDataSource();
        if (dataSource != null) {
            return dataSource.getName();
        } else {
            return getRootAncestorName(srcContent);
        }
    }

    /**
     * Gets the name of the root ancestor of the source content for the artifact
     * represented by this node.
     *
     * @param srcContent The source content.
     *
     * @return The root ancestor name or the empty string if an error occurs.
     */
    private String getRootAncestorName(Content srcContent) throws TskCoreException {
        String parentName = srcContent.getName();
        Content parent = srcContent;

        while ((parent = parent.getParent()) != null) {
            parentName = parent.getName();
        }
        return parentName;
    }

    /**
     * Returns a displayable type string for the given content object.
     *
     * If the content object is a artifact of a custom type then this method may
     * cause a DB call BlackboardArtifact.getType
     *
     * @param source The object to determine the type of.
     *
     * @return A string representing the content type.
     */
//    private String getSourceObjType(Content source) throws TskCoreException {
//        if (source instanceof BlackboardArtifact) {
//            BlackboardArtifact srcArtifact = (BlackboardArtifact) source;
//            return srcArtifact.getType().getDisplayName();
//        } else if (source instanceof Volume) {
//            return TskData.ObjectType.VOL.toString();
//        } else if (source instanceof AbstractFile) {
//            return TskData.ObjectType.ABSTRACTFILE.toString();
//        } else if (source instanceof Image) {
//            return TskData.ObjectType.IMG.toString();
//        } else if (source instanceof VolumeSystem) {
//            return TskData.ObjectType.VS.toString();
//        } else if (source instanceof OsAccount) {
//            return TskData.ObjectType.OS_ACCOUNT.toString();
//        } else if (source instanceof HostAddress) {
//            return TskData.ObjectType.HOST_ADDRESS.toString();
//        } else if (source instanceof Pool) {
//            return TskData.ObjectType.POOL.toString();
//        }
//        return "";
//    }
    private Object getAttrValue(BlackboardAttribute attr) {
        switch (attr.getAttributeType().getValueType()) {
            case BYTE:
                return attr.getValueBytes();
            case DATETIME:
                return new Date(attr.getValueLong() * 1000);
            case DOUBLE:
                return attr.getValueDouble();
            case INTEGER:
                return attr.getValueInt();
            case JSON:
                return getTruncated(attr.getValueString());
            case LONG:
                return attr.getValueLong();
            case STRING:
                return getTruncated(attr.getValueString());
            default:
                throw new IllegalArgumentException("Unknown attribute type value type: " + attr.getAttributeType().getValueType());
        }
    }

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(BlackboardArtifact.Type artType, Long dataSourceId) throws ExecutionException, IllegalArgumentException {
        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  "
                    + "Received {0}", artType));
        }

        DataArtifactCacheKey cacheKey = new DataArtifactCacheKey(artType, dataSourceId);
        return dataArtifactCache.get(cacheKey, () -> fetchDataArtifactsForTable(cacheKey));
    }

    public void dropDataArtifactCache() {
        dataArtifactCache.invalidateAll();
    }

    public void dropDataArtifactCache(BlackboardArtifact.Type artType) {
        dataArtifactCache.invalidate(artType);
    }

    // GVDTODO: incomplete code
//    private List<FilesContentTableDTO> fetchChildFiles(long parentId) throws NoCurrentCaseException, TskCoreException {
//        Content parentContent = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(parentId);
//        List<Content> childContent = parentContent.getChildren();
//        List<FilesContentTableDTO> toRet = childContent.stream().map(c -> new FilesContentTableDTO(c, c.getName(),))
//    }
//
//    static FileCategory getFileCategory(AbstractFile file) {
//        String ext = file.getNameExtension();
//        if (StringUtils.isBlank(ext)) {
//            return FileCategory.UNKNOWN;
//        } else {
//            ext = "." + ext;
//        }
//        if (FileTypeExtensions.getImageExtensions().contains(ext)) {
//            return FileCategory.IMAGE;
//        } else if (FileTypeExtensions.getVideoExtensions().contains(ext)) {
//            return FileCategory.VIDEO;
//        } else if (FileTypeExtensions.getAudioExtensions().contains(ext)) {
//            return FileCategory.AUDIO;
//        } else if (FileTypeExtensions.getDocumentExtensions().contains(ext)) {
//            return FileCategory.DOCUMENT;
//        } else if (FileTypeExtensions.getExecutableExtensions().contains(ext)) {
//            return FileCategory.EXECUTABLE;
//        } else if (FileTypeExtensions.getTextExtensions().contains(ext)) {
//            return FileCategory.TEXT;
//        } else if (FileTypeExtensions.getWebExtensions().contains(ext)) {
//            return FileCategory.WEB;
//        } else if (FileTypeExtensions.getPDFExtensions().contains(ext)) {
//            return FileCategory.PDF;
//        } else if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
//            return FileCategory.ARCHIVE;
//        } else {
//            return FileCategory.NONE;
//        }
//    }
//
//    enum FileCategory {
//        NONE, UNKNOWN, IMAGE, VIDEO, AUDIO, DOCUMENT, EXECUTABLE, TEXT, WEB, PDF, ARCHIVE
//    }
//
//    public List<FilesContentTableDTO> getChildFilesForTable(long parentId) throws ExecutionException, IllegalArgumentException {
//        if (parentId <= 0) {
//            throw new IllegalArgumentException("parent id must be > 0.");
//        }
//        return filesCache.get(parentId, () -> fetchChildFiles(parentId));
//    }
//    public void dropFilesCache() {
//        filesCache.invalidateAll();
//    }
//
//    public void dropFilesCache(long parentId) {
//        filesCache.invalidate(parentId);
//    }
    private static class DataArtifactCacheKey {

        private final BlackboardArtifact.Type artifactType;
        private final Long dataSourceId;

        public DataArtifactCacheKey(BlackboardArtifact.Type artifactType, Long dataSourceId) {
            this.artifactType = artifactType;
            this.dataSourceId = dataSourceId;
        }

        public BlackboardArtifact.Type getArtifactType() {
            return artifactType;
        }

        public Long getDataSourceId() {
            return dataSourceId;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + Objects.hashCode(this.artifactType);
            hash = 47 * hash + Objects.hashCode(this.dataSourceId);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final DataArtifactCacheKey other = (DataArtifactCacheKey) obj;
            if (!Objects.equals(this.artifactType, other.artifactType)) {
                return false;
            }
            if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
                return false;
            }
            return true;
        }

    }

    public static class DataArtifactTableDTO extends BaseRowResultDTO {

        //private final Map<Integer, Object> attributeValues;
        //private final String dataSourceName;
        private final DataArtifact dataArtifact;
        private final Content srcContent;
        private final Content linkedFile;
        private final boolean isTimelineSupported;

        public DataArtifactTableDTO(DataArtifact dataArtifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) {
            super(cellValues, id);
            this.dataArtifact = dataArtifact;
            this.srcContent = srcContent;
            this.linkedFile = linkedFile;
            this.isTimelineSupported = isTimelineSupported;
        }

        public DataArtifact getDataArtifact() {
            return dataArtifact;
        }

        public Content getSrcContent() {
            return srcContent;
        }

        public Content getLinkedFile() {
            return linkedFile;
        }

        public boolean isIsTimelineSupported() {
            return isTimelineSupported;
        }

    }

    public static class ColumnKey {

        private final String fieldName;
        private final String displayName;
        private final String description;

        public ColumnKey(String fieldName, String displayName, String description) {
            this.fieldName = fieldName;
            this.displayName = displayName;
            this.description = description;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public interface RowResultDTO {

        List<Object> getCellValues();

        long getId();
    }

    public class BaseRowResultDTO implements RowResultDTO {

        private final List<Object> cellValues;
        private final long id;

        public BaseRowResultDTO(List<Object> cellValues, long id) {
            this.cellValues = cellValues;
            this.id = id;
        }

        @Override
        public List<Object> getCellValues() {
            return cellValues;
        }

        @Override
        public long getId() {
            return id;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 23 * hash + (int) (this.id ^ (this.id >>> 32));
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BaseRowResultDTO other = (BaseRowResultDTO) obj;
            if (this.id != other.id) {
                return false;
            }
            return true;
        }

    }

    public interface SearchResultsDTO<R extends RowResultDTO> {

        String getTypeId();

        String getDisplayName();

        List<ColumnKey> getColumns();

        List<R> getItems();

        long getTotalResultsCount();
    }

    public class BaseSearchResultsDTO<R extends RowResultDTO> implements SearchResultsDTO<R> {

        private final String typeId;
        private final String displayName;
        private final List<ColumnKey> columns;
        private final List<R> items;
        private final long totalResultsCount;

        public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<R> items) {
            this(typeId, displayName, columns, items, items == null ? 0 : items.size());
        }

        public BaseSearchResultsDTO(String typeId, String displayName, List<ColumnKey> columns, List<R> items, long totalResultsCount) {
            this.typeId = typeId;
            this.displayName = displayName;
            this.columns = columns;
            this.items = items;
            this.totalResultsCount = totalResultsCount;
        }

        @Override
        public String getTypeId() {
            return typeId;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public List<ColumnKey> getColumns() {
            return columns;
        }

        @Override
        public List<R> getItems() {
            return items;
        }

        @Override
        public long getTotalResultsCount() {
            return totalResultsCount;
        }

    }

    public static class DataArtifactTableSearchResultsDTO extends BaseSearchResultsDTO<DataArtifactTableDTO> {

        private static final String TYPE_ID = "DATA_ARTIFACT";

        private final BlackboardArtifact.Type artifactType;

        public DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type artifactType, List<ColumnKey> columns, List<DataArtifactTableDTO> items) {
            super(TYPE_ID, artifactType.getDisplayName(), columns, items);
            this.artifactType = artifactType;
        }

        public BlackboardArtifact.Type getArtifactType() {
            return artifactType;
        }
    }
}
