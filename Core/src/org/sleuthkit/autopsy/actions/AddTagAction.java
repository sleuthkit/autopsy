/*
* Autopsy Forensic Browser
*
* Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract base class for Actions that allow users to tag SleuthKit data
 * model objects.
 */
abstract class AddTagAction extends AbstractAction implements Presenter.Popup {
    
    private static final String NO_COMMENT = "";
    
    AddTagAction(String menuText) {
        super(menuText);
    }
    
    @Override
    public JMenuItem getPopupPresenter() {
        return new TagMenu();
    }
    
    /**
     * Subclasses of AddTagAction, should not override actionPerformed, but
     * instead override addTag.
     *
     * @param event
     */
    @Override
    @SuppressWarnings("NoopMethodInAbstractClass")
    public void actionPerformed(ActionEvent event) {
    }
    
    /**
     * Template method to allow derived classes to provide a string for a menu
     * item label.
     */
    abstract protected String getActionDisplayName();
    
    /**
     * Template method to allow derived classes to add the indicated tag and
     * comment to one or more SleuthKit data model objects.
     */
    abstract protected void addTag(TagName tagName, String comment);
    
    /**
     * Instances of this class implement a context menu user interface for
     * creating or selecting a tag name for a tag and specifying an optional tag
     * comment.
     */
    // @@@ This user interface has some significant usability issues and needs
    // to be reworked.
    private class TagMenu extends JMenu {
        
        TagMenu() {
            super(getActionDisplayName());
            
            // Get the current set of tag names.
            TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
            List<TagName> tagNames = null;
            try {
                tagNames = tagsManager.getAllTagNames();
                Collections.sort(tagNames);
            } catch (TskCoreException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }
            
            // Create a "Quick Tag" sub-menu.
            JMenu quickTagMenu = new JMenu(NbBundle.getMessage(this.getClass(), "AddTagAction.quickTag"));
            add(quickTagMenu);
            
            // Each tag name in the current set of tags gets its own menu item in
            // the "Quick Tags" sub-menu. Selecting one of these menu items adds
            // a tag with the associated tag name.
            if (null != tagNames && !tagNames.isEmpty()) {
                for (final TagName tagName : tagNames) {
                    JMenuItem tagNameItem = new JMenuItem(tagName.getDisplayName());
                    tagNameItem.addActionListener((ActionEvent e) -> {
                        addTag(tagName, NO_COMMENT);
                    });
                    quickTagMenu.add(tagNameItem);
                }
            } else {
                JMenuItem empty = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.noTags"));
                empty.setEnabled(false);
                quickTagMenu.add(empty);
            }
            
            quickTagMenu.addSeparator();
            
            // The "Quick Tag" menu also gets an "Choose Tag..." menu item.
            // Selecting this item initiates a dialog that can be used to create
            // or select a tag name and adds a tag with the resulting name.
            JMenuItem newTagMenuItem = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.newTag"));
            newTagMenuItem.addActionListener((ActionEvent e) -> {
                TagName tagName = GetTagNameDialog.doDialog();
                if (null != tagName) {
                    addTag(tagName, NO_COMMENT);
                }
            });
            quickTagMenu.add(newTagMenuItem);
            
            // Create a "Choose Tag and Comment..." menu item. Selecting this item initiates
            // a dialog that can be used to create or select a tag name with an
            // optional comment and adds a tag with the resulting name.
            JMenuItem tagAndCommentItem = new JMenuItem(
                    NbBundle.getMessage(this.getClass(), "AddTagAction.tagAndComment"));
            tagAndCommentItem.addActionListener((ActionEvent e) -> {
                GetTagNameAndCommentDialog.TagNameAndComment tagNameAndComment = GetTagNameAndCommentDialog.doDialog();
                if (null != tagNameAndComment) {
                    addTag(tagNameAndComment.getTagName(), tagNameAndComment.getComment());
                }
            });
            add(tagAndCommentItem);
            
        }
    }
}
