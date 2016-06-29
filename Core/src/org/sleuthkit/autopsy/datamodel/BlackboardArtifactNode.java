/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.Action;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Node wrapping a blackboard artifact object. This is generated from several
 * places in the tree.
 */
public class BlackboardArtifactNode extends DisplayableItemNode {

    private final BlackboardArtifact artifact;
    private final Content associated;
    private List<NodeProperty<? extends Object>> customProperties;
    private static final Logger LOGGER = Logger.getLogger(BlackboardArtifactNode.class.getName());
    /*
     * Artifact types which should have the full unique path of the associated
     * content as a property.
     */
    private static final Integer[] SHOW_UNIQUE_PATH = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),};

    // TODO (RC): This is an unattractive alternative to subclassing BlackboardArtifactNode,
    // cut from the same cloth as the equally unattractive SHOW_UNIQUE_PATH array
    // above. It should be removed when and if the subclassing is implemented.
    private static final Integer[] SHOW_FILE_METADATA = new Integer[]{
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),};

    /**
     * Construct blackboard artifact node from an artifact and using provided
     * icon
     *
     * @param artifact artifact to encapsulate
     * @param iconPath icon to use for the artifact
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(Children.LEAF, createLookup(artifact));

        this.artifact = artifact;
        //this.associated = getAssociatedContent(artifact);
        this.associated = this.getLookup().lookup(Content.class);
        this.setName(Long.toString(artifact.getArtifactID()));
        this.setDisplayName();
        this.setIconBaseWithExtension(iconPath);
    }

    /**
     * Construct blackboard artifact node from an artifact and using default
     * icon for artifact type
     *
     * @param artifact artifact to encapsulate
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        super(Children.LEAF, createLookup(artifact));

        this.artifact = artifact;
        //this.associated = getAssociatedContent(artifact);
        this.associated = this.getLookup().lookup(Content.class);
        this.setName(Long.toString(artifact.getArtifactID()));
        this.setDisplayName();
        this.setIconBaseWithExtension(ExtractedContent.getIconFilePath(artifact.getArtifactTypeID())); //NON-NLS
    }

    @Override
    @NbBundle.Messages({
        "BlackboardArtifactNode.getAction.errorTitle=Error getting actions",
        "BlackboardArtifactNode.getAction.resultErrorMessage=There was a problem getting actions for the selected result."
        + "  The 'View Result in Timeline' action will not be available.",
        "BlackboardArtifactNode.getAction.linkedFileMessage=There was a problem getting actions for the selected result. "
        + " The 'View File in Timeline' action will not be available."})
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(context)));

        //if this artifact has a time stamp add the action to view it in the timeline
        try {
            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
                actionsList.add(new ViewArtifactInTimelineAction(artifact));
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting arttribute(s) from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.BlackboardArtifactNode_getAction_errorTitle(), Bundle.BlackboardArtifactNode_getAction_resultErrorMessage());
        }

        // if the artifact links to another file, add an action to go to that file
        try {
            AbstractFile c = findLinked(artifact);
            if (c != null) {
                actionsList.add(ViewFileInTimelineAction.createViewFileAction(c));
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting linked file from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.BlackboardArtifactNode_getAction_errorTitle(), Bundle.BlackboardArtifactNode_getAction_linkedFileMessage());
        }

        //if this artifact has associated content, add the action to view the content in the timeline
        AbstractFile file = getLookup().lookup(AbstractFile.class);
        if (null != file) {
            
            actionsList.add(ViewFileInTimelineAction.createViewSourceFileAction(file));
        }

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    /**
     * Set the filter node display name. The value will either be the file name
     * or something along the lines of e.g. "Messages Artifact" for keyword hits
     * on artifacts.
     */
    private void setDisplayName() {
        String displayName = ""; //NON-NLS
        if (associated != null) {
            displayName = associated.getName();
        }

        // If this is a node for a keyword hit on an artifact, we set the 
        // display name to be the artifact type name followed by " Artifact"
        // e.g. "Messages Artifact".
        if (artifact != null && artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            try {
                for (BlackboardAttribute attribute : artifact.getAttributes()) {
                    if (attribute.getAttributeType().getTypeID() == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                        BlackboardArtifact associatedArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                        if (associatedArtifact != null) {
                            displayName = associatedArtifact.getDisplayName() + " Artifact";
                        }
                    }
                }
            } catch (TskCoreException ex) {
                // Do nothing since the display name will be set to the file name.
            }
        }
        this.setDisplayName(displayName);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }
        final String NO_DESCR = NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.noDesc.text");

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, artifact);

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.srcFile.name"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.srcFile.displayName"),
                NO_DESCR,
                this.getDisplayName()));

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ss.put(new NodeProperty<>(entry.getKey(),
                    entry.getKey(),
                    NO_DESCR,
                    entry.getValue()));
        }

        //append custom node properties
        if (customProperties != null) {
            for (NodeProperty<? extends Object> np : customProperties) {
                ss.put(np);
            }
        }
        final int artifactTypeId = artifact.getArtifactTypeID();

        // If mismatch, add props for extension and file type
        if (artifactTypeId == BlackboardArtifact.ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID()) {
            String ext = ""; //NON-NLS
            String actualMimeType = ""; //NON-NLS
            if (associated instanceof AbstractFile) {
                AbstractFile af = (AbstractFile) associated;
                ext = af.getNameExtension();
                actualMimeType = af.getMIMEType();
                if (actualMimeType == null) {
                    actualMimeType = ""; //NON-NLS
                }
            }
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.ext.name"),
                    NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.ext.displayName"),
                    NO_DESCR,
                    ext));
            ss.put(new NodeProperty<>(
                    NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.mimeType.name"),
                    NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.mimeType.displayName"),
                    NO_DESCR,
                    actualMimeType));
        }

        if (Arrays.asList(SHOW_UNIQUE_PATH).contains(artifactTypeId)) {
            String sourcePath = ""; //NON-NLS
            try {
                sourcePath = associated.getUniquePath();
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Failed to get unique path from: {0}", associated.getName()); //NON-NLS
            }

            if (sourcePath.isEmpty() == false) {
                ss.put(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.filePath.name"),
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.filePath.displayName"),
                        NO_DESCR,
                        sourcePath));
            }

            if (Arrays.asList(SHOW_FILE_METADATA).contains(artifactTypeId)) {
                AbstractFile file = associated instanceof AbstractFile ? (AbstractFile) associated : null;
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileModifiedTime.name"),
                        NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileModifiedTime.displayName"),
                        "",
                        file != null ? ContentUtils.getStringTime(file.getMtime(), file) : ""));
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileChangedTime.name"),
                        NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileChangedTime.displayName"),
                        "",
                        file != null ? ContentUtils.getStringTime(file.getCtime(), file) : ""));
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileAccessedTime.name"),
                        NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileAccessedTime.displayName"),
                        "",
                        file != null ? ContentUtils.getStringTime(file.getAtime(), file) : ""));
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileCreatedTime.name"),
                        NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileCreatedTime.displayName"),
                        "",
                        file != null ? ContentUtils.getStringTime(file.getCrtime(), file) : ""));
                ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileSize.name"),
                        NbBundle.getMessage(this.getClass(), "ContentTagNode.createSheet.fileSize.displayName"),
                        "",
                        associated.getSize()));
            }
        } else {
            String dataSourceStr = "";
            try {
                Content dataSource = associated.getDataSource();
                if (dataSource != null) {
                    dataSourceStr = dataSource.getName();
                } else {
                    dataSourceStr = getRootParentName();
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "Failed to get image name from {0}", associated.getName()); //NON-NLS
            }

            if (dataSourceStr.isEmpty() == false) {
                ss.put(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.dataSrc.name"),
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.dataSrc.displayName"),
                        NO_DESCR,
                        dataSourceStr));
            }
        }

        return s;
    }

    private String getRootParentName() {
        String parentName = associated.getName();
        Content parent = associated;
        try {
            while ((parent = parent.getParent()) != null) {
                parentName = parent.getName();
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to get parent name from {0}", associated.getName()); //NON-NLS
            return "";
        }
        return parentName;
    }

    /**
     * Add an additional custom node property to that node before it is
     * displayed
     *
     * @param np NodeProperty to add
     */
    public void addNodeProperty(NodeProperty<?> np) {
        if (null == customProperties) {
            //lazy create the list
            customProperties = new ArrayList<>();
        }
        customProperties.add(np);
    }

    /**
     * Fill map with Artifact properties
     *
     * @param map      map with preserved ordering, where property names/values
     *                 are put
     * @param artifact to extract properties from
     */
    @SuppressWarnings("deprecation")
    private void fillPropertyMap(Map<String, Object> map, BlackboardArtifact artifact) {
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                final int attributeTypeID = attribute.getAttributeType().getTypeID();
                //skip some internal attributes that user shouldn't see
                if (attributeTypeID == ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                } else if (attribute.getAttributeType().getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                    map.put(attribute.getAttributeType().getDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), associated));
                } else if (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_TOOL_OUTPUT.getTypeID()
                        && attributeTypeID == ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()) {
                    /*
                     * This was added because the RegRipper output would often
                     * cause the UI to get a black line accross it and hang if
                     * you hovered over large output or selected it. This
                     * reduces the amount of data in the table. Could consider
                     * doing this for all fields in the UI.
                     */
                    String value = attribute.getDisplayString();
                    if (value.length() > 512) {
                        value = value.substring(0, 512);
                    }
                    map.put(attribute.getAttributeType().getDisplayName(), value);
                } else {
                    map.put(attribute.getAttributeType().getDisplayName(), attribute.getDisplayString());
                }
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Getting attributes failed", ex); //NON-NLS
        }
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Create a Lookup based on what is in the passed in artifact.
     *
     * @param artifact
     *
     * @return
     */
    private static Lookup createLookup(BlackboardArtifact artifact) {
        List<Object> forLookup = new ArrayList<>();
        forLookup.add(artifact);

        // Add the content the artifact is associated with
        Content content = getAssociatedContent(artifact);
        if (content != null) {
            forLookup.add(content);
        }

        // if there is a text highlighted version, of the content, add it too
        // currently happens from keyword search module
        TextMarkupLookup highlight = getHighlightLookup(artifact, content);
        if (highlight != null) {
            forLookup.add(highlight);
        }

        return Lookups.fixed(forLookup.toArray(new Object[forLookup.size()]));
    }

    private static Content getAssociatedContent(BlackboardArtifact artifact) {
        try {
            return artifact.getSleuthkitCase().getContentById(artifact.getObjectID());
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Getting file failed", ex); //NON-NLS
        }
        throw new IllegalArgumentException(
                NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.getAssocCont.exception.msg"));
    }

    

    private static TextMarkupLookup getHighlightLookup(BlackboardArtifact artifact, Content content) {
        if (artifact.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            return null;
        }

        long objectId = content.getId();

        Lookup lookup = Lookup.getDefault();
        TextMarkupLookup highlightFactory = lookup.lookup(TextMarkupLookup.class);
        try {
            List<BlackboardAttribute> attributes = artifact.getAttributes();
            String keyword = null;
            String regexp = null;
            for (BlackboardAttribute att : attributes) {
                final int attributeTypeID = att.getAttributeType().getTypeID();
                if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                    keyword = att.getValueString();
                } else if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()) {
                    regexp = att.getValueString();
                } else if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                    objectId = att.getValueLong();
                }
            }
            if (keyword != null) {
                boolean isRegexp = StringUtils.isNotBlank(regexp);
                String origQuery;
                if (isRegexp) {
                    origQuery = regexp;
                } else {
                    origQuery = keyword;
                }
                return highlightFactory.createInstance(objectId, keyword, isRegexp, origQuery);
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to retrieve Blackboard Attributes", ex); //NON-NLS
        }
        return null;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    /*
     * TODO (AUT-1849): Correct or remove peristent column reordering code
     *
     * Added to support this feature.
     */
//    @Override
//    public String getItemType() {
//        return "BlackboardArtifact"; //NON-NLS
//    }
}
