/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Abstract class to define context action to replace a tag with another
 *
 * @param <T> tag type
 */
@NbBundle.Messages({
    "ReplaceTagAction.replaceTag=Replace Selected Tag(s) With"
})
abstract class ReplaceTagAction<T extends Tag> extends AbstractAction implements Presenter.Popup {

    private static final long serialVersionUID = 1L;
    protected static final String MENU_TEXT = NbBundle.getMessage(ReplaceTagAction.class,
            "ReplaceTagAction.replaceTag");

    ReplaceTagAction(String menuText) {
        super(menuText);
    }

    /**
     * Subclasses of replaceTagAction should not override actionPerformed, but
     * instead override replaceTag.
     *
     * @param event
     */
    @Override
    @SuppressWarnings("NoopMethodInAbstractClass")
    public void actionPerformed(ActionEvent event) {
    }

    protected String getActionDisplayName() {
        return MENU_TEXT;
    }

    /**
     * Method to actually replace the selected tag with the given new tag
     *
     * @param oldTag     - the TagName which is being removed from the item
     * @param newTagName - the TagName which is being added to the itme
     * @param comment    the comment associated with the tag, empty string for
     *                   no comment
     */
    abstract protected void replaceTag(T oldTag, TagName newTagName, String comment);

    /**
     * Returns elected tags which are to be replaced
     *
     * @return
     */
    abstract Collection<? extends T> getTagsToReplace();

    @Override
    public JMenuItem getPopupPresenter() {
        return new ReplaceTagMenu();
    }

    /**
     * Instances of this class implement a context menu user interface for
     * selecting a tag name to replace the tag with
     */
    private final class ReplaceTagMenu extends JMenu {

        private static final long serialVersionUID = 1L;

        ReplaceTagMenu() {
            super(getActionDisplayName());

            final Collection<? extends T> selectedTags = getTagsToReplace();

            // Get the current set of tag names.
            Map<String, TagName> tagNamesMap = null;
            List<String> standardTagNames = TagsManager.getStandardTagNames();
            Map<String, JMenu> tagSetMenuMap = new HashMap<>();
            List<JMenuItem> standardTagMenuitems = new ArrayList<>();
            // Ideally we should'nt allow user to pick a replacement tag that's already been applied to an item
            // In the very least we don't allow them to pick the same tag as the one they are trying to replace
            Set<String> existingTagNames = new HashSet<>();
            try {
                TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                tagNamesMap = new TreeMap<>(tagsManager.getDisplayNamesToTagNamesMap());

                if (!selectedTags.isEmpty()) {
                    T firstTag = selectedTags.iterator().next();
                    existingTagNames.add(firstTag.getName().getDisplayName());
                }

                if (!tagNamesMap.isEmpty()) {
                    for (Map.Entry<String, TagName> entry : tagNamesMap.entrySet()) {
                        TagName tagName = entry.getValue();
                        TagSet tagSet = tagsManager.getTagSet(tagName);

                        // Show custom tags before predefined tags in the menu
                        if (tagSet != null) {
                            JMenu menu = tagSetMenuMap.get(tagSet.getName());
                            if (menu == null) {
                                menu = createSubmenuForTagSet(tagSet, existingTagNames, selectedTags);
                                tagSetMenuMap.put(tagSet.getName(), menu);
                            }
                        } else if (standardTagNames.contains(tagName.getDisplayName())) {
                            standardTagMenuitems.add(createMenutItem(tagName, existingTagNames, selectedTags));
                        } else {
                            add(createMenutItem(tagName, existingTagNames, selectedTags));
                        }
                    }
                } else {
                    JMenuItem empty = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.noTags"));
                    empty.setEnabled(false);
                    add(empty);
                }

            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(ReplaceTagMenu.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }

            // 
            if (this.getItemCount() > 0) {
                addSeparator();
            }
            standardTagMenuitems.forEach((menuItem) -> {
                add(menuItem);
            });

            tagSetMenuMap.values().forEach((menuItem) -> {
                add(menuItem);
            });

            addSeparator();
            JMenuItem newTagMenuItem = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.newTag"));
            newTagMenuItem.addActionListener((ActionEvent event) -> {
                TagName newTagName = GetTagNameDialog.doDialog();
                if (null != newTagName) {
                    selectedTags.forEach((oldtag) -> {
                        replaceTag(oldtag, newTagName, oldtag.getComment());
                    });
                }
            });
            add(newTagMenuItem);
            // Create a "Choose Tag and Comment..." menu item. Selecting this item initiates
            // a dialog that can be used to create or select a tag name with an
            // optional comment and adds a tag with the resulting name.
            JMenuItem tagAndCommentItem = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.tagAndComment"));
            tagAndCommentItem.addActionListener((ActionEvent event) -> {
                GetTagNameAndCommentDialog.TagNameAndComment tagNameAndComment = GetTagNameAndCommentDialog.doDialog();
                if (null != tagNameAndComment) {
                    selectedTags.forEach((oldtag) -> {
                        replaceTag(oldtag, tagNameAndComment.getTagName(), tagNameAndComment.getComment());
                    });
                }
            });
            add(tagAndCommentItem);
        }
    }

    /**
     * Build a JMenu for the given TagSet.
     *
     * @param tagSet
     * @param tagNamesToDisable
     * @param selectedTags
     *
     * @return JMenu for the given TagSet
     */
    private JMenu createSubmenuForTagSet(TagSet tagSet, Set<String> tagNamesToDisable, Collection<? extends T> selectedTags) {
        JMenu menu = new JMenu(tagSet.getName());
        List<TagName> tagNameList = tagSet.getTagNames();

        for (TagName tagName : tagNameList) {
            menu.add(createMenutItem(tagName, tagNamesToDisable, selectedTags));
        }

        return menu;
    }

    /**
     * Create a menu item for the given TagName.
     *
     * @param tagName TagName from which to create the menu item.
     * @param tagNamesToDisable
     * @param selectedTags
     *
     * @return Menu item for given TagName.
     */
    private JMenuItem createMenutItem(TagName tagName, Set<String> tagNamesToDisable, Collection<? extends T> selectedTags) {
        String tagDisplayName = tagName.getDisplayName();
        String notableString = tagName.getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
        JMenuItem tagNameItem = new JMenuItem(tagDisplayName + notableString);

        if (tagDisplayName.equals(TagsManager.getBookmarkTagDisplayName())) {
            tagNameItem.setAccelerator(AddBookmarkTagAction.BOOKMARK_SHORTCUT);
        }

        tagNameItem.addActionListener((ActionEvent e) -> {
            selectedTags.forEach((oldtag) -> {
                replaceTag(oldtag, tagName, oldtag.getComment());
            });
        });

        tagNameItem.setEnabled(!tagNamesToDisable.contains(tagDisplayName));

        return tagNameItem;
    }
}
