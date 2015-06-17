
/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
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
import javafx.scene.layout.AnchorPane;
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
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.ContextMenuActionsProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.TagsChangeEvent;
import org.sleuthkit.autopsy.imagegallery.actions.AddDrawableTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.actions.DeleteFollowUpTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.SwingMenuItemAdapter;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryChangeEvent;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract base class for {@link DrawableTile} and {@link SlideShowView},
 * since they share a similar node tree and many behaviors, other implementers
 * of {@link DrawableView}s should implement the interface directly
 *
 */
public abstract class DrawableViewBase extends AnchorPane implements DrawableView {

    private static final Logger LOGGER = Logger.getLogger(DrawableViewBase.class.getName());

    private static final Border UNSELECTED_BORDER = new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3)));

    private static final Border SELECTED_BORDER = new Border(new BorderStroke(Color.BLUE, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3)));

    //TODO: do this in CSS? -jm
    protected static final Image videoIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/video-file.png");
    protected static final Image hashHitIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/hashset_hits.png");
    protected static final Image followUpIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/flag_red.png");
    protected static final Image followUpGray = new Image("org/sleuthkit/autopsy/imagegallery/images/flag_gray.png");

    protected static final FileIDSelectionModel globalSelectionModel = FileIDSelectionModel.getInstance();

    /**
     * displays the icon representing video files
     */
    @FXML
    protected ImageView fileTypeImageView;

    /**
     * displays the icon representing hash hits
     */
    @FXML
    protected ImageView hashHitImageView;

    /**
     * displays the icon representing follow up tag
     */
    @FXML
    protected ImageView followUpImageView;

    @FXML
    protected ToggleButton followUpToggle;

    /**
     * the label that shows the name of the represented file
     */
    @FXML
    protected Label nameLabel;

    @FXML
    protected BorderPane imageBorder;

    static private ContextMenu contextMenu;

    private DrawableFile<?> file;

    private Long fileID;

    /**
     * the groupPane this {@link DrawableViewBase} is embedded in
     */
    final private GroupPane groupPane;
    private boolean registered = false;
    private final ImageGalleryController controller;

    GroupPane getGroupPane() {
        return groupPane;
    }

    protected DrawableViewBase(GroupPane groupPane) {
        this.groupPane = groupPane;
        this.controller = groupPane.getController();
        globalSelectionModel.getSelected().addListener((Observable observable) -> {
            updateSelectionState();
        });

        //set up mouse listener
        //TODO: split this between DrawableTile and SingleDrawableViewBase
        addEventFilter(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {

            @Override
            public void handle(MouseEvent t) {

                switch (t.getButton()) {
                    case PRIMARY:
                        if (t.getClickCount() == 1) {
                            if (t.isControlDown()) {
                                globalSelectionModel.toggleSelection(fileID);
                            } else {
                                groupPane.makeSelection(t.isShiftDown(), fileID);
                            }
                        } else if (t.getClickCount() > 1) {
                            groupPane.activateSlideShowViewer(fileID);
                        }
                        break;
                    case SECONDARY:

                        if (t.getClickCount() == 1) {
                            if (globalSelectionModel.isSelected(fileID) == false) {
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
                        contextMenu = buildContextMenu();
                        contextMenu.show(DrawableViewBase.this, t.getScreenX(), t.getScreenY());

                        break;
                }
                t.consume();
            }

            private ContextMenu buildContextMenu() {
                final ArrayList<MenuItem> menuItems = new ArrayList<>();

                menuItems.add(new CategorizeAction(controller).getPopupMenu());

                menuItems.add(new AddDrawableTagAction(controller).getPopupMenu());

                final MenuItem extractMenuItem = new MenuItem("Extract File(s)");
                extractMenuItem.setOnAction((ActionEvent t) -> {
                    SwingUtilities.invokeLater(() -> {
                        TopComponent etc = WindowManager.getDefault().findTopComponent(ImageGalleryTopComponent.PREFERRED_ID);
                        ExtractAction.getInstance().actionPerformed(new java.awt.event.ActionEvent(etc, 0, null));
                    });
                });
                menuItems.add(extractMenuItem);

                MenuItem contentViewer = new MenuItem("Show Content Viewer");
                contentViewer.setOnAction((ActionEvent t) -> {
                    SwingUtilities.invokeLater(() -> {
                        new NewWindowViewAction("Show Content Viewer", new FileNode(getFile().getAbstractFile())).actionPerformed(null);
                    });
                });
                menuItems.add(contentViewer);

                MenuItem externalViewer = new MenuItem("Open in External Viewer");
                final ExternalViewerAction externalViewerAction = new ExternalViewerAction("Open in External Viewer", new FileNode(getFile().getAbstractFile()));

                externalViewer.setDisable(externalViewerAction.isEnabled() == false);
                externalViewer.setOnAction((ActionEvent t) -> {
                    SwingUtilities.invokeLater(() -> {
                        externalViewerAction.actionPerformed(null);
                    });
                });
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

    @ThreadConfined(type = ThreadType.UI)
    protected abstract void clearContent();

    protected abstract void disposeContent();

    protected abstract Runnable getContentUpdateRunnable();

    protected abstract String getTextForLabel();

    @SuppressWarnings("deprecation")
    protected void initialize() {
        followUpToggle.setOnAction((ActionEvent event) -> {
            if (followUpToggle.isSelected() == true) {
                try {
                    final TagName followUpTagName = controller.getTagsManager().getFollowUpTagName();
                    globalSelectionModel.clearAndSelect(fileID);
                    new AddDrawableTagAction(controller).addTag(followUpTagName, "");
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to add Follow Up tag.  Could not load TagName.", ex);
                }
            } else {
                new DeleteFollowUpTagAction(controller, file).handle(event);
            }
        });
    }

    @Override
    public DrawableFile<?> getFile() {
        if (fileID != null) {
            if (file == null || file.getId() != fileID) {
                try {
                    file = ImageGalleryController.getDefault().getFileFromId(fileID);
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "failed to get DrawableFile for obj_id" + fileID, ex);
                    file = null;
                }
            }
            return file;
        } else {
            return null;
        }
    }

    protected boolean hasFollowUp() throws TskCoreException {
        String followUpTagName = getController().getTagsManager().getFollowUpTagName().getDisplayName();
        Collection<TagName> tagNames = DrawableAttribute.TAGS.getValue(getFile());
        return tagNames.stream().anyMatch((tn) -> tn.getDisplayName().equals(followUpTagName));
    }

    @Override
    synchronized public Long getFileID() {
        return fileID;
    }

    @Override
    synchronized public void handleTagsChanged(TagsChangeEvent evnt) {
        if (fileID != null && evnt.getFileIDs().contains(fileID)) {
            updateFollowUpIcon();
        }
    }

    synchronized protected void updateFollowUpIcon() {
        if (file != null) {
            try {
                boolean hasFollowUp = hasFollowUp();
                Platform.runLater(() -> {
                    followUpImageView.setImage(hasFollowUp ? followUpIcon : followUpGray);
                    followUpToggle.setSelected(hasFollowUp);
                });
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get follow up status for file.", ex);
            }
        }
    }

    @Override
    synchronized public void setFile(final Long fileID) {
        if (Objects.equals(fileID, this.fileID) == false) {
            this.fileID = fileID;
            disposeContent();

            if (this.fileID == null || Case.isCaseOpen() == false) {
                if (registered == true) {
                    getController().getCategoryManager().unregisterListener(this);
                    getController().getTagsManager().unregisterListener(this);
                    registered = false;
                }
                file = null;
                Platform.runLater(() -> {
                    clearContent();
                });
            } else {
                if (registered == false) {
                    getController().getCategoryManager().registerListener(this);
                    getController().getTagsManager().registerListener(this);
                    registered = true;
                }
                file = null;
                getFile();
                updateSelectionState();
                updateCategoryBorder();
                updateFollowUpIcon();
                updateUI();
                Platform.runLater(getContentUpdateRunnable());
            }
        }
    }

    private void updateUI() {
        final boolean isVideo = getFile().isVideo();
        final boolean hasHashSetHits = hasHashHit();
        final String text = getTextForLabel();

        Platform.runLater(() -> {
            fileTypeImageView.setImage(isVideo ? videoIcon : null);
            hashHitImageView.setImage(hasHashSetHits ? hashHitIcon : null);
            nameLabel.setText(text);
            nameLabel.setTooltip(new Tooltip(text));
        });
    }

    /**
     * update the visual representation of the selection state of this
     * DrawableView
     */
    synchronized protected void updateSelectionState() {
        final boolean selected = globalSelectionModel.isSelected(getFileID());
        Platform.runLater(() -> {
            setBorder(selected ? SELECTED_BORDER : UNSELECTED_BORDER);
        });
    }

    @Override
    public Region getCategoryBorderRegion() {
        return imageBorder;
    }

    @Subscribe
    @Override
    synchronized public void handleCategoryChanged(CategoryChangeEvent evt) {
        if (evt.getFileIDs().contains(getFileID())) {
            updateCategoryBorder();
        }
    }

    @Override
    public ImageGalleryController getController() {
        return controller;
    }
}
