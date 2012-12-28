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
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.Bookmarks;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Action on a file or artifact that bookmarks a file and/or artifact
 * and reloads the bookmark view.
 * Supports bookmarking of a fs file, directory and layout file and layout
 * directory (virtual files/dirs for unalloc content) 
 * 
 * TODO add use enters description and hierarchy (TSK_TAG_NAME with slashes)
 */
public class TagResultAction extends AbstractAction implements Presenter.Popup {

    private static final Logger logger = Logger.getLogger(TagFileAction.class.getName());
    //content to bookmark
    private BlackboardArtifact tagArtifact;
    
    public TagResultAction(BlackboardArtifact artifact) {
        tagArtifact = artifact;
    }
    
    private String getComment() {
        String comment = JOptionPane.showInputDialog(null,
                "Please enter a comment for the tag:",
                "Tag Comment",
                JOptionPane.PLAIN_MESSAGE);
        if(comment == null || comment.isEmpty()) {
            comment = "No Comment";
        }
        return comment;
    }
    
    private void refreshDirectoryTree() {
        DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();  
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenu result = new JMenu("Tag Result");
        
        JMenuItem contentItem = new JMenuItem("Bookmark Result");
        contentItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                Tags.createBookmark(tagArtifact, getComment());
                refreshDirectoryTree();
            }
            
        });
        result.add(contentItem);
        result.addSeparator();
        
        JMenuItem newTagItem = new JMenuItem("Create a new tag");
        newTagItem.addActionListener(new ActionListener() {
                
            @Override
            public void actionPerformed(ActionEvent e) {
                Map<String, String> tagMap = new CreateTagDialog(new JFrame(), true).display();
                if (tagMap != null) {
                    Tags.createTag(tagArtifact, tagMap.get("Name"), tagMap.get("Comment"));
                    refreshDirectoryTree();
                }
            }

        });
        result.add(newTagItem);
        result.addSeparator();
        
        List<String> tagNames = Tags.getTagNames();
        if (tagNames.isEmpty()) {
            JMenuItem empty = new JMenuItem("No tags");
            empty.setEnabled(false);
            result.add(empty);
        } else {
            for (final String tagName : Tags.getTagNames()) {
                if (tagName.equals(Bookmarks.BOOKMARK_TAG_NAME)) {
                    continue;
                }
                JMenuItem tagItem = new JMenuItem(tagName);
                tagItem.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Tags.createTag(tagArtifact, tagName, getComment());
                        refreshDirectoryTree();
                    }

                });
                result.add(tagItem);
            }
        }
        
        return result;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Do nothing - this action should never be performed
        // Submenu actions are invoked instead
    }
}