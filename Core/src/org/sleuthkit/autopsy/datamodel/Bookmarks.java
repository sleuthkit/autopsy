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

import java.awt.event.ActionEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponentinterfaces.BlackboardResultViewer;
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
 * @deprecated cosolidated under Tags
 * 
 * TODO bookmark hierarchy support (TSK_TAG_NAME with slashes)
 */
@Deprecated
public class Bookmarks implements AutopsyVisitableItem {

    public static final String NAME = "Bookmarks";
    private static final String FILE_BOOKMARKS_LABEL_NAME = "File Bookmarks";
    private static final String RESULT_BOOKMARKS_LABEL_NAME = "Result Bookmarks";
    //bookmarks are specializations of tags
    public static final String BOOKMARK_TAG_NAME = "Bookmark";
    private static final String BOOKMARK_ICON_PATH = "org/sleuthkit/autopsy/images/star-bookmark-icon-16.png";
    private static final Logger logger = Logger.getLogger(Bookmarks.class.getName());
    private SleuthkitCase skCase;
    private final Map<BlackboardArtifact.ARTIFACT_TYPE, List<BlackboardArtifact>> data =
            new EnumMap<BlackboardArtifact.ARTIFACT_TYPE, List<BlackboardArtifact>>(BlackboardArtifact.ARTIFACT_TYPE.class);

    public Bookmarks(SleuthkitCase skCase) {
        this.skCase = skCase;

    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return null; //v.visit(this);
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
                List<BlackboardArtifact> tagFiles = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE,
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME,
                        BOOKMARK_TAG_NAME);
                List<BlackboardArtifact> tagArtifacts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT,
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME,
                        BOOKMARK_TAG_NAME);

                data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE, tagFiles);
                data.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT, tagArtifacts);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Count not initialize bookmark nodes, ", ex);
            }


        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return null; // v.visit(this);
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
            return null; //v.visit(this);
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
            BlackboardArtifactNode bookmarkNode = null;

            int artifactTypeID = artifact.getArtifactTypeID();
            if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                final BlackboardArtifact sourceResult = Tags.getArtifactFromTag(artifact.getArtifactID());
                bookmarkNode = new BlackboardArtifactNode(artifact, BOOKMARK_ICON_PATH) {
                    @Override
                    public Action[] getActions(boolean bln) {
                        //Action [] actions = super.getActions(bln); //To change body of generated methods, choose Tools | Templates.
                        Action[] actions = new Action[1];
                        actions[0] = new AbstractAction("View Source Result") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                //open the source artifact in dir tree
                                if (sourceResult != null) {
                                    BlackboardResultViewer v = Lookup.getDefault().lookup(BlackboardResultViewer.class);
                                    v.viewArtifact(sourceResult);
                                }
                            }
                        };
                        return actions;
                    }
                };

                //add custom property
                final String NO_DESCR = "no description";
                String resultType = sourceResult.getDisplayName();
                NodeProperty resultTypeProp = new NodeProperty("Source Result Type",
                        "Result Type",
                        NO_DESCR,
                        resultType);
                bookmarkNode.addNodeProperty(resultTypeProp);

            } else {
                //file bookmark, no additional action
                bookmarkNode = new BlackboardArtifactNode(artifact, BOOKMARK_ICON_PATH);

            }
            return bookmarkNode;
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