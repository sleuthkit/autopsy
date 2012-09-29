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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Support for bookmark (file and result/artifact) nodes and displaying
 * bookmarks in the directory tree Bookmarks are divided into file and result
 * children bookmarks.
 *
 * Bookmarks are specialized tags - TSK_TAG_NAME starts with File Bookmark or
 * Result Bookmark
 *
 * TODO bookmark hierarchy support (TSK_TAG_NAME with slashes)
 */
public class Bookmarks implements AutopsyVisitableItem {

    public static final String NAME = "Bookmarks";
    
    private static final String FILE_BOOKMARKS_LABEL_NAME = "File Bookmarks";
    private static final String RESULT_BOOKMARKS_LABEL_NAME = "Result Bookmarks";
    //bookmarks are specializations of tags
    public static final String FILE_BOOKMARK_TAG_NAME = "File Bookmark";
    public static final String RESULT_BOOKMARK_TAG_NAME = "Result Bookmark";
    private static final String BOOKMARK_ICON_PATH = "org/sleuthkit/autopsy/images/star-bookmark-icon-16.png";
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
            super(Children.create(new BookmarksRootChildren(), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension(BOOKMARK_ICON_PATH);
            initData();
        }

        private void initData() {
            data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE, null);
            data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT, null);

            try {

                //filter out tags that are not bookmarks
                //we get bookmarks that have tag names that start with predefined names, preserving the bookmark hierarchy
                data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE,
                        skCase.getBlackboardArtifacts(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME, FILE_BOOKMARK_TAG_NAME, true));

                data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT,
                        skCase.getBlackboardArtifacts(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME, RESULT_BOOKMARK_TAG_NAME, true));


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

            String name = null;
            if (bookType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE)) {
                name = FILE_BOOKMARKS_LABEL_NAME;
            } else if (bookType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT)) {
                name = RESULT_BOOKMARKS_LABEL_NAME;
            }

            super.setName(name);
            super.setDisplayName(name + " (" + bookmarks.size() + ")");

            this.setIconBaseWithExtension(BOOKMARK_ICON_PATH);
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
            return new BlackboardArtifactNode(artifact, BOOKMARK_ICON_PATH);
        }
    }

    /**
     * Links existing blackboard artifact (a tag) to this artifact. Linkage is
     * made using TSK_TAGGED_ARTIFACT attribute.
     */
    void addArtifactTag(BlackboardArtifact art, BlackboardArtifact tag) throws TskCoreException {
        if (art.equals(tag)) {
            throw new TskCoreException("Cannot tag the same artifact: id" + art.getArtifactID());
        }
        BlackboardAttribute attrLink = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
                "", art.getArtifactID());
        tag.addAttribute(attrLink);
    }

    /**
     * Get tag artifacts linked to the artifact
     *
     * @param art artifact to get tags for
     * @return list of children artifacts or an empty list
     * @throws TskCoreException exception thrown if a critical error occurs
     * within tsk core and child artifact could not be queried
     */
    List<BlackboardArtifact> getTagArtifacts(BlackboardArtifact art) throws TskCoreException {
        return skCase.getBlackboardArtifacts(ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT, art.getArtifactID());
    }
}