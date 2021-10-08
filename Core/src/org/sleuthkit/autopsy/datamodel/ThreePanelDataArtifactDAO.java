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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.BaseRowResultDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.BaseSearchResultsDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.ColumnKey;
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
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.srcFile.name=Source Name",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.srcFile.displayName=Source Name",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.srcFile.description=Source Name",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.score.name=Score",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.score.displayName=S",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.score.description=Score",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.comment.name=Comment",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.comment.displayName=C",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.comment.description=Comment",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.occurrences.name=Occurrences",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.occurrences.displayName=O",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.occurrences.description=Occurrences",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.dataSource.name=Data Source",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.dataSource.displayName=Data Source",
    "ThreePanelDataArtifactDAO.dataArtifact.columnKeys.dataSource.description=Data Source"
})
public class ThreePanelDataArtifactDAO {

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
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_srcFile_name(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_srcFile_displayName(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_srcFile_description()
    );

    private static final ColumnKey S_COL = new ColumnKey(
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_score_name(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_score_displayName(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_score_description()
    );

    private static final ColumnKey C_COL = new ColumnKey(
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_comment_name(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_comment_displayName(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_comment_description()
    );

    private static final ColumnKey O_COL = new ColumnKey(
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_occurrences_name(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_occurrences_displayName(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_occurrences_description()
    );

    private static final ColumnKey DATASOURCE_COL = new ColumnKey(
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_dataSource_name(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_dataSource_displayName(),
            Bundle.ThreePanelDataArtifactDAO_dataArtifact_columnKeys_dataSource_description()
    );

    private static ThreePanelDataArtifactDAO instance = null;

    public synchronized static ThreePanelDataArtifactDAO getInstance() {
        if (instance == null) {
            instance = new ThreePanelDataArtifactDAO();
        }

        return instance;
    }

    private final Cache<DataArtifactKeyv2, DataArtifactTableSearchResultsDTO> dataArtifactCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    private DataArtifactTableSearchResultsDTO fetchDataArtifactsForTable(DataArtifactKeyv2 cacheKey) throws NoCurrentCaseException, TskCoreException {
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
                    .collect(Collectors.toMap(attr -> attr.getAttributeType(), attr -> getAttrValue(attr), (attr1, attr2) -> attr1));

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
            return !HIDDEN_ATTR_TYPES.contains(attrType)
                    && !BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON.equals(attrType.getValueType());
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

    public DataArtifactTableSearchResultsDTO getDataArtifactsForTable(DataArtifactKeyv2 artifactKey) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.DATA_ARTIFACT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and data artifact.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        return dataArtifactCache.get(artifactKey, () -> fetchDataArtifactsForTable(artifactKey));
    }

    public void dropDataArtifactCache() {
        dataArtifactCache.invalidateAll();
    }

    public void dropDataArtifactCache(BlackboardArtifact.Type artType) {
        dataArtifactCache.invalidate(artType);
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
