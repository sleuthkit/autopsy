/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An abstract base class for Actions that allow users to tag SleuthKit data
 * model objects.
 */
abstract class AddTagAction extends AbstractAction implements Presenter.Popup {

    private static final long serialVersionUID = 1L;
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

        private static final long serialVersionUID = 1L;

        TagMenu() {
            super(getActionDisplayName());

            // Get the current set of tag names.
            Map<String, TagName> tagNamesMap = null;
            try {
                TagsManager tagsManager = Case.getOpenCase().getServices().getTagsManager();
                tagNamesMap = new TreeMap<>(tagsManager.getDisplayNamesToTagNamesMap());
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }

            // Create a "Quick Tag" sub-menu.
            JMenu quickTagMenu = new JMenu(NbBundle.getMessage(this.getClass(), "AddTagAction.quickTag"));
            add(quickTagMenu);

            // Each tag name in the current set of tags gets its own menu item in
            // the "Quick Tags" sub-menu. Selecting one of these menu items adds
            // a tag with the associated tag name.
            if (null != tagNamesMap && !tagNamesMap.isEmpty()) {
                for (Map.Entry<String, TagName> entry : tagNamesMap.entrySet()) {
                    String tagDisplayName = entry.getKey();
                    String notableString = entry.getValue().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                    JMenuItem tagNameItem = new JMenuItem(tagDisplayName + notableString);
                    // for the bookmark tag name only, added shortcut label
                    if (tagDisplayName.equals(NbBundle.getMessage(AddTagAction.class, "AddBookmarkTagAction.bookmark.text"))) {
                        tagNameItem.setAccelerator(AddBookmarkTagAction.BOOKMARK_SHORTCUT);
                    }

                    tagNameItem.addActionListener((ActionEvent e) -> {
                        getAndAddTag(entry.getKey(), entry.getValue(), NO_COMMENT);
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

        /**
         * Method to add to the action listener for each menu item. Allows a tag
         * display name to be added to the menu with an action listener without
         * having to instantiate a TagName object for it. When the method is
         * called, the TagName object is created here if it doesn't already
         * exist.
         *
         * @param tagDisplayName display name for the tag name
         * @param tagName        TagName object associated with the tag name,
         *                       may be null
         * @param comment        comment for the content or artifact tag
         */
        private void getAndAddTag(String tagDisplayName, TagName tagName, String comment) {
            Case openCase;
            try {
                openCase = Case.getOpenCase();
            } catch (NoCurrentCaseException ex) {
                Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
                return;
            }

            if (tagName == null) {
                try {
                    tagName = openCase.getServices().getTagsManager().addTagName(tagDisplayName);
                } catch (TagsManager.TagNameAlreadyExistsException ex) {
                    try {
                        tagName = openCase.getServices().getTagsManager().getDisplayNamesToTagNamesMap().get(tagDisplayName);
                    } catch (TskCoreException ex1) {
                        Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, tagDisplayName + " already exists in database but an error occurred in retrieving it.", ex1); //NON-NLS
                    } 
                } catch (TskCoreException ex) {
                    Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, "Error adding " + tagDisplayName + " tag name", ex); //NON-NLS
                } 
            }
            addTag(tagName, comment);
        }
    }
}
