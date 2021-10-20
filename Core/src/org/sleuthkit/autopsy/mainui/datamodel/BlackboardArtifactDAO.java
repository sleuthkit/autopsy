package org.sleuthkit.autopsy.mainui.datamodel;


import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.mainui.datamodel.Bundle;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

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

/**
 * Base class for common methods.
 */
@NbBundle.Messages({
    "BlackboardArtifactDAO.columnKeys.srcFile.name=Source Name",
    "BlackboardArtifactDAO.columnKeys.srcFile.displayName=Source Name",
    "BlackboardArtifactDAO.columnKeys.srcFile.description=Source Name",
    "BlackboardArtifactDAO.columnKeys.score.name=Score",
    "BlackboardArtifactDAO.columnKeys.score.displayName=S",
    "BlackboardArtifactDAO.columnKeys.score.description=Score",
    "BlackboardArtifactDAO.columnKeys.comment.name=Comment",
    "BlackboardArtifactDAO.columnKeys.comment.displayName=C",
    "BlackboardArtifactDAO.columnKeys.comment.description=Comment",
    "BlackboardArtifactDAO.columnKeys.occurrences.name=Occurrences",
    "BlackboardArtifactDAO.columnKeys.occurrences.displayName=O",
    "BlackboardArtifactDAO.columnKeys.occurrences.description=Occurrences",
    "BlackboardArtifactDAO.columnKeys.dataSource.name=Data Source",
    "BlackboardArtifactDAO.columnKeys.dataSource.displayName=Data Source",
    "BlackboardArtifactDAO.columnKeys.dataSource.description=Data Source"
})
abstract class BlackboardArtifactDAO {
    // GVDTODO there is a different standard for normal attr strings and email attr strings
    static final int STRING_LENGTH_MAX = 160;
    static final String ELLIPSIS = "...";

    @SuppressWarnings("deprecation")
    static final Set<Integer> HIDDEN_ATTR_TYPES = ImmutableSet.of(
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_ASSOCIATED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_SET_NAME.getTypeID(),
            BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID()
    );
    static final Set<Integer> HIDDEN_EMAIL_ATTR_TYPES = ImmutableSet.of(
            BlackboardAttribute.Type.TSK_DATETIME_SENT.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CONTENT_HTML.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CONTENT_RTF.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_BCC.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CC.getTypeID(),
            BlackboardAttribute.Type.TSK_HEADERS.getTypeID()
    );

    static final ColumnKey SRC_FILE_COL = new ColumnKey(
            Bundle.BlackboardArtifactDAO_columnKeys_srcFile_name(),
            Bundle.BlackboardArtifactDAO_columnKeys_srcFile_displayName(),
            Bundle.BlackboardArtifactDAO_columnKeys_srcFile_description()
    );

    static final ColumnKey S_COL = new ColumnKey(
            Bundle.BlackboardArtifactDAO_columnKeys_score_name(),
            Bundle.BlackboardArtifactDAO_columnKeys_score_displayName(),
            Bundle.BlackboardArtifactDAO_columnKeys_score_description()
    );

    static final ColumnKey C_COL = new ColumnKey(
            Bundle.BlackboardArtifactDAO_columnKeys_comment_name(),
            Bundle.BlackboardArtifactDAO_columnKeys_comment_displayName(),
            Bundle.BlackboardArtifactDAO_columnKeys_comment_description()
    );

    static final ColumnKey O_COL = new ColumnKey(
            Bundle.BlackboardArtifactDAO_columnKeys_occurrences_name(),
            Bundle.BlackboardArtifactDAO_columnKeys_occurrences_displayName(),
            Bundle.BlackboardArtifactDAO_columnKeys_occurrences_description()
    );

    static final ColumnKey DATASOURCE_COL = new ColumnKey(
            Bundle.BlackboardArtifactDAO_columnKeys_dataSource_name(),
            Bundle.BlackboardArtifactDAO_columnKeys_dataSource_displayName(),
            Bundle.BlackboardArtifactDAO_columnKeys_dataSource_description()
    );
    
