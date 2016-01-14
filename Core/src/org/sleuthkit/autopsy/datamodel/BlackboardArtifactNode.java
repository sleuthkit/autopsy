/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Node wrapping a blackboard artifact object. This is generated from several
 * places in the tree.
 */
public class BlackboardArtifactNode extends DisplayableItemNode {

    private final BlackboardArtifact artifact;
    private final Content associated;
    private List<NodeProperty<? extends Object>> customProperties;
    static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());
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

    /**
     * Set the filter node display name. The value will either be the file name
     * or something along the lines of e.g. "Messages Artifact" for keyword hits
     * on artifacts.
     */
    private void setDisplayName() {
        String displayName = "";
        if (associated != null) {
            displayName = associated.getName();
        }

        // If this is a node for a keyword hit on an artifact, we set the 
        // display name to be the artifact type name followed by " Artifact"
        // e.g. "Messages Artifact".
        if (artifact != null && artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            try {
                for (BlackboardAttribute attribute : artifact.getAttributes()) {
                    if (attribute.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                        BlackboardArtifact associatedArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifact(attribute.getValueLong());
                        if (associatedArtifact != null) {
                            displayName = associatedArtifact.getDisplayName() + " Artifact"; // NON-NLS
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
            String ext = "";
            if (associated instanceof AbstractFile) {
                AbstractFile af = (AbstractFile) associated;
                ext = af.getNameExtension();
            }
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.ext.name"),
                    NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.ext.displayName"),
                    NO_DESCR,
                    ext));

            try {
                String actualMimeType = "";
                ArrayList<BlackboardArtifact> artList = associated.getAllArtifacts();
                for (BlackboardArtifact art : artList) {
                    List<BlackboardAttribute> atrList = art.getAttributes();
                    for (BlackboardAttribute att : atrList) {
                        if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID()) {
                            actualMimeType = att.getValueString();
                        }
                    }
                }
                if (actualMimeType.isEmpty()) {
                    logger.log(Level.WARNING, "Could not find expected TSK_FILE_TYPE_SIG attribute."); //NON-NLS
                } else {
                    ss.put(new NodeProperty<>(
                            NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.mimeType.name"),
                            NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.mimeType.displayName"),
                            NO_DESCR,
                            actualMimeType));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error while searching for TSK_FILE_TYPE_SIG attribute: ", ex); //NON-NLS
            }
        }

        if (Arrays.asList(SHOW_UNIQUE_PATH).contains(artifactTypeId)) {
            String sourcePath = "";
            try {
                sourcePath = associated.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get unique path from: {0}", associated.getName()); //NON-NLS
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
                logger.log(Level.WARNING, "Failed to get image name from {0}", associated.getName()); //NON-NLS
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
            logger.log(Level.WARNING, "Failed to get parent name from {0}", associated.getName()); //NON-NLS
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
    public <T> void addNodeProperty(NodeProperty<T> np) {
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
    @SuppressWarnings("deprecation") // TODO: Remove this when TSK_TAGGED_ARTIFACT rows are removed in a database upgrade.
    private void fillPropertyMap(Map<String, Object> map, BlackboardArtifact artifact) {
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                final int attributeTypeID = attribute.getAttributeTypeID();
                //skip some internal attributes that user shouldn't see
                if (attributeTypeID == ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                } else if (attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID()) {
                    map.put(attribute.getAttributeTypeDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), associated));
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
                    map.put(attribute.getAttributeTypeDisplayName(), value);
                } else {
                    map.put(attribute.getAttributeTypeDisplayName(), attribute.getDisplayString());
                }
            }
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Getting attributes failed", ex); //NON-NLS
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
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Getting file failed", ex); //NON-NLS
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
                final int attributeTypeID = att.getAttributeTypeID();
                if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                    keyword = att.getValueString();
                } else if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()) {
                    regexp = att.getValueString();
                } else if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                    objectId = att.getValueLong();
                }
            }
            if (keyword != null) {
                boolean isRegexp = (regexp != null && !regexp.equals(""));
                String origQuery;
                if (isRegexp) {
                    origQuery = regexp;
                } else {
                    origQuery = keyword;
                }
                return highlightFactory.createInstance(objectId, keyword, isRegexp, origQuery);
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Failed to retrieve Blackboard Attributes", ex); //NON-NLS
        }
        return null;
    }
    
    @Override
    public boolean isLeafTypeNode() {
        return true;
    }

    @Override
    public String getItemType() {
        return "BlackboardArtifact";
    }
}
