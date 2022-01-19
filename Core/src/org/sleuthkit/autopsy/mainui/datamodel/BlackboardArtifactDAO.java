package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.collect.ImmutableSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Sets;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.sco.SCOUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DOWNLOAD_SOURCE;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_DATA_SOURCE_USAGE;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_GEN_INFO;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_TL_EVENT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HostAddress;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

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
    "BlackboardArtifactDAO.columnKeys.dataSource.name=Data Source",
    "BlackboardArtifactDAO.columnKeys.dataSource.displayName=Data Source",
    "BlackboardArtifactDAO.columnKeys.dataSource.description=Data Source"
})
abstract class BlackboardArtifactDAO extends AbstractDAO {

    private static Logger logger = Logger.getLogger(BlackboardArtifactDAO.class.getName());

    static final int EMAIL_CONTENT_MAX_LEN = 160;
    static final int TOOL_TEXT_MAX_LEN = 512;
    static final String ELLIPSIS = "...";

    @SuppressWarnings("deprecation")
    static final Set<Integer> HIDDEN_ATTR_TYPES = ImmutableSet.of(
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_ASSOCIATED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_SET_NAME.getTypeID(),
            BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID(),
            BlackboardAttribute.Type.TSK_PATH_ID.getTypeID()
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
            SCOUtils.SCORE_COLUMN_NAME,
            SCOUtils.SCORE_COLUMN_NAME,
            SCOUtils.SCORE_COLUMN_NAME
    );

    static final ColumnKey C_COL = new ColumnKey(
            SCOUtils.COMMENT_COLUMN_NAME,
            SCOUtils.COMMENT_COLUMN_NAME,
            SCOUtils.COMMENT_COLUMN_NAME
    );

    static final ColumnKey O_COL = new ColumnKey(
            SCOUtils.OCCURANCES_COLUMN_NAME,
            SCOUtils.OCCURANCES_COLUMN_NAME,
            SCOUtils.OCCURANCES_COLUMN_NAME
    );

    static final ColumnKey DATASOURCE_COL = new ColumnKey(
            Bundle.BlackboardArtifactDAO_columnKeys_dataSource_name(),
            Bundle.BlackboardArtifactDAO_columnKeys_dataSource_displayName(),
            Bundle.BlackboardArtifactDAO_columnKeys_dataSource_description()
    );

    /**
     * Types that should not be shown in the tree.
     */
    @SuppressWarnings("deprecation")
    private static final Set<BlackboardArtifact.Type> IGNORED_TYPES = Sets.newHashSet(
            // these are shown in other parts of the UI (and different node types)
            TSK_DATA_SOURCE_USAGE,
            TSK_GEN_INFO,
            new BlackboardArtifact.Type(TSK_DOWNLOAD_SOURCE),
            TSK_TL_EVENT,
            //This is not meant to be shown in the UI at all. It is more of a meta artifact.
            TSK_ASSOCIATED_OBJECT
    );

    static final String IGNORED_TYPES_SQL_SET = IGNORED_TYPES.stream()
            .map(tp -> Integer.toString(tp.getTypeID()))
            .collect(Collectors.joining(", "));

    /**
     * @return The set of types that are not shown in the tree.
     */
    protected static Set<BlackboardArtifact.Type> getIgnoredTreeTypes() {
        return IGNORED_TYPES;
    }

