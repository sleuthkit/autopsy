/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.awt.Window;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.collections.ObservableSet;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.actions.GetTagNameAndCommentDialog;
import org.sleuthkit.autopsy.actions.GetTagNameDialog;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this Action allow users to apply tags to content.
 */
public class AddTagAction extends Action {

    private static final Logger LOGGER = Logger.getLogger(AddTagAction.class.getName());

    private final ImageGalleryController controller;
    private final Set<Long> selectedFileIDs;
    private final TagName tagName;

    public AddTagAction(ImageGalleryController controller, TagName tagName, Set<Long> selectedFileIDs) {
        super(tagName.getDisplayName());
        this.controller = controller;
        this.selectedFileIDs = selectedFileIDs;
        this.tagName = tagName;
        setGraphic(controller.getTagsManager().getGraphic(tagName));
        String notableString = tagName.getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
        setText(tagName.getDisplayName() + notableString);
        setEventHandler(actionEvent -> addTagWithComment(""));
    }

    static public Menu getTagMenu(ImageGalleryController controller) throws TskCoreException {
        return new TagMenu(controller);
    }

    private void addTagWithComment(String comment) {
        addTagsToFiles(tagName, comment, selectedFileIDs);
    }

    @NbBundle.Messages({"# {0} - fileID",
        "AddDrawableTagAction.addTagsToFiles.alert=Unable to tag file {0}."})
    private void addTagsToFiles(TagName tagName, String comment, Set<Long> selectedFiles) {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                // check if the same tag is being added for the same abstract file.
                DrawableTagsManager tagsManager = controller.getTagsManager();
                for (Long fileID : selectedFiles) {
                    try {
                        final DrawableFile file = controller.getFileFromID(fileID);
                        LOGGER.log(Level.INFO, "tagging {0} with {1} and comment {2}", new Object[]{file.getName(), tagName.getDisplayName(), comment}); //NON-NLS

                        List<ContentTag> contentTags = tagsManager.getContentTags(file);
                        Optional<TagName> duplicateTagName = contentTags.stream()
                                .map(ContentTag::getName)
                                .filter(tagName::equals)
                                .findAny();

                        if (duplicateTagName.isPresent()) {
                            LOGGER.log(Level.INFO, "{0} already tagged as {1}. Skipping.", new Object[]{file.getName(), tagName.getDisplayName()}); //NON-NLS
                        } else {
                            LOGGER.log(Level.INFO, "Tagging {0} as {1}", new Object[]{file.getName(), tagName.getDisplayName()}); //NON-NLS
                            controller.getTagsManager().addContentTag(file, tagName, comment);
                        }

                    } catch (TskCoreException tskCoreException) {
                        LOGGER.log(Level.SEVERE, "Error tagging file", tskCoreException); //NON-NLS
                        Platform.runLater(()
                                -> new Alert(Alert.AlertType.ERROR, Bundle.AddDrawableTagAction_addTagsToFiles_alert(fileID)).show()
                        );
                        break;
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "unexpected exception while tagging files", ex); //NON-NLS
                }
            }
        }.execute();
    }

    @NbBundle.Messages({"AddTagAction.menuItem.quickTag=Quick Tag",
        "AddTagAction.menuItem.noTags=No tags",
        "AddTagAction.menuItem.newTag=New Tag...",
        "AddTagAction.menuItem.tagAndComment=Tag and Comment...",
        "AddDrawableTagAction.displayName.plural=Tag Files",
        "AddDrawableTagAction.displayName.singular=Tag File"})
    private static class TagMenu extends Menu {

        TagMenu(ImageGalleryController controller) throws TskCoreException {
            setGraphic(new ImageView(DrawableAttribute.TAGS.getIcon()));
            ObservableSet<Long> selectedFileIDs = controller.getSelectionModel().getSelected();
            setText(selectedFileIDs.size() > 1
                    ? Bundle.AddDrawableTagAction_displayName_plural()
                    : Bundle.AddDrawableTagAction_displayName_singular());

            // Create a "Quick Tag" sub-menu.
            Menu quickTagMenu = new Menu(Bundle.AddTagAction_menuItem_quickTag());
            getItems().add(quickTagMenu);

            /*
             * Each non-Category tag name in the current set of tags gets its
             * own menu item in the "Quick Tags" sub-menu. Selecting one of
             * these menu items adds a tag with the associated tag name.
             */
            Collection<TagName> tagNames = controller.getTagsManager().getNonCategoryTagNames();
            if (tagNames.isEmpty()) {
                MenuItem empty = new MenuItem(Bundle.AddTagAction_menuItem_noTags());
                empty.setDisable(true);
                quickTagMenu.getItems().add(empty);
            } else {
                tagNames.stream()
                        .map(tagName -> new AddTagAction(controller, tagName, selectedFileIDs))
                        .map(ActionUtils::createMenuItem)
                        .forEachOrdered(quickTagMenu.getItems()::add);
            }

            /*
             * The "Quick Tag" menu also gets an "New Tag..." menu item.
             * Selecting this item initiates a dialog that can be used to create
             * or select a tag name and adds a tag with the resulting name.
             */
            MenuItem newTagMenuItem = new MenuItem(Bundle.AddTagAction_menuItem_newTag());
            newTagMenuItem.setOnAction(actionEvent
                    -> SwingUtilities.invokeLater(() -> {
                        TagName tagName = GetTagNameDialog.doDialog(getIGWindow());
                        if (tagName != null) {
                            new AddTagAction(controller, tagName, selectedFileIDs).handle(actionEvent);
                        }
                    }));
            quickTagMenu.getItems().add(newTagMenuItem);

            /*
             * Create a "Tag and Comment..." menu item. Selecting this item
             * initiates a dialog that can be used to create or select a tag
             * name with an optional comment and adds a tag with the resulting
             * name.
             */
            MenuItem tagAndCommentItem = new MenuItem(Bundle.AddTagAction_menuItem_tagAndComment());
            tagAndCommentItem.setOnAction(actionEvent
                    -> SwingUtilities.invokeLater(() -> {
                        GetTagNameAndCommentDialog.TagNameAndComment tagNameAndComment = GetTagNameAndCommentDialog.doDialog(getIGWindow());
                        if (null != tagNameAndComment) {
                            new AddTagAction(controller, tagNameAndComment.getTagName(), selectedFileIDs).addTagWithComment(tagNameAndComment.getComment());
                        }
                    }));
            getItems().add(tagAndCommentItem);
        }
    }

    static private Window getIGWindow() {
        TopComponent etc = ImageGalleryTopComponent.getTopComponent();
        return SwingUtilities.getWindowAncestor(etc);
    }
}
