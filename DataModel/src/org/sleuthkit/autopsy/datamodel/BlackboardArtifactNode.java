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

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author dfickling
 */
public class BlackboardArtifactNode extends AbstractNode implements DisplayableItemNode{
    
    BlackboardArtifact artifact;
    static final Logger logger = Logger.getLogger(BlackboardArtifactNode.class.getName());

    public BlackboardArtifactNode(BlackboardArtifact artifact) {
        super(Children.LEAF, Lookups.singleton(new ArtifactStringContent(artifact)));
        this.artifact = artifact;
        this.setName(artifact.getArtifactTypeName());
        this.setDisplayName(artifact.getDisplayName());
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/artifact-icon.png");
        
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

        Map<Integer, Object> map = new LinkedHashMap<Integer, Object>();
        fillPropertyMap(map, artifact);
        
        ss.put(new NodeProperty("Artifact Name",
                                "Artifact Name",
                                "no description",
                                artifact.getDisplayName()));
        
        ATTRIBUTE_TYPE[] attributeTypes = ATTRIBUTE_TYPE.values();
        for(Map.Entry<Integer, Object> entry : map.entrySet()){
            if(attributeTypes.length > entry.getKey()){
                ss.put(new NodeProperty(attributeTypes[entry.getKey()-1].getDisplayName(),
                        attributeTypes[entry.getKey()-1].getDisplayName(),
                        NO_DESCR,
                        entry.getValue()));
            }
        }
        return s;
    }

    /**
     * Fill map with Artifact properties
     * @param map, with preserved ordering, where property names/values are put
     * @param content to extract properties from
     */
    public static void fillPropertyMap(Map<Integer, Object> map, BlackboardArtifact artifact) {
        try {
            for(BlackboardAttribute attribute : artifact.getAttributes()){
                switch(attribute.getValueType()){
                    case STRING:
                        map.put(attribute.getAttributeTypeID(), attribute.getValueString());
                        break;
                    case INTEGER:
                        map.put(attribute.getAttributeTypeID(), attribute.getValueInt());
                        break;
                    case LONG:
                        map.put(attribute.getAttributeTypeID(), attribute.getValueLong());
                        break;
                    case DOUBLE:
                        map.put(attribute.getAttributeTypeID(), attribute.getValueDouble());
                        break;
                    case BYTE:
                        map.put(attribute.getAttributeTypeID(), attribute.getValueBytes());
                        break;
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
    
    public File getAssociatedFile(){
        try {
            return artifact.getSleuthkitCase().getFileById(artifact.getObjectID());
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "SQL query threw exception", ex);
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Getting file failed", ex);
        }
        throw new IllegalArgumentException("Couldn't get file from database");
    }
    
    public Directory getParentDirectory(){
        try{
            return getAssociatedFile().getParentDirectory();
        } catch (TskException ex) {
            logger.log(Level.WARNING, "File has no parent", ex);
        }
        throw new IllegalArgumentException("Couldn't get context");
    }

    public FileNode getContentNode() {
        return new FileNode(getAssociatedFile());
    }
    
    public DirectoryNode getContextNode() {
        return new DirectoryNode(getParentDirectory());
    }
    
}
