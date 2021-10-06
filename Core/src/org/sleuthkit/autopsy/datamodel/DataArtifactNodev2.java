/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.ImmutableSet;
import org.sleuthkit.autopsy.actions.ViewArtifactAction;
import org.sleuthkit.autopsy.actions.ViewOsAccountAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import static org.sleuthkit.autopsy.datamodel.AbstractContentNode.NO_DESCR;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactTableDTO;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.DataArtifactTableSearchResultsDTO;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.VirtualDirectory;


public class DataArtifactNodev2 extends AbstractNode {

    private static final Logger logger = Logger.getLogger(DataArtifactNodev2.class.getName());

    private static Lookup createLookup(DataArtifactTableDTO row) {
        DataArtifactItem artifactItem = new DataArtifactItem(row.getDataArtifact(), row.getSrcContent());
        if (row.getSrcContent() == null) {
            return Lookups.fixed(row.getDataArtifact(), artifactItem);
        } else {
            return Lookups.fixed(row.getDataArtifact(), artifactItem, row.getSrcContent());
        }
    }

    private final BlackboardArtifact.Type artifactType;
    private final Map<Integer, BlackboardAttribute.Type> attributeTypes;
    private final DataArtifactTableDTO artifactRow;
    private final boolean hasSupportedTimeStamp;
    private String translatedSourceName = null;

    public DataArtifactNodev2(DataArtifactTableSearchResultsDTO tableData, DataArtifactTableDTO artifactRow) {
        this(tableData, artifactRow, IconsUtil.getIconFilePath(tableData.getArtifactType().getTypeID()));
    }

    public DataArtifactNodev2(DataArtifactTableSearchResultsDTO tableData, DataArtifactTableDTO artifactRow, String iconPath) {
        super(Children.LEAF, createLookup(artifactRow));

        setDisplayName(artifactRow.getSrcContent().getName());
        setShortDescription(getDisplayName());
        setName(Long.toString(artifactRow.getDataArtifact().getArtifactID()));
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);