    TableData createTableData(BlackboardArtifact.Type artType, List<BlackboardArtifact> arts) throws TskCoreException, NoCurrentCaseException {
        // A linked hashmap is being used for artifactAttributes to ensure that artifact order 
        // as well as attribute orders within those artifacts are preserved.  This is to maintain
        // a consistent ordering of attribute columns as received from BlackboardArtifact.getAttributes
        Map<Long, Map<BlackboardAttribute.Type, Object>> artifactAttributes = new LinkedHashMap<>();
        for (BlackboardArtifact art : arts) {
            Map<BlackboardAttribute.Type, Object> attrs = art.getAttributes().stream()
                    .filter(attr -> isRenderedAttr(artType, attr.getAttributeType()))
                    .collect(Collectors.toMap(attr -> attr.getAttributeType(), attr -> getAttrValue(artType, attr), (attr1, attr2) -> attr1, LinkedHashMap::new));

            artifactAttributes.put(art.getId(), attrs);
        }

        // NOTE: this has to be in the same order as values are added
        List<BlackboardAttribute.Type> attributeTypeKeys = artifactAttributes.values().stream()
                .flatMap(attrs -> attrs.keySet().stream())
                .distinct()
                .collect(Collectors.toList());

        List<ColumnKey> columnKeys = new ArrayList<>();
        columnKeys.add(SRC_FILE_COL);
        columnKeys.add(S_COL);
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
            cellValues.add(getDisplayNameBySourceContent(srcContent));
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
                // Note that we need to get the attribute from the original artifact since it is not displayed.
                if (artifact.getAttribute(BlackboardAttribute.Type.TSK_PATH_ID) != null) {
                    long linkedId = artifact.getAttribute(BlackboardAttribute.Type.TSK_PATH_ID).getValueLong();
                    linkedFile = linkedId >= 0
                            ? getCase().getAbstractFileById(linkedId)
                            : null;
                }
            }

            boolean isTimelineSupported = isTimelineSupported(attrValues.keySet());

            rows.add(createRow(artifact, srcContent, linkedFile, isTimelineSupported, cellValues, id));
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
        // JSON attributes are always hidden
        if (BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON.equals(attrType.getValueType())) {
            return false;
        }

