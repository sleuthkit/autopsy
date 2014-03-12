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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Node wrapping a blackboard artifact object.  This represents a single artifact. 
 * Its parent is typically an ArtifactTypeNode.
 */
public class BlackboardArtifactNode extends DisplayableItemNode {

    private BlackboardArtifact artifact;
    private Content associated;
    private List<NodeProperty<? extends Object>> customProperties;
    static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());
    /**
     * Artifact types which should have the associated content's full unique path
     * as a property.
     */
    private static final Integer[] SHOW_UNIQUE_PATH = new Integer[] { 
        BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID(),
        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
    };

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
        final String NO_DESCR = NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.noDesc.text");

        Map<String, Object> map = new LinkedHashMap<>();
        fillPropertyMap(map, artifact);

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.srcFile.name"),
                                NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.srcFile.displayName"),
                                NO_DESCR,
                                associated.getName()));

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
                    logger.log(Level.WARNING, "Could not find expected TSK_FILE_TYPE_SIG attribute.");
                } else {
                    ss.put(new NodeProperty<>(
                            NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.mimeType.name"),
                            NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.mimeType.displayName"),
                            NO_DESCR,
                            actualMimeType));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error while searching for TSK_FILE_TYPE_SIG attribute: ", ex);
            }            
        }        
        
        if (Arrays.asList(SHOW_UNIQUE_PATH).contains(artifactTypeId)) {
            String sourcePath = "";
            try {
                sourcePath = associated.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get unique path from: {0}", associated.getName());
            }

            if (sourcePath.isEmpty() == false) {
                ss.put(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.filePath.name"),
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.filePath.displayName"),
                        NO_DESCR,
                        sourcePath));
            }
        } else {
            String dataSource = "";
            try {
                Image image = associated.getImage();
                if (image != null) {
                    dataSource = image.getName();
                } else {
                    dataSource = getRootParentName();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to get image name from {0}", associated.getName());
            }
            
            if (dataSource.isEmpty() == false) {
                ss.put(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.dataSrc.name"),
                        NbBundle.getMessage(this.getClass(), "BlackboardArtifactNode.createSheet.dataSrc.displayName"),
                        NO_DESCR,
                        dataSource));
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
            logger.log(Level.WARNING, "Failed to get parent name from {0}", associated.getName());
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
        if (customProperties == null) {
            //lazy create the list
            customProperties = new ArrayList<>();
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
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()
                        || attributeTypeID == ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
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
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID() 
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED.getTypeID()
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID()
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID() 
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()
                                    || attributeTypeID == ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID() ) {
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
        List<Object> forLookup = new ArrayList<>();
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
        throw new IllegalArgumentException(
                NbBundle.getMessage(BlackboardArtifactNode.class, "BlackboardArtifactNode.getAssocCont.exception.msg"));
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

    // @@@ TODO: Merge with ArtifactTypeNode.getIcon()
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
            case TSK_CONTACT:
                return "contact.png";
            case TSK_MESSAGE:
                return "message.png";
            case TSK_CALLLOG:
                return "calllog.png";
            case TSK_CALENDAR_ENTRY:
                return "calendar.png";
            case TSK_SPEED_DIAL_ENTRY:
                return "speeddialentry.png";
            case TSK_BLUETOOTH_PAIRING:
                return "bluetooth.png";
            case TSK_GPS_BOOKMARK:
                return "gpsfav.png";
            case TSK_GPS_LAST_KNOWN_LOCATION:
                return "gps-lastlocation.png";
            case TSK_GPS_SEARCH:
                return "gps-search.png";
            case TSK_SERVICE_ACCOUNT:
                return "account-icon-16.png";
            case TSK_ENCRYPTION_DETECTED:
                return "encrypted-file.png";
            case TSK_EXT_MISMATCH_DETECTED:
                return "mismatch-16.png";
                
        }
        return "artifact-icon.png";
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