        this.artifactRow = artifactRow;
        this.artifactType = tableData.getArtifactType();
        this.attributeTypes = tableData.getAttributeTypes().stream()
                .collect(Collectors.toMap(attr -> attr.getTypeID(), attr -> attr));
        this.hasSupportedTimeStamp = supportedTimeStamp(tableData.getAttributeTypes(), this.artifactRow.getAttributeValues());
    }

    private boolean supportedTimeStamp(List<BlackboardAttribute.Type> attributeTypes, Map<Integer, Object> attributeValues) {
        return attributeTypes.stream()
                .anyMatch(tp -> {
                    return BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME.equals(tp.getValueType())
                            && attributeValues.containsKey(tp.getTypeID());
                });
    }

    /**
     * Returns a list of non null actions from the given possibly null options.
     *
     * @param items The items to purge of null items.
     *
     * @return The list of non-null actions.
     */
    private List<Action> getNonNull(Action... items) {
        return Stream.of(items)
                .filter(i -> i != null)
                .collect(Collectors.toList());
    }

    @Override
    public Action[] getActions(boolean context) {
        // groupings of actions where each group will be separated by a divider
        List<List<Action>> actionsLists = new ArrayList<>();

        DataArtifact artifact = this.artifactRow.getDataArtifact();
        Content srcContent = this.artifactRow.getSrcContent();

        // view artifact in timeline
        actionsLists.add(getNonNull(
                getTimelineArtifactAction(artifact, this.hasSupportedTimeStamp)
        ));

        // view associated file (TSK_PATH_ID attr) in directory and timeline
        AbstractFile associatedFile = this.artifactRow.getLinkedFile() instanceof AbstractFile
                ? (AbstractFile) this.artifactRow.getLinkedFile()
                : null;
        actionsLists.add(getAssociatedFileActions(associatedFile, this.artifactType));

        // view source content in directory and timeline
        actionsLists.add(getNonNull(
                getViewSrcContentAction(artifact, srcContent),
                getTimelineSrcContentAction(srcContent)
        ));

        // menu options for artifact with report parent
        if (srcContent instanceof Report) {
            actionsLists.add(DataModelActionsFactory.getActions(srcContent, false));
        }

        Node parentFileNode = getParentFileNode(srcContent);
        int selectedFileCount = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size();
        int selectedArtifactCount = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifactItem.class).size();

        // view source content if source content is some sort of file
        actionsLists.add(getSrcContentViewerActions(parentFileNode, selectedFileCount));

        // extract / export if source content is some sort of file
        if (parentFileNode != null) {
            actionsLists.add(Arrays.asList(ExtractAction.getInstance(), ExportCSVAction.getInstance()));
        }

        // file and result tagging
        actionsLists.add(getTagActions(parentFileNode != null, artifact, selectedFileCount, selectedArtifactCount));

        // menu extension items (i.e. add to central repository)
        actionsLists.add(ContextMenuExtensionPoint.getActions());

        // netbeans default items (i.e. properties)
        actionsLists.add(Arrays.asList(super.getActions(context)));

        return actionsLists.stream()
                // remove any empty lists
                .filter((lst) -> lst != null && !lst.isEmpty())
                // add in null between each list group
                .flatMap(lst -> Stream.concat(Stream.of((Action) null), lst.stream()))
                // skip the first null
                .skip(1)
                .toArray(sz -> new Action[sz]);
    }

    /**
     * Returns the name of the artifact based on the artifact type to be used
     * with the associated file string in a right click menu.
     *
     * @param artifactType The artifact type.
     *
     * @return The artifact type name.
     */
    @Messages({
        "DataArtifactNodev2_getAssociatedTypeStr_webCache=Cached File",
        "DataArtifactNodev2_getAssociatedTypeStr_webDownload=Downloaded File",
        "DataArtifactNodev2_getAssociatedTypeStr_associated=Associated File",})
    private String getAssociatedTypeStr(BlackboardArtifact.Type artifactType) {
        if (BlackboardArtifact.Type.TSK_WEB_CACHE.equals(artifactType)) {
            return Bundle.DataArtifactNodev2_getAssociatedTypeStr_webCache();
        } else if (BlackboardArtifact.Type.TSK_WEB_DOWNLOAD.equals(artifactType)) {
            return Bundle.DataArtifactNodev2_getAssociatedTypeStr_webDownload();
        } else {
            return Bundle.DataArtifactNodev2_getAssociatedTypeStr_associated();
        }
    }

    /**
     * Returns the name to represent the type of the content (file, data
     * artifact, os account, item).
     *
     * @param content The content.
     *
     * @return The name of the type of content.
     */
    @Messages({
        "DataArtifactNodev2_getViewSrcContentAction_type_File=File",
        "DataArtifactNodev2_getViewSrcContentAction_type_DataArtifact=Data Artifact",
        "DataArtifactNodev2_getViewSrcContentAction_type_OSAccount=OS Account",
        "DataArtifactNodev2_getViewSrcContentAction_type_unknown=Item"
    })
    private String getContentTypeStr(Content content) {
        if (content instanceof AbstractFile) {
            return Bundle.DataArtifactNodev2_getViewSrcContentAction_type_File();
        } else if (content instanceof DataArtifact) {
            return Bundle.DataArtifactNodev2_getViewSrcContentAction_type_DataArtifact();
        } else if (content instanceof OsAccount) {
            return Bundle.DataArtifactNodev2_getViewSrcContentAction_type_OSAccount();
        } else {
            return Bundle.DataArtifactNodev2_getViewSrcContentAction_type_unknown();
        }
    }

    @Messages({
        "# {0} - type",
        "DataArtifactNodev2_getAssociatedFileActions_viewAssociatedFileAction=View {0} in Directory",
        "# {0} - type",
        "DataArtifactNodev2_getAssociatedFileActions_viewAssociatedFileInTimelineAction=View {0} in Timeline..."
    })
    private List<Action> getAssociatedFileActions(AbstractFile associatedFile, BlackboardArtifact.Type artifactType) {
        if (associatedFile != null) {
            return Arrays.asList(
                    new ViewContextAction(
                            Bundle.DataArtifactNodev2_getAssociatedFileActions_viewAssociatedFileAction(
                                    getAssociatedTypeStr(artifactType)),
                            associatedFile),
                    new ViewFileInTimelineAction(associatedFile,
                            Bundle.DataArtifactNodev2_getAssociatedFileActions_viewAssociatedFileInTimelineAction(
                                    getAssociatedTypeStr(artifactType)))
            );
        } else {
            return Collections.emptyList();
        }

    }

    /**
     * Creates an action to navigate to src content in tree hierarchy.
     *
     * @param artifact The artifact.
     * @param content  The content.
     *
     * @return The action or null if no action derived.
     */
    @Messages({
        "# {0} - contentType",
        "DataArtifactNodev2_getSrcContentAction_actionDisplayName=View Source {0} in Directory"
    })
    private Action getViewSrcContentAction(BlackboardArtifact artifact, Content content) {
        if (content instanceof DataArtifact) {
            return new ViewArtifactAction(
                    (BlackboardArtifact) content,
                    Bundle.DataArtifactNodev2_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)));
        } else if (content instanceof OsAccount) {
            return new ViewOsAccountAction(
                    (OsAccount) content,
                    Bundle.DataArtifactNodev2_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)));
        } else if (content instanceof AbstractFile || artifact instanceof DataArtifact) {
            return new ViewContextAction(
                    Bundle.DataArtifactNodev2_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)),
                    content);
        } else {
            return null;
        }
    }

    /**
     * Returns a Node representing the file content if the content is indeed
     * some sort of file. Otherwise, return null.
     *
     * @param content The content.
     *
     * @return The file node or null if not a file.
     */
    private Node getParentFileNode(Content content) {
        if (content instanceof File) {
            return new FileNode((AbstractFile) content);
        } else if (content instanceof Directory) {
            return new DirectoryNode((Directory) content);
        } else if (content instanceof VirtualDirectory) {
            return new VirtualDirectoryNode((VirtualDirectory) content);
        } else if (content instanceof LocalDirectory) {
            return new LocalDirectoryNode((LocalDirectory) content);
        } else if (content instanceof LayoutFile) {
            return new LayoutFileNode((LayoutFile) content);
        } else if (content instanceof LocalFile || content instanceof DerivedFile) {
            return new LocalFileNode((AbstractFile) content);
        } else if (content instanceof SlackFile) {
            return new SlackFileNode((AbstractFile) content);
        } else {
            return null;
        }
    }

    /**
     * Returns tag actions.
     *
     * @param hasSrcFile            Whether or not the artifact has a source
     *                              file.
     * @param artifact              This artifact.
     * @param selectedFileCount     The count of selected files.
     * @param selectedArtifactCount The count of selected artifacts.
     *
     * @return The tag actions.
     */
    private List<Action> getTagActions(boolean hasSrcFile, BlackboardArtifact artifact, int selectedFileCount, int selectedArtifactCount) {
        List<Action> actionsList = new ArrayList<>();

        // don't show AddContentTagAction for data artifacts.
        if (hasSrcFile && !(artifact instanceof DataArtifact)) {
            actionsList.add(AddContentTagAction.getInstance());
        }

        actionsList.add(AddBlackboardArtifactTagAction.getInstance());

        // don't show DeleteFileContentTagAction for data artifacts.
        if (hasSrcFile && (!(artifact instanceof DataArtifact)) && (selectedFileCount == 1)) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }

        if (selectedArtifactCount == 1) {
            actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
        }

        return actionsList;
    }

    /**
     * Returns actions to view src content in a different viewer or window.
     *
     * @param srcFileNode       The source file node or null if no source file.
     * @param selectedFileCount The number of selected files.
     *
     * @return The list of actions or an empty list.
     */
    @Messages({
        "DataArtifactNodev2_getSrcContentViewerActions_viewInNewWin=View Item in New Window",
        "DataArtifactNodev2_getSrcContentViewerActions_openInExtViewer=Open in External Viewer  Ctrl+E"
    })
    private List<Action> getSrcContentViewerActions(Node srcFileNode, int selectedFileCount) {
        List<Action> actionsList = new ArrayList<>();
        if (srcFileNode != null) {
            actionsList.add(new NewWindowViewAction(Bundle.DataArtifactNodev2_getSrcContentViewerActions_viewInNewWin(), srcFileNode));
            if (selectedFileCount == 1) {
                actionsList.add(new ExternalViewerAction(Bundle.DataArtifactNodev2_getSrcContentViewerActions_openInExtViewer(), srcFileNode));
            } else {
                actionsList.add(ExternalViewerShortcutAction.getInstance());
            }
        }
        return actionsList;
    }

    /**
     * If the source content of the artifact represented by this node is a file,
     * returns an action to view the file in the data source tree.
     *
     * @param srcContent The src content to navigate to in the timeline action.
     *
     * @return The src content navigation action or null.
     */
    @NbBundle.Messages({
        "# {0} - contentType",
        "DataArtifactNodev2_getTimelineSrcContentAction_actionDisplayName=View Source {0} in Timeline... "
    })
    private Action getTimelineSrcContentAction(Content srcContent) {
        if (srcContent instanceof AbstractFile) {
            return new ViewFileInTimelineAction((AbstractFile) srcContent,
                    Bundle.DataArtifactNodev2_getTimelineSrcContentAction_actionDisplayName(
                            getContentTypeStr(srcContent)));
        }

        // GVDTODO does not appear necessary at this time
//        else if (srcContent instanceof DataArtifact) {
//            try {
//                if (hasSupportedTimeStamp((BlackboardArtifact) srcContent)) {
//                    return new ViewArtifactInTimelineAction((BlackboardArtifact) srcContent,
//                            Bundle.DataArtifactNodev2_getTimelineSrcContentAction_actionDisplayName(
//                                    getContentTypeStr(srcContent)));
//                }
//            } catch (TskCoreException ex) {
//                logger.log(Level.SEVERE, MessageFormat.format("Error getting source data artifact timestamp (artifact objID={0})", srcContent.getId()), ex); //NON-NLS
//            }
//        }
        return null;
    }

    /**
     * If the artifact represented by this node has a timestamp, an action to
     * view it in the timeline.
     *
     * @param art                   The artifact for timeline navigation action.
     * @param hasSupportedTimeStamp This artifact has a supported time stamp.
     *
     * @return The action or null if no action should exist.
     */
    @Messages({
        "DataArtifactNodev2_getTimelineArtifactAction_displayName=View Selected Item in Timeline... "
    })
    private Action getTimelineArtifactAction(BlackboardArtifact art, boolean hasSupportedTimeStamp) {
        if (hasSupportedTimeStamp) {
            return new ViewArtifactInTimelineAction(art, Bundle.DataArtifactNodev2_getTimelineArtifactAction_displayName());
        } else {
            return null;
        }
    }

    @NbBundle.Messages({
        "DataArtifactNodev2.createSheet.srcFile.name=Source Name",
        "DataArtifactNodev2.createSheet.srcFile.displayName=Source Name",
        "DataArtifactNodev2.createSheet.srcFile.origName=Original Name",
        "DataArtifactNodev2.createSheet.srcFile.origDisplayName=Original Name",
        "DataArtifactNodev2.createSheet.artifactType.displayName=Result Type",
        "DataArtifactNodev2.createSheet.artifactType.name=Result Type",
        "DataArtifactNodev2.createSheet.artifactDetails.displayName=Result Details",
        "DataArtifactNodev2.createSheet.artifactDetails.name=Result Details",
        "DataArtifactNodev2.createSheet.artifactMD5.displayName=MD5 Hash",
        "DataArtifactNodev2.createSheet.artifactMD5.name=MD5 Hash",
        "DataArtifactNodev2.createSheet.fileSize.name=Size",
        "DataArtifactNodev2.createSheet.fileSize.displayName=Size",
        "DataArtifactNodev2.createSheet.path.displayName=Path",
        "DataArtifactNodev2.createSheet.path.name=Path",
        "DataArtifactNodev2.createSheet.dataSrc.name=Data Source",
        "DataArtifactNodev2.createSheet.dataSrc.displayName=Data Source"
    })
    @Override
    protected Sheet createSheet() {
        Content srcContent = this.artifactRow.getSrcContent();

        /*
         * Create an empty property sheet.
         */
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        /*
         * Add the name of the source content of the artifact represented by
         * this node to the sheet. The value of this property is the same as the
         * display name of the node and this a "special" property that displays
         * the node's icon as well as the display name.
         */
        sheetSet.put(new NodeProperty<>(
                Bundle.DataArtifactNodev2_createSheet_srcFile_name(),
                Bundle.DataArtifactNodev2_createSheet_srcFile_displayName(),
                NO_DESCR,
                getDisplayName()));

        GetSCOTask scoTask = addSCOColumns(sheetSet);

        if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
            /*
             * If machine translation is configured, add the original name of
             * the of the source content of the artifact represented by this
             * node to the sheet.
             */
            sheetSet.put(new NodeProperty<>(
                    Bundle.DataArtifactNodev2_createSheet_srcFile_origName(),
                    Bundle.DataArtifactNodev2_createSheet_srcFile_origDisplayName(),
                    NO_DESCR,
                    translatedSourceName != null ? srcContent.getName() : ""));
        }

        /*
         * Add the attributes of the artifact represented by this node to the
         * sheet.
         */
        for (Map.Entry<String, Object> entry
                : getPropertyMap(this.artifactType.getTypeID(), this.attributeTypes, this.artifactRow.getAttributeValues()).entrySet()) {

            sheetSet.put(new NodeProperty<>(entry.getKey(),
                    entry.getKey(),
                    NO_DESCR,
                    entry.getValue()));
        }

        String dataSourceStr = this.artifactRow.getDataSourceName();

        if (dataSourceStr.isEmpty() == false) {
            sheetSet.put(new NodeProperty<>(
                    Bundle.DataArtifactNodev2_createSheet_dataSrc_name(),
                    Bundle.DataArtifactNodev2_createSheet_dataSrc_displayName(),
                    NO_DESCR,
                    dataSourceStr));
        }

        return sheet;
    }

    /**
     * Adds a "custom" property to the property sheet of this node, independent
     * of the artifact this node represents or its source content.
     *
     * @param property The custom property.
     */
