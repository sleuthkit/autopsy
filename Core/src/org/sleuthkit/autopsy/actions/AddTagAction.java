/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2021 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An abstract super class for Actions that allow users to tag Sleuth Kit data
 * model objects.
 */
abstract class AddTagAction extends AbstractAction implements Presenter.Popup {

    private static final long serialVersionUID = 1L;
    private static final String NO_COMMENT = "";
    private final Collection<Content> contentObjsToTag;

    /**
     * Constructs an instance of an abstract super class for Actions that allow
     * users to tag Sleuth Kit data model objects.
     *
     * @param menuText The menu item text.
     */
    AddTagAction(String menuText) {
        super(menuText);
        contentObjsToTag = new HashSet<>();
    }

    @Override
    public JMenuItem getPopupPresenter() {
        contentObjsToTag.clear();
        return new TagMenu();
    }

    /**
     * Get the collection of content which may have been specified for this
     * action. Empty collection returned when no content was specified.
     *
     * @return The specified content for this action.
     */
    Collection<Content> getContentToTag() {
        return Collections.unmodifiableCollection(contentObjsToTag);
    }

    /**
     * Get the menu for adding tags to the specified collection of Content.
     *
     * @param contentToTag The collection of Content the menu actions will be
     *                     applied to.
     *
     * @return The menu which will allow users to choose the tag they want to
     *         apply to the Content specified.
     */
    public JMenuItem getMenuForContent(Collection<? extends Content> contentToTag) {
        contentObjsToTag.clear();
        contentObjsToTag.addAll(contentToTag);
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

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Instances of this class implement a context menu user interface for
     * creating or selecting a tag name for a tag and specifying an optional tag
     * comment.
     */
    // @@@ This user interface has some significant usability issues and needs
    // to be reworked.
    private final class TagMenu extends JMenu {

        private static final long serialVersionUID = 1L;

        TagMenu() {
            super(getActionDisplayName());

            // Get the current set of tag names.
            Map<String, TagName> tagNamesMap;
            List<String> standardTagNames = TagsManager.getStandardTagNames();
            Map<String, JMenu> tagSetMenuMap = new HashMap<>();
            List<JMenuItem> standardTagMenuitems = new ArrayList<>();
            try {
                TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                tagNamesMap = new TreeMap<>(tagsManager.getDisplayNamesToTagNamesMap());

                // Create a menu item for each of the existing and visible tags.
                // Selecting one of these menu items adds  a tag with the associated tag name.
                if (!tagNamesMap.isEmpty()) {
                    for (Map.Entry<String, TagName> entry : tagNamesMap.entrySet()) {
                        TagName tagName = entry.getValue();
                        TagSet tagSet = tagsManager.getTagSet(tagName);

                        // Show custom tags before predefined tags in the menu
                        if (tagSet != null) {
                            JMenu menu = tagSetMenuMap.get(tagSet.getName());
                            if (menu == null) {
                                menu = createSubmenuForTagSet(tagSet);
                                tagSetMenuMap.put(tagSet.getName(), menu);
                            }
                        } else if (standardTagNames.contains(tagName.getDisplayName())) {
                            standardTagMenuitems.add(createMenutItem(tagName));
                        } else {
                            add(createMenutItem(tagName));
                        }
                    }
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }

            if (getItemCount() > 0) {
                addSeparator();
            }

            standardTagMenuitems.forEach((menuItem) -> {
                add(menuItem);
            });

            tagSetMenuMap.values().forEach((menu) -> {
                add(menu);
            });

            addSeparator();

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

            // Create a  "New Tag..." menu item.
            // Selecting this item initiates a dialog that can be used to create
            // or select a tag name and adds a tag with the resulting name.
            JMenuItem newTagMenuItem = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.newTag"));
            newTagMenuItem.addActionListener((ActionEvent e) -> {
                TagName tagName = GetTagNameDialog.doDialog();
                if (null != tagName) {
                    addTag(tagName, NO_COMMENT);
                }
            });
            add(newTagMenuItem);

        }

        /**
         * Build a JMenu for the given TagSet.
         *
         * @param tagSet
         *
         * @return JMenu for the given TagSet
         */
        private JMenu createSubmenuForTagSet(TagSet tagSet) {
            JMenu menu = new JMenu(tagSet.getName());
            List<TagName> tagNameList = tagSet.getTagNames();

            for (TagName tagName : tagNameList) {
                menu.add(createMenutItem(tagName));
            }

            return menu;
        }

        /**
         * Create a menu item for the given TagName.
         *
         * @param tagName TagName from which to create the menu item.
         *
         * @return Menu item for given TagName.
         */
        private JMenuItem createMenutItem(TagName tagName) {
            String tagDisplayName = tagName.getDisplayName();
            String notableString = tagName.getTagType() == TskData.TagType.BAD ? TagsManager.getNotableTagLabel() : "";
            JMenuItem tagNameItem = new JMenuItem(tagDisplayName + notableString);

            if (tagDisplayName.equals(TagsManager.getBookmarkTagDisplayName())) {
                tagNameItem.setAccelerator(AddBookmarkTagAction.BOOKMARK_SHORTCUT);
            }

            tagNameItem.addActionListener((ActionEvent e) -> {
                addTag(tagName, NO_COMMENT);
            });

            return tagNameItem;
        }

    }

}
