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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.actions.AddTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.actions.DeleteFollowUpTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.DeleteTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.OpenExternalViewerAction;
import org.sleuthkit.autopsy.imagegallery.actions.SwingMenuItemAdapter;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract base class for DrawableTile and SlideShowView, since they share a
 * similar node tree and many behaviors, other implementors of DrawableViews
 * should implement the interface directly
 *
 *
 * TODO: refactor ExternalViewerAction to supply its own name
 */
@NbBundle.Messages({"DrawableTileBase.externalViewerAction.text=Open in External Viewer"})
public abstract class DrawableTileBase extends DrawableUIBase {

    private static final Logger logger = Logger.getLogger(DrawableTileBase.class.getName());

    private static final Border UNSELECTED_BORDER = new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3)));
    private static final Border SELECTED_BORDER = new Border(new BorderStroke(Color.BLUE, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3)));

    //TODO: do this in CSS? -jm
    protected static final Image followUpIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/flag_red.png"); //NON-NLS
    protected static final Image followUpGray = new Image("org/sleuthkit/autopsy/imagegallery/images/flag_gray.png"); //NON-NLS

    protected final FileIDSelectionModel selectionModel;
    private static ContextMenu contextMenu;

    /**
     * displays the icon representing video files
     */
    @FXML
    private ImageView fileTypeImageView;

    /**
     * displays the icon representing hash hits
     */
    @FXML
    private ImageView hashHitImageView;

    /**
     * displays the icon representing follow up tag
     */
    @FXML
    private ImageView followUpImageView;

    @FXML
    private ToggleButton followUpToggle;

    @FXML
    BorderPane imageBorder;

    /**
     * the label that shows the name of the represented file
     */
    @FXML
    Label nameLabel;

    @FXML
    protected ImageView imageView;
    /**
     * the groupPane this {@link DrawableTileBase} is embedded in
     */
    final private GroupPane groupPane;

    volatile private boolean registered = false;

    /**
     *
     * @param groupPane  the value of groupPane
     * @param controller the value of controller
     */
    @NbBundle.Messages({"DrawableTileBase.menuItem.extractFiles=Extract File(s)",
        "DrawableTileBase.menuItem.showContentViewer=Show Content Viewer"})
    protected DrawableTileBase(GroupPane groupPane, final ImageGalleryController controller) {
        super(controller);
        this.groupPane = groupPane;
        selectionModel = controller.getSelectionModel();
        selectionModel.getSelected().addListener(new WeakInvalidationListener(selectionListener));

        //set up mouse listener
        addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {

            @Override
            public void handle(MouseEvent t) {
                getFile().ifPresent(file -> {
                    final long fileID = file.getId();
                    switch (t.getButton()) {

                        case SECONDARY:
                            if (t.getClickCount() == 1) {
                                if (selectionModel.isSelected(fileID) == false) {
                                    groupPane.makeSelection(false, fileID);
                                }
                            }
                            if (contextMenu != null) {
                                contextMenu.hide();
                            }
                            final ContextMenu groupContextMenu = groupPane.getContextMenu();
                            if (groupContextMenu != null) {
                                groupContextMenu.hide();
                            }
                            contextMenu = buildContextMenu(file);
                            contextMenu.show(DrawableTileBase.this, t.getScreenX(), t.getScreenY());
                            break;
                    }
                });
                t.consume();
            }

            private ContextMenu buildContextMenu(DrawableFile file) {
                final ArrayList<MenuItem> menuItems = new ArrayList<>();

                menuItems.add(CategorizeAction.getCategoriesMenu(getController()));

                try {
                    menuItems.add(AddTagAction.getTagMenu(getController()));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error building tagging context menu.", ex);
                }

                final Collection<AbstractFile> selectedFilesList = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
                if (selectedFilesList.size() == 1) {
                    menuItems.add(DeleteTagAction.getTagMenu(getController()));
                }

                final MenuItem extractMenuItem = new MenuItem(Bundle.DrawableTileBase_menuItem_extractFiles());
                extractMenuItem.setOnAction(actionEvent
                        -> SwingUtilities.invokeLater(() -> {
                            TopComponent etc = ImageGalleryTopComponent.getTopComponent();
                            ExtractAction.getInstance().actionPerformed(new java.awt.event.ActionEvent(etc, 0, null));
                        }));
                menuItems.add(extractMenuItem);

                MenuItem contentViewer = new MenuItem(Bundle.DrawableTileBase_menuItem_showContentViewer());
                contentViewer.setOnAction(actionEvent
                        -> SwingUtilities.invokeLater(() -> {
                            new NewWindowViewAction(Bundle.DrawableTileBase_menuItem_showContentViewer(), new FileNode(file.getAbstractFile()))
                                    .actionPerformed(null);
                        }));
                menuItems.add(contentViewer);
                MenuItem externalViewer = new MenuItem("Open in External Viewer");
                externalViewer.setOnAction(actionEvent
                        -> SwingUtilities.invokeLater(() -> {
                            ExternalViewerShortcutAction.getInstance()
                                    .actionPerformed(null);
                        }));
                externalViewer.setAccelerator(OpenExternalViewerAction.EXTERNAL_VIEWER_SHORTCUT);
                menuItems.add(externalViewer);

                Collection<? extends ContextMenuActionsProvider> menuProviders = Lookup.getDefault().lookupAll(ContextMenuActionsProvider.class);

                for (ContextMenuActionsProvider provider : menuProviders) {
                    for (final Action act : provider.getActions()) {
                        if (act instanceof Presenter.Popup) {
                            Presenter.Popup aact = (Presenter.Popup) act;
                            menuItems.add(SwingMenuItemAdapter.create(aact.getPopupPresenter()));
                        }
                    }
                }

                ContextMenu contextMenu = new ContextMenu(menuItems.toArray(new MenuItem[]{}));
                contextMenu.setAutoHide(true);
                return contextMenu;
            }
        });
    }
    private final InvalidationListener selectionListener = observable -> updateSelectionState();

    GroupPane getGroupPane() {
        return groupPane;
    }

    protected abstract String getTextForLabel();

    protected void initialize() {

        followUpToggle.setOnAction(
                actionEvent -> getFile().ifPresent(
                        file -> {
                            if (followUpToggle.isSelected() == true) {
                                selectionModel.clearAndSelect(file.getId());
                                new AddTagAction(getController(), getController().getTagsManager().getFollowUpTagName(), selectionModel.getSelected()).handle(actionEvent);
                            } else {
                                new DeleteFollowUpTagAction(getController(), file).handle(actionEvent);
                            }
                        })
        );
    }

    protected boolean hasFollowUp() {
        if (getFileID().isPresent()) {

            TagName followUpTagName = getController().getTagsManager().getFollowUpTagName();
            if (getFile().isPresent()) {
                return DrawableAttribute.TAGS.getValue(getFile().get()).stream()
                        .anyMatch(followUpTagName::equals);
            } else {
                return false;
            }
        }
        return false;
    }

    @Override
    synchronized protected void setFileHelper(final Long newFileID) {
        setFileIDOpt(Optional.ofNullable(newFileID));
        setFileOpt(Optional.empty());

        disposeContent();

        if (getFileID().isPresent() == false || Case.isCaseOpen() == false) {
            if (registered == true) {
                getController().getCategoryManager().unregisterListener(this);
                getController().getTagsManager().unregisterListener(this);
                registered = false;
            }
            updateContent();
        } else {
            if (registered == false) {
                getController().getCategoryManager().registerListener(this);
                getController().getTagsManager().registerListener(this);
                registered = true;
            }
            updateSelectionState();
            updateCategory();
            updateFollowUpIcon();
            updateContent();
            updateMetaData();
        }
    }

    private void updateMetaData() {
        getFile().ifPresent(file -> {
            final boolean isVideo = file.isVideo();
            final boolean hasHashSetHits = hasHashHit();

            final String text = getTextForLabel();

            Platform.runLater(() -> {
                fileTypeImageView.setManaged(isVideo);
                fileTypeImageView.setVisible(isVideo);
                hashHitImageView.setManaged(hasHashSetHits);
                hashHitImageView.setVisible(hasHashSetHits);

                nameLabel.setText(text);
                nameLabel.setTooltip(new Tooltip(text));
            });
        });

    }

    /**
     * update the visual representation of the selection state of this
     * DrawableView
     */
    protected void updateSelectionState() {
        getFileID().ifPresent(fileID -> {
            final boolean selected = selectionModel.isSelected(fileID);
            Platform.runLater(() -> setBorder(selected ? SELECTED_BORDER : UNSELECTED_BORDER));
        });
    }

    @Override
    public Region getCategoryBorderRegion() {
        return imageBorder;
    }

    @Subscribe
    @Override
    public void handleTagAdded(ContentTagAddedEvent evt) {
        getFileID().ifPresent(fileID -> {
            final TagName followUpTagName = getController().getTagsManager().getFollowUpTagName(); //NON-NLS
            final ContentTag addedTag = evt.getAddedTag();
            if (fileID == addedTag.getContent().getId()
                    && addedTag.getName().equals(followUpTagName)) {
                Platform.runLater(() -> {
                    followUpImageView.setImage(followUpIcon);
                    followUpToggle.setSelected(true);
                });
            }
        });
    }

    @Subscribe
    @Override
    public void handleTagDeleted(ContentTagDeletedEvent evt) {
        getFileID().ifPresent(fileID -> {
            final TagName followUpTagName = getController().getTagsManager().getFollowUpTagName(); //NON-NLS
            final ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = evt.getDeletedTagInfo();
            if (fileID == deletedTagInfo.getContentID()
                    && deletedTagInfo.getName().equals(followUpTagName)) {
                updateFollowUpIcon();
            }
        });
    }

    private void updateFollowUpIcon() {
        boolean hasFollowUp = hasFollowUp();
        Platform.runLater(() -> {
            followUpImageView.setImage(hasFollowUp ? followUpIcon : followUpGray);
            followUpToggle.setSelected(hasFollowUp);
        });
    }
}