//    public void addNodeProperty(NodeProperty<?> property) {
//        if (customProperties == null) {
//            customProperties = new ArrayList<>();
//        }
//        customProperties.add(property);
//    }
    @SuppressWarnings("deprecation")
    private static final Set<Integer> HIDDEN_ATTR_TYPES = ImmutableSet.of(
            ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_ASSOCIATED_ARTIFACT.getTypeID(),
            BlackboardAttribute.Type.TSK_SET_NAME.getTypeID(),
            BlackboardAttribute.Type.TSK_KEYWORD_SEARCH_TYPE.getTypeID()
    );

    @SuppressWarnings("deprecation")
    private static final Set<Integer> TRUNCATED_ATTR_TYPES = ImmutableSet.of(
            ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID(),
            BlackboardAttribute.Type.TSK_TEXT.getTypeID()
    );

    private Map<String, Object> getPropertyMap(int artifactTypeId, Map<Integer, BlackboardAttribute.Type> attrTypes, Map<Integer, Object> attributes) {
        Map<String, Object> toRet = new HashMap<>();
        for (Entry<Integer, Object> entry : attributes.entrySet()) {
            Integer typeId = entry.getKey();
            BlackboardAttribute.Type attrType = attrTypes.get(typeId);
            TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType = attrType != null ? attrType.getValueType() : null;
            String attrTypeStr = attrType != null ? attrType.getDisplayName() : typeId.toString();
            Object value = entry.getValue();

            if (HIDDEN_ATTR_TYPES.contains(typeId) || valueType == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.JSON) {
                /*
                     * Do nothing.
                 */
                continue;
            } else if (artifactTypeId == BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID()) {
                Object msgVal = getEmailMsgProperty(typeId, value);
                if (msgVal != null) {
                    toRet.put(attrTypeStr, msgVal);
                }
            } else if (value instanceof Date) {
                toRet.put(attrTypeStr, TimeZoneUtils.getFormattedTime(((Date) value).getTime() / 1000));
            } else if (TRUNCATED_ATTR_TYPES.contains(typeId) && value instanceof String) {
                /*
                     * The truncation of text attributes appears to have been
                     * motivated by the statement that "RegRipper output would
                     * often cause the UI to get a black line accross it and
                     * hang if you hovered over large output or selected it.
                     * This reduces the amount of data in the table. Could
                     * consider doing this for all fields in the UI."
                 */
                String valueString = ((String) value);
                if (valueString.length() > 512) {
                    valueString = valueString.substring(0, 512);
                }
                toRet.put(attrTypeStr, valueString);
            } else {
                toRet.put(attrTypeStr, value);
            }
        }
        return toRet;
    }

    private static final Set<Integer> HIDDEN_EMAIL_ATTR_TYPES = ImmutableSet.of(
            BlackboardAttribute.Type.TSK_DATETIME_SENT.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CONTENT_HTML.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CONTENT_RTF.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_BCC.getTypeID(),
            BlackboardAttribute.Type.TSK_EMAIL_CC.getTypeID(),
            BlackboardAttribute.Type.TSK_HEADERS.getTypeID()
    );

    /**
     * Adds an email message attribute of the artifact this node represents to a
     * map of name-value pairs, where the names are attribute type display
     * names.
     *
     * @param map       The map to be populated with the artifact attribute
     *                  name-value pair.
     * @param attribute The attribute to use to make the map entry.
     */
    private Object getEmailMsgProperty(int attrTypeId, Object value) {
        if (HIDDEN_EMAIL_ATTR_TYPES.contains(attrTypeId)) {
            return null;
        } else if (attrTypeId == ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN.getTypeID() && value instanceof String) {
            String valueStr = (String) value;
            if (valueStr.length() > 160) {
                valueStr = valueStr.substring(0, 160) + "...";
            }
            return valueStr;
        } else if (value instanceof Date) {
            return TimeZoneUtils.getFormattedTime(((Date) value).getTime() / 1000);
        } else {
            return value;
        }
    }

    @Messages({
        "DataArtifactNodev2.createSheet.comment.displayName=C",
        "DataArtifactNodev2.createSheet.comment.name=C",
        "# {0} - occurrenceCount",
        "# {1} - attributeType",
        "DataArtifactNodev2.createSheet.count.description=There were {0} datasource(s) found with occurrences of the correlation value of type {1}",
        "DataArtifactNodev2.createSheet.count.displayName=O",
        "DataArtifactNodev2.createSheet.count.name=O",
        "DataArtifactNodev2.createSheet.count.noCorrelationAttributes.description=No correlation properties found",
        "DataArtifactNodev2.createSheet.count.noCorrelationValues.description=Unable to find other occurrences because no value exists for the available correlation property",
        "DataArtifactNodev2.createSheet.score.displayName=S",
        "DataArtifactNodev2.createSheet.score.name=S"
    })
    private GetSCOTask addSCOColumns(Sheet.Set sheetSet) {
        if (!UserPreferences.getHideSCOColumns()) {
            /*
             * Add S(core), C(omments), and O(ther occurences) columns to the
             * sheet and start a background task to compute the value of these
             * properties for the artifact represented by this node. The task
             * will fire a PropertyChangeEvent when the computation is completed
             * and this node's PropertyChangeListener will update the sheet.
             */
            sheetSet.put(new NodeProperty<>(
                    Bundle.DataArtifactNodev2_createSheet_score_name(),
                    Bundle.DataArtifactNodev2_createSheet_score_displayName(),
                    "" /* VALUE_LOADING */,
                    ""));
            sheetSet.put(new NodeProperty<>(
                    Bundle.DataArtifactNodev2_createSheet_comment_name(),
                    Bundle.DataArtifactNodev2_createSheet_comment_displayName(),
                    "" /* VALUE_LOADING */,
                    ""));
            if (CentralRepository.isEnabled()) {
                sheetSet.put(new NodeProperty<>(
                        Bundle.DataArtifactNodev2_createSheet_count_name(),
                        Bundle.DataArtifactNodev2_createSheet_count_displayName(),
                        "" /* VALUE_LOADING */,
                        ""));
            }
            //return new GetSCOTask(new WeakReference<>(this), scoListener);
        }
        return null;
    }
}
