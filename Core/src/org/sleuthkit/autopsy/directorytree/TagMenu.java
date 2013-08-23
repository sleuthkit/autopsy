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
import java.util.TreeSet;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * The menu that results when one right-clicks on a file or artifact.
 */
public abstract class TagMenu extends JMenu {
    public TagMenu(String menuItemText) {
        super(menuItemText);

        // Create the 'Quick Tag' sub-menu and add it to the tag menu.
        JMenu quickTagMenu = new JMenu("Quick Tag");
        add(quickTagMenu);    

        // Get the existing tag names.
        TreeSet<String> tagNames = Tags.getAllTagNames();
        if (tagNames.isEmpty()) {
            JMenuItem empty = new JMenuItem("No tags");
            empty.setEnabled(false);
            quickTagMenu.add(empty);
        }
            
        // Add a menu item for each existing tag name to the 'Quick Tag' menu.
        for (final String tagName : tagNames) {
            JMenuItem tagNameItem = new JMenuItem(tagName);
            tagNameItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    applyTag(tagName, "");
                    refreshDirectoryTree();
                }
            });
            quickTagMenu.add(tagNameItem);
        }
        
        quickTagMenu.addSeparator();
            
        // Create the 'New Tag' menu item and add it to the 'Quick Tag' menu.
        JMenuItem newTagMenuItem = new JMenuItem("New Tag");
        newTagMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String tagName = CreateTagDialog.getNewTagNameDialog(null);
                if (tagName != null) {
                    applyTag(tagName, "");
                    refreshDirectoryTree();
                }
            }
        });
        quickTagMenu.add(newTagMenuItem);

        // Create the 'Tag and Comment' menu item and add it to the tag menu.
        JMenuItem tagAndCommentItem = new JMenuItem("Tag and Comment");
        tagAndCommentItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TagAndCommentDialog.CommentedTag commentedTag = TagAndCommentDialog.doDialog();
                if (null != commentedTag) {
                    applyTag(commentedTag.getName(), commentedTag.getComment());
                    refreshDirectoryTree();
                }
            }
        });
        add(tagAndCommentItem);        
    }
        
    private void refreshDirectoryTree() {
        //TODO instead should send event to node children, which will call its refresh() / refreshKeys()
        DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
        viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
    }
    
    protected abstract void applyTag(String tagName, String comment);    
}
