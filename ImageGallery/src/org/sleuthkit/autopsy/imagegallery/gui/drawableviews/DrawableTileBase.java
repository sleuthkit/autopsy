
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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
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
import org.sleuthkit.autopsy.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.events.TagEvent;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.actions.AddDrawableTagAction;
import org.sleuthkit.autopsy.imagegallery.actions.CategorizeAction;
import org.sleuthkit.autopsy.imagegallery.actions.SwingMenuItemAdapter;
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
public abstract class DrawableTileBase extends DrawableUIBase {

    private static final Logger LOGGER = Logger.getLogger(DrawableTileBase.class.getName());

    private static final Border UNSELECTED_BORDER = new Border(new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3)));

    private static final Border SELECTED_BORDER = new Border(new BorderStroke(Color.BLUE, BorderStrokeStyle.SOLID, new CornerRadii(2), new BorderWidths(3)));

    //TODO: do this in CSS? -jm
    protected static final Image videoIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/video-file.png");
    protected static final Image hashHitIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/hashset_hits.png");
    protected static final Image followUpIcon = new Image("org/sleuthkit/autopsy/imagegallery/images/flag_red.png");
    protected static final Image followUpGray = new Image("org/sleuthkit/autopsy/imagegallery/images/flag_gray.png");

    protected static final FileIDSelectionModel globalSelectionModel = FileIDSelectionModel.getInstance();
    private static ContextMenu contextMenu;

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

    /**
     * the groupPane this {@link DrawableTileBase} is embedded in
     */
    final private GroupPane groupPane;
    volatile private boolean registered = false;
    private final ImageGalleryController controller;

    protected DrawableTileBase(GroupPane groupPane) {
        super(groupPane.getController());

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
                getFile().ifPresent(file -> {
                    final long fileID = file.getId();
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
                            contextMenu = buildContextMenu(file);
                            contextMenu.show(DrawableTileBase.this, t.getScreenX(), t.getScreenY());
                            break;
                    }
                });

                t.consume();
            }

            private ContextMenu buildContextMenu(DrawableFile<?> file) {
                final ArrayList<MenuItem> menuItems = new ArrayList<>();

                menuItems.add(new CategorizeAction(getController()).getPopupMenu());

                menuItems.add(new AddDrawableTagAction(getController()).getPopupMenu());

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
                        new NewWindowViewAction("Show Content Viewer", new FileNode(file.getAbstractFile())).actionPerformed(null);
                    });
                });
                menuItems.add(contentViewer);

                MenuItem externalViewer = new MenuItem("Open in External Viewer");
                final ExternalViewerAction externalViewerAction = new ExternalViewerAction("Open in External Viewer", new FileNode(file.getAbstractFile()));

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

    GroupPane getGroupPane() {
        return groupPane;
    }

    @ThreadConfined(type = ThreadType.UI)
    protected abstract void clearContent();

    protected abstract void disposeContent();

    protected abstract Runnable getContentUpdateRunnable();

    protected abstract String getTextForLabel();

    protected void initialize() {
        followUpToggle.setOnAction((ActionEvent event) -> {
            getFile().ifPresent(file -> {
                if (followUpToggle.isSelected() == true) {
                    try {
                        globalSelectionModel.clearAndSelect(file.getId());
                        new AddDrawableTagAction(getController()).addTag(getController().getTagsManager().getFollowUpTagName(), "");
                    new AddDrawableTagAction(controller).addTag(followUpTagName, "");
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to add Follow Up tag.  Could not load TagName.", ex);
                    }
                } else {
                    new DeleteFollowUpTagAction(getController(), file).handle(event);
                }
            });
        });
    }

    protected boolean hasFollowUp() {
        if (getFileID().isPresent()) {
            try {
                TagName followUpTagName = getController().getTagsManager().getFollowUpTagName();
                return DrawableAttribute.TAGS.getValue(getFile().get()).stream()
                        .anyMatch(followUpTagName::equals);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "failed to get follow up tag name ", ex);
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    protected void setFileHelper(final Long newFileID) {
        setFileIDOpt(Optional.ofNullable(newFileID));
        disposeContent();

        if (getFileID().isPresent() == false || Case.isCaseOpen() == false) {
            if (registered == true) {
                getController().getCategoryManager().unregisterListener(this);
                getController().getTagsManager().unregisterListener(this);
                registered = false;
            }
            setFileOpt(Optional.empty());
            Platform.runLater(() -> {
                clearContent();
            });
        } else {
            if (registered == false) {
                getController().getCategoryManager().registerListener(this);
                getController().getTagsManager().registerListener(this);
                registered = true;
            }
            setFileOpt(Optional.empty());

            updateSelectionState();
            updateCategory();
            updateFollowUpIcon();
            updateUI();
            Platform.runLater(getContentUpdateRunnable());
        }
    }

    private void updateUI() {
        getFile().ifPresent(file -> {
            final boolean isVideo = file.isVideo();
            final boolean hasHashSetHits = hasHashHit();
            final String text = getTextForLabel();

            Platform.runLater(() -> {
                fileTypeImageView.setImage(isVideo ? videoIcon : null);
                hashHitImageView.setImage(hasHashSetHits ? hashHitIcon : null);
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
        getFile().ifPresent(file -> {
            final boolean selected = globalSelectionModel.isSelected(file.getId());
            Platform.runLater(() -> {
                setBorder(selected ? SELECTED_BORDER : UNSELECTED_BORDER);
            });
        });
    }

    @Override
    public Region getCategoryBorderRegion() {
        return imageBorder;
    }

    @Subscribe
    @Override
    synchronized public void handleTagsChanged(TagsChangeEvent evnt) {
        if (fileID != null && (evnt.getFileIDs().contains(fileID) || evnt.getFileIDs().contains(-1L))) {
            updateFollowUpIcon();
        }
    }

    @Subscribe
    @Override
    public void handleTagAdded(ContentTagAddedEvent evt) {

        handleTagEvent(evt, () -> {
            Platform.runLater(() -> {
                followUpImageView.setImage(followUpIcon);
                followUpToggle.setSelected(true);
            });
        });
    }

    @Subscribe
    @Override
    public void handleTagDeleted(ContentTagDeletedEvent evt) {
        handleTagEvent(evt, this::updateFollowUpIcon);
    }

    void handleTagEvent(TagEvent<ContentTag> evt, Runnable runnable) {
        getFileID().ifPresent(fileID -> {
            try {
                final TagName followUpTagName = getController().getTagsManager().getFollowUpTagName();
                final ContentTag deletedTag = evt.getTag();

                if (fileID == deletedTag.getContent().getId()
                        && deletedTag.getName().equals(followUpTagName)) {
                    runnable.run();
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get followup tag name.  Unable to update follow up status for file. ", ex);
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
