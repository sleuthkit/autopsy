/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.HostAddress;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 *
 * @author gregd
 */
public class ThreePanelDAO {

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

        // determine all different attribute types present as well as row data for each artifact
        Set<BlackboardAttribute.Type> attributeTypes = new HashSet<>();
        List<DataArtifactTableDTO> rows = new ArrayList<>();

        for (DataArtifact artifact : arts) {
            long id = artifact.getId();
            Map<Integer, Object> attributeValues = new HashMap<>();
            for (BlackboardAttribute attr : artifact.getAttributes()) {
                attributeTypes.add(attr.getAttributeType());
                attributeValues.put(attr.getAttributeType().getTypeID(), getAttrValue(attr));
            }

            Object linkedId = attributeValues.get(BlackboardAttribute.Type.TSK_PATH_ID.getTypeName());
            AbstractFile linkedFile = linkedId instanceof Long && ((Long) linkedId) >= 0
                    ? skCase.getAbstractFileById((Long) linkedId)
                    : null;

            Content srcContent = artifact.getParent();
            String dataSourceName = getDataSourceName(srcContent);
            rows.add(new DataArtifactTableDTO(id, attributeValues, artifact, srcContent, linkedFile, dataSourceName));
        }

        List<BlackboardAttribute.Type> attributeTypeSortedList = attributeTypes.stream()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        return new DataArtifactTableSearchResultsDTO(artType, attributeTypeSortedList, rows);
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
    private String getSourceObjType(Content source) throws TskCoreException {
        if (source instanceof BlackboardArtifact) {
            BlackboardArtifact srcArtifact = (BlackboardArtifact) source;
            return srcArtifact.getType().getDisplayName();
        } else if (source instanceof Volume) {
            return TskData.ObjectType.VOL.toString();
        } else if (source instanceof AbstractFile) {
            return TskData.ObjectType.ABSTRACTFILE.toString();
        } else if (source instanceof Image) {
            return TskData.ObjectType.IMG.toString();
        } else if (source instanceof VolumeSystem) {
            return TskData.ObjectType.VS.toString();
        } else if (source instanceof OsAccount) {
            return TskData.ObjectType.OS_ACCOUNT.toString();
        } else if (source instanceof HostAddress) {
            return TskData.ObjectType.HOST_ADDRESS.toString();
        } else if (source instanceof Pool) {
            return TskData.ObjectType.POOL.toString();
        }
        return "";
    }

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
                return attr.getValueString();
            case LONG:
                return attr.getValueLong();
            case STRING:
                return attr.getValueString();
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

    public class ColumnKey {

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

        public DataArtifactTableSearchResultsDTO(BlackboardArtifact.Type artifactType,  List<ColumnKey> columns, List<DataArtifactTableDTO> items) {
            super(TYPE_ID, artifactType.getDisplayName(), columns, items);
            this.artifactType = artifactType;
        }

        public BlackboardArtifact.Type getArtifactType() {
            return artifactType;
        }
    }
}
