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

    private final Cache<TableCacheKey, DataArtifactTableDTO> tableCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    private DataArtifactTableDTO fetchDataArtifactsForTable(TableCacheKey cacheKey) throws NoCurrentCaseException, TskCoreException {
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
        List<DataArtifactRow> rows = new ArrayList<>();

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
            rows.add(new DataArtifactRow(id, attributeValues, artifact, srcContent, linkedFile, dataSourceName));
        }

        List<BlackboardAttribute.Type> attributeTypeSortedList = attributeTypes.stream()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());

        return new DataArtifactTableDTO(artType, attributeTypeSortedList, rows);
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

    public DataArtifactTableDTO getDataArtifactsForTable(BlackboardArtifact.Type artType, Long dataSourceId) throws ExecutionException {
        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  "
                    + "Received {0}", artType));
        }

        TableCacheKey cacheKey = new TableCacheKey(artType, dataSourceId);
        return tableCache.get(cacheKey, () -> fetchDataArtifactsForTable(cacheKey));
    }

    public void dropTableCache() {
        tableCache.invalidateAll();
    }

    public void dropTableCache(BlackboardArtifact.Type artType) {
        tableCache.invalidate(artType);
    }

    private static class TableCacheKey {

        private final BlackboardArtifact.Type artifactType;
        private final Long dataSourceId;

        public TableCacheKey(BlackboardArtifact.Type artifactType, Long dataSourceId) {
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
            final TableCacheKey other = (TableCacheKey) obj;
            if (!Objects.equals(this.artifactType, other.artifactType)) {
                return false;
            }
            if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
                return false;
            }
            return true;
        }

    }

    public static class DataArtifactRow {

        private final long id;

        private final Map<Integer, Object> attributeValues;

        private final DataArtifact dataArtifact;
        private final Content srcContent;
        private final Content linkedFile;
        private String dataSourceName;

        public DataArtifactRow(long id, Map<Integer, Object> attributeValues, DataArtifact dataArtifact, Content srcContent, Content linkedFile, String dataSourceName) {
            this.id = id;
            this.attributeValues = attributeValues;
            this.dataArtifact = dataArtifact;
            this.srcContent = srcContent;
            this.linkedFile = linkedFile;
            this.dataSourceName = dataSourceName;
        }

        public long getId() {
            return id;
        }

        public Map<Integer, Object> getAttributeValues() {
            return attributeValues;
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

        public String getDataSourceName() {
            return dataSourceName;
        }
    }

    public static class DataArtifactTableDTO {

        private final BlackboardArtifact.Type artifactType;
        private final List<BlackboardAttribute.Type> attributeTypes;
        private final List<DataArtifactRow> rows;

        public DataArtifactTableDTO(BlackboardArtifact.Type artifactType, List<BlackboardAttribute.Type> attributeKeys, List<DataArtifactRow> rows) {
            this.artifactType = artifactType;
            this.attributeTypes = attributeKeys;
            this.rows = rows;
        }

        public BlackboardArtifact.Type getArtifactType() {
            return artifactType;
        }

        public List<BlackboardAttribute.Type> getAttributeTypes() {
            return attributeTypes;
        }

        public List<DataArtifactRow> getRows() {
            return rows;
        }
    }
}
