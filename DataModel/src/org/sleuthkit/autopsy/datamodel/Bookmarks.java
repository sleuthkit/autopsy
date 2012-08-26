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

import java.util.HashMap;
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
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Support for bookmark (file and result/artifact) nodes and displaying
 * bookmarks in the directory tree Bookmarks are divided into file and result
 * children bookmarks.
 */
public class Bookmarks implements AutopsyVisitableItem {

    private static final String LABEL_NAME = "Bookmarks";
    private static final String DISPLAY_NAME = LABEL_NAME;
    private static final Logger logger = Logger.getLogger(Bookmarks.class.getName());
    private SleuthkitCase skCase;
    private final Map<BlackboardArtifact.ARTIFACT_TYPE, List<BlackboardArtifact>> data =
            new HashMap<BlackboardArtifact.ARTIFACT_TYPE, List<BlackboardArtifact>>();

    public Bookmarks(SleuthkitCase skCase) {
        this.skCase = skCase;

    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * bookmarks root node with file/result bookmarks
     */
    public class BookmarksRootNode extends DisplayableItemNode {

        public BookmarksRootNode() {
            super(Children.create(new BookmarksRootChildren(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(LABEL_NAME);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/mail-icon-16.png");
            initData();
        }

        private void initData() {
            data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_BOOKMARK_FILE, null);
            data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_BOOKMARK_ARTIFACT, null);

            try {
                for (BlackboardArtifact.ARTIFACT_TYPE artType : data.keySet()) {
                    data.put(artType, skCase.getBlackboardArtifacts(artType));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Count not initialize bookmark nodes, ", ex);
            }


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

        @Override
        public TYPE getDisplayableItemNodeType() {
            return TYPE.ARTIFACT;
        }
    }

    /**
     * bookmarks root child node creating types of bookmarks nodes
     */
    private class BookmarksRootChildren extends ChildFactory<BlackboardArtifact.ARTIFACT_TYPE> {

        @Override
        protected boolean createKeys(List<BlackboardArtifact.ARTIFACT_TYPE> list) {
            for (BlackboardArtifact.ARTIFACT_TYPE artType : data.keySet()) {
                list.add(artType);
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact.ARTIFACT_TYPE key) {
            return new BookmarksNodeRoot(key, data.get(key));
        }
    }

    /**
     * Bookmarks node representation (file or result)
     */
    public class BookmarksNodeRoot extends DisplayableItemNode {

        public BookmarksNodeRoot(BlackboardArtifact.ARTIFACT_TYPE bookType, List<BlackboardArtifact> bookmarks) {
            super(Children.create(new BookmarksChildrenNode(bookmarks), true), Lookups.singleton(bookType.getDisplayName()));
            final String name = bookType.getDisplayName();
            super.setName(name);
            super.setDisplayName(name + " (" + bookmarks.size() + ")");
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

        @Override
        public TYPE getDisplayableItemNodeType() {
            return TYPE.ARTIFACT;
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }
    }

    /**
     * Node representing mail folder content (mail messages)
     */
    private class BookmarksChildrenNode extends ChildFactory<BlackboardArtifact> {

        private List<BlackboardArtifact> bookmarks;

        private BookmarksChildrenNode(List<BlackboardArtifact> bookmarks) {
            super();
            this.bookmarks = bookmarks;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            list.addAll(bookmarks);
            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact artifact) {
            return new BlackboardArtifactNode(artifact);
        }
    }
}