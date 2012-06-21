/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Support for TSK_EMAIL_MSG nodes and displaying emails in the directory tree
 * Email messages are grouped into parent folders, and the folders are grouped into parent accounts
 * if TSK_PATH is available to define the relationship structure for every message
 */
public class EmailExtracted implements AutopsyVisitableItem {
    
    private static final String LABEL_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getLabel();
    private static final String DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getDisplayName();
    private static final Logger logger = Logger.getLogger(EmailExtracted.class.getName());
    
    private static final String MAIL_ACCOUNT = "Account";
    private static final String MAIL_FOLDER = "Folder";
    
    private static final String MAIL_PATH_SEPARATOR = "/";
    
    private SleuthkitCase skCase;
    private Map<String, Map<String,List<Long>>> accounts;
    
    public EmailExtracted(SleuthkitCase skCase) {
        this.skCase = skCase;
        accounts = new LinkedHashMap<String, Map<String,List<Long>>>();
    }
    
    private void initArtifacts() {
        accounts.clear();
        try {
            int artId = BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID();
            int pathAttrId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID();
            String query = "SELECT value_text,blackboard_attributes.artifact_id,attribute_type_id "
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " 
                    + "attribute_type_id=" + pathAttrId 
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id"
                    + " AND blackboard_artifacts.artifact_type_id=" + artId;
            ResultSet rs = skCase.runQuery(query);
            while(rs.next()){
                final String path = rs.getString("value_text");
                final long artifactId = rs.getLong("artifact_id");
                final Map<String,String> parsedPath = parsePath(path);
                final String account = parsedPath.get(MAIL_ACCOUNT);
                final String folder = parsedPath.get(MAIL_FOLDER);
                
                Map<String,List<Long>> folders = accounts.get(account);
                if (folders == null) {
                    folders = new LinkedHashMap<String,List<Long>>();
                    accounts.put(account, folders);
                }
                List<Long> messages = folders.get(folder);
                if (messages == null) {
                    messages = new ArrayList<Long>();
                    folders.put(folder, messages);
                }
                messages.add(artifactId);
            }
            skCase.closeRunQuery(rs);
           
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Cannot initialize email extraction", ex);
        }
    }
    
    
    private static Map<String,String> parsePath(String path) {
        Map<String,String> parsed = new HashMap<String,String>();
        String [] split = path.split(MAIL_PATH_SEPARATOR);
        if (split.length < 3)
            return null;
        
        parsed.put(MAIL_ACCOUNT, split[1]);
        parsed.put(MAIL_FOLDER, split[2]);
        return parsed;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }
    
    /**
     * Mail root node grouping all mail accounts
     */
    public class EmailExtractedRootNode extends AbstractNode implements DisplayableItemNode{

        public EmailExtractedRootNode() {
            super(Children.create(new EmailExtractedRootChildren(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(LABEL_NAME);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/mail-icon-16.png");
            initArtifacts();
        }
        
        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
        
        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("Name",
                    "Name",
                    "no description",
                    getName()));
            
            return s;
        }
    }
    
    /**
     * Mail root child node creating each account node
     */
    private class EmailExtractedRootChildren extends ChildFactory<String> {

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(accounts.keySet());
            return true;
        }
        
        @Override
        protected Node createNodeForKey(String key) {
            return new EmailExtractedAccountNode(key, accounts.get(key));
        }
    }
    
    /**
     * Account node representation
     */
    public class EmailExtractedAccountNode extends AbstractNode implements DisplayableItemNode {
        
        public EmailExtractedAccountNode(String name, Map<String,List<Long>> children) {
            super(Children.create(new EmailExtractedAccountChildrenNode(children), true), Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(name + " (" + children.size() + ")");
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/account-icon-16.png");
        }
        
        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("Name",
                    "Name",
                    "no description",
                    getName()));
            
            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
    }
    
    /**
     * Account node child creating sub nodes for every folder
     */
    private class EmailExtractedAccountChildrenNode extends ChildFactory<String> {

        private Map<String,List<Long>> folders;

        private EmailExtractedAccountChildrenNode(Map<String,List<Long>> folders) {
            super();
            this.folders = folders;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(folders.keySet());
           
            return true;
        }
        
        @Override
        protected Node createNodeForKey(String key) {
            return new EmailExtractedFolderNode(key, folders.get(key));
        }
    }
    
    /**
     * Node representing mail folder
     */
    public class EmailExtractedFolderNode extends AbstractNode implements DisplayableItemNode {
        
        public EmailExtractedFolderNode(String name, List<Long> children) {
            super(Children.create(new EmailExtractedFolderChildrenNode(children), true), Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(name + " (" + children.size() + ")");
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-16.png");
        }
        
        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("Name",
                    "Name",
                    "no description",
                    getName()));
            
            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
    }
    
    /**
     * Node representing mail folder content (mail messages)
     */
    private class EmailExtractedFolderChildrenNode extends ChildFactory<BlackboardArtifact> {

        private List<Long> messages;

        private EmailExtractedFolderChildrenNode(List<Long> messages) {
            super();
            this.messages = messages;        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            for (long l : messages) {
                try {
                    //TODO: bulk artifact gettings
                    list.add(skCase.getBlackboardArtifact(l));
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Error creating mail messages nodes", ex);
                }
            }
            return true;
        }
        
        @Override
        protected Node createNodeForKey(BlackboardArtifact artifact) {
            return new BlackboardArtifactNode(artifact);
        }
    }
}