    TableData createTableData(BlackboardArtifact.Type artType, List<BlackboardArtifact> arts) throws TskCoreException, NoCurrentCaseException {
        Map<Long, Map<BlackboardAttribute.Type, Object>> artifactAttributes = new HashMap<>();
        for (BlackboardArtifact art : arts) {
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
        addAnalysisResultColumnKeys(columnKeys);
        columnKeys.addAll(attributeTypeKeys.stream()
                .map(attrType -> new ColumnKey(attrType.getTypeName(), attrType.getDisplayName(), attrType.getDisplayName()))
                .collect(Collectors.toList()));
        columnKeys.add(DATASOURCE_COL);

        // determine all different attribute types present as well as row data for each artifact
        List<RowDTO> rows = new ArrayList<>();

        for (BlackboardArtifact artifact : arts) {
            List<Object> cellValues = new ArrayList<>();

            Content srcContent = artifact.getParent();
            cellValues.add(srcContent.getName());
            // GVDTODO handle translated filename here
            // GVDTODO handle SCO
            cellValues.add(null);
            cellValues.add(null);
            cellValues.add(null);
            
            addAnalysisResultFields(artifact, cellValues);

            long id = artifact.getId();
            Map<BlackboardAttribute.Type, Object> attrValues = artifactAttributes.getOrDefault(id, Collections.emptyMap());
            // NOTE: this has to be in the same order as attribute keys
            for (BlackboardAttribute.Type colAttrType : attributeTypeKeys) {
                cellValues.add(attrValues.get(colAttrType));
            }

            String dataSourceName = getDataSourceName(srcContent);
            cellValues.add(dataSourceName);

            AbstractFile linkedFile = null;
            if (artType.getCategory().equals(BlackboardArtifact.Category.DATA_ARTIFACT)) {
                Object linkedId = attrValues.get(BlackboardAttribute.Type.TSK_PATH_ID);
                linkedFile = linkedId instanceof Long && ((Long) linkedId) >= 0
                        ? getCase().getAbstractFileById((Long) linkedId)
                        : null;
            }

            boolean isTimelineSupported = isTimelineSupported(attrValues.keySet());

            rows.add(createRow(artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id));
            //rows.add(new AnalysisResultRowDTO(artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id));
        }

        return new TableData(columnKeys, rows);
    }
    
    abstract RowDTO createRow(BlackboardArtifact dataArtifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id);
    
    void addAnalysisResultColumnKeys(List<ColumnKey> columnKeys) { 
        // By default, do nothing
    }
    
    void addAnalysisResultFields(BlackboardArtifact artifact, List<Object> cells) throws TskCoreException {
        // By default, do nothing
    }
    
    SleuthkitCase getCase() throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getSleuthkitCase();
    }
    
    boolean isRenderedAttr(BlackboardArtifact.Type artType, BlackboardAttribute.Type attrType) {
        if (BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() == artType.getTypeID()) {
            return !HIDDEN_EMAIL_ATTR_TYPES.contains(attrType.getTypeID());
        } else {
            return !HIDDEN_ATTR_TYPES.contains(attrType.getTypeID())
                    && !BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON.equals(attrType.getValueType());
        }
    }

    private String getTruncated(String str) {
        return str.length() > STRING_LENGTH_MAX
                ? str.substring(0, STRING_LENGTH_MAX) + ELLIPSIS
                : str;
    }

    boolean isTimelineSupported(Collection<BlackboardAttribute.Type> attrTypes) {
        return attrTypes.stream()
                .anyMatch(tp -> BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME.equals(tp.getValueType()));
    }

    String getDataSourceName(Content srcContent) throws TskCoreException {
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
    Object getAttrValue(BlackboardAttribute attr) {
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
    
    class TableData {
        final List<ColumnKey> columnKeys;
        final List<RowDTO> rows;
        
        TableData(List<ColumnKey> columnKeys, List<RowDTO> rows) {
            this.columnKeys = columnKeys;
            this.rows = rows;
        }
    }
}