        if (BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID() == artType.getTypeID()) {
            return !HIDDEN_EMAIL_ATTR_TYPES.contains(attrType.getTypeID());
        } else {
            return !HIDDEN_ATTR_TYPES.contains(attrType.getTypeID());
        }
    }

    private String getTruncated(String str, int maxLen) {
        return str.length() > maxLen
                ? str.substring(0, maxLen) + ELLIPSIS
                : str;
    }

    boolean isTimelineSupported(Collection<BlackboardAttribute.Type> attrTypes) {
        return attrTypes.stream()
                .anyMatch(tp -> BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME.equals(tp.getValueType()));
    }

    String getWhereClause(SearchParams<BlackboardArtifactSearchParam> cacheKey) {
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        String originalWhereClause = " artifacts.artifact_type_id = " + artType.getTypeID() + " ";
        if (dataSourceId != null) {
            originalWhereClause += " AND artifacts.data_source_obj_id = " + dataSourceId + " ";
        }

        String pagedWhereClause = originalWhereClause
                + " ORDER BY artifacts.obj_id ASC"
                + (cacheKey.getMaxResultsCount() != null && cacheKey.getMaxResultsCount() > 0 ? " LIMIT " + cacheKey.getMaxResultsCount() : "")
                + (cacheKey.getStartItem() > 0 ? " OFFSET " + cacheKey.getStartItem() : "");
        return pagedWhereClause;
    }

    long getTotalResultsCount(SearchParams<BlackboardArtifactSearchParam> cacheKey, long currentPageSize) throws TskCoreException, NoCurrentCaseException {
        Blackboard blackboard = getCase().getBlackboard();
        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        if ((cacheKey.getStartItem() == 0) // offset is zero AND
                && ((cacheKey.getMaxResultsCount() != null && currentPageSize < cacheKey.getMaxResultsCount()) // number of results is less than max
                || (cacheKey.getMaxResultsCount() == null))) { // OR max number of results was not specified
            return currentPageSize;
        } else {
            if (dataSourceId != null) {
                return blackboard.getArtifactsCount(artType.getTypeID(), dataSourceId);
            } else {
                return blackboard.getArtifactsCount(artType.getTypeID());
            }
        }
    }

    /**
     * Returns the display name to use based on the source content.
     *
     * @param srcContent The source content.
     *
     * @return The display name.
     */
    String getDisplayNameBySourceContent(Content srcContent) {
        if (srcContent instanceof BlackboardArtifact) {
            try {
                return ((BlackboardArtifact) srcContent).getShortDescription();
            } catch (TskCoreException ex) {
                // Log the error, but set the display name to
                // Content.getName so there is something visible to the user.
                logger.log(Level.WARNING, "Failed to get short description for artifact id = " + srcContent.getId(), ex);
            }
        } else if (srcContent instanceof OsAccount) {
            return ((OsAccount) srcContent).getAddr().orElse(srcContent.getName());
        }

        return srcContent.getName();
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
    String getSourceObjType(Content source) throws TskCoreException {
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
    @SuppressWarnings("deprecation")
    Object getAttrValue(BlackboardArtifact.Type artType, BlackboardAttribute attr) {

        // Handle the special cases
        if (artType.equals(BlackboardArtifact.Type.TSK_EMAIL_MSG)
                && attr.getAttributeType().equals(BlackboardAttribute.Type.TSK_EMAIL_CONTENT_PLAIN)) {
            return getTruncated(attr.getValueString(), EMAIL_CONTENT_MAX_LEN);
        }

        /* From BlackboardArtifactNode:
         * The truncation of text attributes appears to have been
         * motivated by the statement that "RegRipper output would
         * often cause the UI to get a black line accross it and
         * hang if you hovered over large output or selected it.
         */
        if ((BlackboardArtifact.ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID() == artType.getTypeID())
                && attr.getAttributeType().equals(BlackboardAttribute.Type.TSK_TEXT)) {
            return getTruncated(attr.getValueString(), TOOL_TEXT_MAX_LEN);
        }

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
                // We shouldn't get here since JSON attribute are not displayed in the table
                return attr.getValueString();
            case LONG:
                return attr.getValueLong();
            case STRING:
                return attr.getValueString();
            default:
                throw new IllegalArgumentException("Unknown attribute type value type: " + attr.getAttributeType().getValueType());
        }
    }

    /**
     * Returns a list of paged artifacts.
     *
     * @param arts         The artifacts.
     * @param searchParams The search parameters including the paging.
     *
     * @return The list of paged artifacts.
     */
    List<BlackboardArtifact> getPaged(List<? extends BlackboardArtifact> arts, SearchParams<?> searchParams) {
        Stream<? extends BlackboardArtifact> pagedArtsStream = arts.stream()
                .sorted(Comparator.comparing((art) -> art.getId()))
                .skip(searchParams.getStartItem());

        if (searchParams.getMaxResultsCount() != null) {
            pagedArtsStream = pagedArtsStream.limit(searchParams.getMaxResultsCount());
        }

        return pagedArtsStream.collect(Collectors.toList());
    }

    class TableData {

        final List<ColumnKey> columnKeys;
        final List<RowDTO> rows;

        TableData(List<ColumnKey> columnKeys, List<RowDTO> rows) {
            this.columnKeys = columnKeys;
            this.rows = rows;
        }
    }

    /**
     * Returns the count of each artifact type in the category.
     *
     * @param category     The artifact type category.
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return The mapping of type to count.
     *
     * @throws NoCurrentCaseException
     * @throws TskCoreException
     */
    Map<BlackboardArtifact.Type, Long> getCounts(BlackboardArtifact.Category category, Long dataSourceId) throws NoCurrentCaseException, TskCoreException {

        // get artifact types and counts
        SleuthkitCase skCase = getCase();
        String query = "artifact_type_id, COUNT(*) AS count "
                + " FROM blackboard_artifacts "
                + " WHERE artifact_type_id NOT IN (" + IGNORED_TYPES_SQL_SET + ") "
                + " AND artifact_type_id IN "
                + " (SELECT artifact_type_id FROM blackboard_artifact_types WHERE category_type = " + category.getID() + ")"
                + (dataSourceId == null ? "" : (" AND data_source_obj_id = " + dataSourceId + " "))
                + " GROUP BY artifact_type_id";
        Map<BlackboardArtifact.Type, Long> typeCounts = new HashMap<>();

        skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
            try {
                while (resultSet.next()) {
                    int artifactTypeId = resultSet.getInt("artifact_type_id");
                    BlackboardArtifact.Type type = skCase.getBlackboard().getArtifactType(artifactTypeId);
                    long count = resultSet.getLong("count");
                    typeCounts.put(type, count);
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "An error occurred while fetching artifact type counts.", ex);
            }
        });

        return typeCounts;
    }
}
