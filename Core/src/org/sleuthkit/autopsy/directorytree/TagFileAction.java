/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.nodes.Node;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.autopsy.directorytree.TagDialog.TagDialogResult;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;

/**
 * Action on a file or artifact that bookmarks a file and/or artifact and
 * reloads the bookmark view. Supports bookmarking of a fs file, directory and
 * layout file and layout directory (virtual files/dirs for unalloc content)
 *
 * TODO add use enters description and hierarchy (TSK_TAG_NAME with slashes)
 */
public class TagFileAction extends AbstractAction implements Presenter.Popup {

    private static final Logger logger = Logger.getLogger(TagFileAction.class.getName());
    //content to bookmark
    private AbstractFile tagFile;
    private final InitializeBookmarkFileV initializer = new InitializeBookmarkFileV();

    public TagFileAction(Node contentNode) {
        Content content = contentNode.getLookup().lookup(Content.class);
        tagFile = content.accept(initializer);
    }

    public TagFileAction(Content content) {
        tagFile = content.accept(initializer);
    }

    private void refreshDirectoryTree() {
        DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenu result = new JMenu("Tag Source File");

        JMenuItem contentItem = new JMenuItem("Bookmark Source File");
        contentItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final TagDialog tagDialog = new TagDialog(TagDialog.Type.BOOKMARK, "Bookmark Source File", null, "Bookmark", false);
                tagDialog.setVisible(true);
                TagDialogResult inputResult = tagDialog.getResult();
                if (inputResult.isAccept()) {
                    Tags.createBookmark(tagFile, inputResult.getComment());
                    refreshDirectoryTree();
                }
            }
        });
        result.add(contentItem);
        result.addSeparator();

        JMenuItem newTagItem = new JMenuItem("Create a new tag");
        newTagItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String newTagName = new CreateTagDialog(new JFrame(), true).display();
                if (newTagName != null) {
                    //get comment
                    final TagDialog tagDialog = new TagDialog(TagDialog.Type.TAG, "Tag Source File", null, newTagName, false);
                    tagDialog.setVisible(true);
                    TagDialogResult inputResult = tagDialog.getResult();
                    if (inputResult.isAccept()) {
                        Tags.createTag(tagFile, newTagName, inputResult.getComment());
                        refreshDirectoryTree();
                    }
                }
            }
        });
        result.add(newTagItem);
        result.addSeparator();

        final List<String> tagNames = Tags.getTagNames();
        if (tagNames.isEmpty()) {
            JMenuItem empty = new JMenuItem("No tags");
            empty.setEnabled(false);
            result.add(empty);
        } else {
            for (final String tagName : tagNames) {
                if (tagName.equals(Tags.BOOKMARK_TAG_NAME)) {
                    //skip showing bookmarks in tags menu, as they have a separate menu
                    continue;
                }
                JMenuItem tagItem = new JMenuItem(tagName);
                tagItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final TagDialog tagDialog = new TagDialog(TagDialog.Type.TAG, "Tag Source File", tagNames, tagName, true);
                        tagDialog.setVisible(true);
                        TagDialogResult inputResult = tagDialog.getResult();
                        if (inputResult.isAccept()) {
                            Tags.createTag(tagFile, inputResult.getSelectedTag(), inputResult.getComment());
                            refreshDirectoryTree();
                        }


                    }
                });
                result.add(tagItem);
            }
        }

        return result;
    }

    /**
     * Returns the FsContent if it is supported, otherwise null
     */
    private static class InitializeBookmarkFileV extends ContentVisitor.Default<AbstractFile> {

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.File f) {
            return f;
        }

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.LayoutFile lf) {
            return lf;
        }
        
        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.DerivedFile lf) {
            return lf;
        }

        @Override
        public AbstractFile visit(org.sleuthkit.datamodel.VirtualDirectory ld) {
            return ld;
        }

        @Override
        public AbstractFile visit(Directory dir) {
            return ContentUtils.isDotDirectory(dir) ? null : dir;
        }

        @Override
        protected AbstractFile defaultVisit(Content cntnt) {
            return null;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Do nothing - this action should never be performed
        // Submenu actions are invoked instead
    }
}