/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Node wrapping a blackboard artifact object
 */
public class BlackboardArtifactNode extends DisplayableItemNode {

    private BlackboardArtifact artifact;
    private Content associated;
    private List<NodeProperty> customProperties;
    static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());

    /**
     * Construct blackboard artifact node from an artifact and using provided
     * icon
     *
     * @param artifact artifact to encapsulate
     * @param iconPath icon to use for the artifact
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact, String iconPath) {
        super(Children.LEAF, getLookups(artifact));

        this.artifact = artifact;
        //this.associated = getAssociatedContent(artifact);
        this.associated = this.getLookup().lookup(Content.class);
        this.setName(Long.toString(artifact.getArtifactID()));
        this.setDisplayName(associated.getName());
        this.setIconBaseWithExtension(iconPath);
    }

    /**
     * Construct blackboard artifact node from an artifact and using default
     * icon for artifact type
     *
     * @param artifact artifact to encapsulate
     */
    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        super(Children.LEAF, getLookups(artifact));

        this.artifact = artifact;
        //this.associated = getAssociatedContent(artifact);
        this.associated = this.getLookup().lookup(Content.class);
        this.setName(Long.toString(artifact.getArtifactID()));
        this.setDisplayName(associated.getName());
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/" + getIcon(BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID())));

    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }
        final String NO_DESCR = "no description";

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        fillPropertyMap(map, artifact);

        ss.put(new NodeProperty("Source File",
                "Source File",
                NO_DESCR,
                associated.getName()));

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            ss.put(new NodeProperty(entry.getKey(),
                    entry.getKey(),
                    NO_DESCR,
                    entry.getValue()));
        }

        String path = "";
        try {
            path = associated.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + associated);
        }
        final int artifactTypeID = artifact.getArtifactTypeID();

        //custom additional properties
        //TODO use addNodeProperty() instead of hardcoding here
        if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                || artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            ss.put(new NodeProperty("File Path",
                    "File Path",
                    NO_DESCR,
                    path));
        }

        //append custom node properties
        if (customProperties != null) {
            for (NodeProperty np : customProperties) {
                ss.put(np);
            }

        }

        return s;
    }

    /**
     * Add an additional custom node property to that node before it is
     * displayed
     *
     * @param np NodeProperty to add
     */
    public void addNodeProperty(NodeProperty np) {
        if (customProperties == null) {
            //lazy create the list
            customProperties = new ArrayList<NodeProperty>();
        }
        customProperties.add(np);

    }

    /**
     * Fill map with Artifact properties
     *
     * @param map map with preserved ordering, where property names/values are
     * put
     * @param artifact to extract properties from
     */
    private void fillPropertyMap(Map<String, Object> map, BlackboardArtifact artifact) {
        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                final int attributeTypeID = attribute.getAttributeTypeID();
                //skip some internal attributes that user shouldn't see
                if (attributeTypeID == ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()) {
                    continue;
                } else {
                    switch (attribute.getValueType()) {
                        case STRING:
                            String valString = attribute.getValueString();
                            map.put(attribute.getAttributeTypeDisplayName(),  valString == null ? "":valString);
                            break;
                        case INTEGER:
                            map.put(attribute.getAttributeTypeDisplayName(), attribute.getValueInt());
                            break;
                        case LONG:
                            if (attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()) {
                                map.put(attribute.getAttributeTypeDisplayName(), ContentUtils.getStringTime(attribute.getValueLong(), associated));
                            } else {
                                map.put(attribute.getAttributeTypeDisplayName(), attribute.getValueLong());
                            }
                            break;
                        case DOUBLE:
                            map.put(attribute.getAttributeTypeDisplayName(), attribute.getValueDouble());
                            break;
                        case BYTE:
                            map.put(attribute.getAttributeTypeDisplayName(), attribute.getValueBytes());
                            break;
                    }
                }
            }
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Getting attributes failed", ex);
        }
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    private static Lookup getLookups(BlackboardArtifact artifact) {
        Content content = getAssociatedContent(artifact);
        HighlightLookup highlight = getHighlightLookup(artifact, content);
        List<Object> forLookup = new ArrayList<Object>();
        forLookup.add(artifact);
        if (content != null) {
            forLookup.add(content);
        }
        if (highlight != null) {
            forLookup.add(highlight);
        }

        return Lookups.fixed(forLookup.toArray(new Object[forLookup.size()]));
    }

    private static Content getAssociatedContent(BlackboardArtifact artifact) {
        try {
            return artifact.getSleuthkitCase().getContentById(artifact.getObjectID());
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Getting file failed", ex);
        }
        throw new IllegalArgumentException("Couldn't get file from database");
    }

    private static HighlightLookup getHighlightLookup(BlackboardArtifact artifact, Content content) {
        if (artifact.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            return null;
        }
        Lookup lookup = Lookup.getDefault();
        HighlightLookup highlightFactory = lookup.lookup(HighlightLookup.class);
        try {
            List<BlackboardAttribute> attributes = artifact.getAttributes();
            String keyword = null;
            String regexp = null;
            String origQuery = null;
            for (BlackboardAttribute att : attributes) {
                final int attributeTypeID = att.getAttributeTypeID();
                if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                    keyword = att.getValueString();
                } else if (attributeTypeID == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()) {
                    regexp = att.getValueString();
                }
            }
            if (keyword != null) {
                boolean isRegexp = (regexp != null && !regexp.equals(""));
                if (isRegexp) {
                    origQuery = regexp;
                } else {
                    origQuery = keyword;
                }
                return highlightFactory.createInstance(content, keyword, isRegexp, origQuery);
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Failed to retrieve Blackboard Attributes", ex);
        }
        return null;
    }

    private String getIcon(BlackboardArtifact.ARTIFACT_TYPE type) {
        switch (type) {
            case TSK_WEB_BOOKMARK:
                return "bookmarks.png";
            case TSK_WEB_COOKIE:
                return "cookies.png";
            case TSK_WEB_HISTORY:
                return "history.png";
            case TSK_WEB_DOWNLOAD:
                return "downloads.png";
            case TSK_INSTALLED_PROG:
                return "programs.png";
            case TSK_RECENT_OBJECT:
                return "recent_docs.png";
            case TSK_DEVICE_ATTACHED:
                return "usb_devices.png";
            case TSK_WEB_SEARCH_QUERY:
                return "searchquery.png";
            case TSK_TAG_FILE:
                return "blue-tag-icon-16.png";
            case TSK_TAG_ARTIFACT:
                return "green-tag-icon-16.png";
            case TSK_METADATA_EXIF:
                return "camera-icon-16.png";
        }
        return "artifact-icon.png";
    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.ARTIFACT;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
