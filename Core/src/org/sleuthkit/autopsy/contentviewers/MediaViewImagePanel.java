/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagCreator;
import org.sleuthkit.autopsy.contentviewers.imagetagging.StoredTag;
import org.sleuthkit.autopsy.contentviewers.imagetagging.StoredTagListener;
import org.sleuthkit.autopsy.contentviewers.imagetagging.TopLevelTagsGroup;
import org.sleuthkit.autopsy.contentviewers.imagetagging.StoredTagEvent;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import org.controlsfx.control.MaskerPane;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Lists;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ControlType;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Image viewer part of the Media View layered pane. Uses JavaFX to display the
 * image.
 */
@NbBundle.Messages({"MediaViewImagePanel.externalViewerButton.text=Open in External Viewer  Ctrl+E",
    "MediaViewImagePanel.errorLabel.text=Could not load file into Media View.",
    "MediaViewImagePanel.errorLabel.OOMText=Could not load file into Media View: insufficent memory."})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class MediaViewImagePanel extends JPanel implements MediaFileViewer.MediaViewPanel {

    private static final Image EXTERNAL = new Image(MediaViewImagePanel.class.getResource("/org/sleuthkit/autopsy/images/external.png").toExternalForm());

    private final boolean fxInited;

    private JFXPanel fxPanel;
    private TopLevelTagsGroup imageGroup;
    private ImageTagCreator tagger;
    private ImageView fxImageView;
    private Node focusedNode;
    private ScrollPane scrollPane;
    private final ProgressBar progressBar = new ProgressBar();
    private final MaskerPane maskerPane = new MaskerPane();

    private double zoomRatio;
    private double rotation; // Can be 0, 90, 180, and 270.

    private static final double[] ZOOM_STEPS = {
        0.0625, 0.125, 0.25, 0.375, 0.5, 0.75,
        1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10};

    private static final double MIN_ZOOM_RATIO = 0.0625; // 6.25%
    private static final double MAX_ZOOM_RATIO = 10.0; // 1000%

    static {
        ImageIO.scanForPlugins();
    }

    /**
     * mime types we should be able to display. if the mimetype is unknown we
     * will fall back on extension and jpg/png header
     */
    static private final SortedSet<String> supportedMimes = ImageUtils.getSupportedImageMimeTypes();

    /**
     * extensions we should be able to display
     */
    static private final List<String> supportedExtensions = ImageUtils.getSupportedImageExtensions().stream()
            .map("."::concat) //NOI18N
            .collect(Collectors.toList());

    private Task<Image> readImageTask;

    /**
     * Creates new form MediaViewImagePanel
     */
    public MediaViewImagePanel() {
        initComponents();
        fxInited = org.sleuthkit.autopsy.core.Installer.isJavaFxInited();
        if (fxInited) {
            Platform.runLater(() -> {

                // build jfx ui (we could do this in FXML?)
                fxImageView = new ImageView();  // will hold image
                imageGroup = new TopLevelTagsGroup(fxImageView);
                deleteTagButton.setEnabled(false);
                imageGroup.addFocusChangeListener((event) -> {
                    if(event.getType() == ControlType.NOT_FOCUSED || event.getNode() == fxImageView) {
                        deleteTagButton.setEnabled(false);
                        createTagButton.setEnabled(true);
                    } else if (event.getType() == ControlType.FOCUSED) {
                        deleteTagButton.setEnabled(true);
                        createTagButton.setEnabled(false);
                        focusedNode = event.getNode();
                    }
                });
                scrollPane = new ScrollPane(imageGroup); // scrolls and sizes imageview
                scrollPane.getStyleClass().add("bg"); //NOI18N
                scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
                scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);

                fxPanel = new JFXPanel(); // bridge jfx-swing
                Scene scene = new Scene(scrollPane); //root of jfx tree
                scene.getStylesheets().add(MediaViewImagePanel.class.getResource("MediaViewImagePanel.css").toExternalForm()); //NOI18N
                fxPanel.setScene(scene);

                fxImageView.setSmooth(true);
                fxImageView.setCache(true);

                EventQueue.invokeLater(() -> {
                    add(fxPanel);//add jfx ui to JPanel
                });
            });
        }
    }

    public boolean isInited() {
        return fxInited;
    }

    /**
     * Clear the displayed image
     */
    public void reset() {
        Platform.runLater(() -> {
            fxImageView.setViewport(new Rectangle2D(0, 0, 0, 0));
            fxImageView.setImage(null);
            imageGroup.getChildren().clear();
            scrollPane.setContent(null);
            scrollPane.setContent(imageGroup);
        });
    }

    private void showErrorNode(String errorMessage, AbstractFile file) {
        final Button externalViewerButton = new Button(Bundle.MediaViewImagePanel_externalViewerButton_text(), new ImageView(EXTERNAL));
        externalViewerButton.setOnAction(actionEvent
                -> //fx ActionEvent
                /*
                 * TODO: why is the name passed into the action constructor? it
                 * means we duplicate this string all over the place -jm
                 */ new ExternalViewerAction(Bundle.MediaViewImagePanel_externalViewerButton_text(), new FileNode(file))
                        .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "")) //Swing ActionEvent 
        );

        final VBox errorNode = new VBox(10, new Label(errorMessage), externalViewerButton);
        errorNode.setAlignment(Pos.CENTER);
    }

    /**
     * Show the contents of the given AbstractFile as a visual image.
     *
     * @param file image file to show
     */
    void showImageFx(final AbstractFile file) {
        if (!fxInited) {
            return;
        }

        Platform.runLater(() -> {
            if (readImageTask != null) {
                readImageTask.cancel();
            }
            readImageTask = ImageUtils.newReadImageTask(file);
            readImageTask.setOnSucceeded(succeeded -> {
                if (!Case.isCaseOpen()) {
                    /*
                     * Handle the in-between condition when case is being closed
                     * and an image was previously selected
                     *
                     * NOTE: I think this is unnecessary -jm
                     */
                    reset();
                    return;
                }

                try {
                    Image fxImage = readImageTask.get();
                    imageGroup.getChildren().clear();
                    imageGroup.getChildren().add(fxImageView);
                    if (nonNull(fxImage)) {
                        // We have a non-null image, so let's show it.
                        fxImageView.setImage(fxImage);
                        tagger = new ImageTagCreator(fxImageView);
                        StoredTagListener newTagListener = (StoredTagEvent evt) -> {
                            StoredTag tag = evt.getTag();
                            imageGroup.getChildren().add(tag);
                        };
                        tagger.addNewTagListener(newTagListener);
                        imageGroup.getChildren().add(tagger);
                        resetView();
                        scrollPane.setContent(imageGroup);
                    } else {
                        showErrorNode(Bundle.MediaViewImagePanel_errorLabel_text(), file);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showErrorNode(Bundle.MediaViewImagePanel_errorLabel_text(), file);
                }
                scrollPane.setCursor(Cursor.DEFAULT);
            });
            readImageTask.setOnFailed(failed -> {
                if (!Case.isCaseOpen()) {
                    /*
                     * Handle in-between condition when case is being closed and
                     * an image was previously selected
                     *
                     * NOTE: I think this is unnecessary -jm
                     */
                    reset();
                    return;
                }
                Throwable exception = readImageTask.getException();
                if (exception instanceof OutOfMemoryError
                        && exception.getMessage().contains("Java heap space")) {
                    showErrorNode(Bundle.MediaViewImagePanel_errorLabel_OOMText(), file);
                } else {
                    showErrorNode(Bundle.MediaViewImagePanel_errorLabel_text(), file);
                }

                scrollPane.setCursor(Cursor.DEFAULT);
            });

            maskerPane.setProgressNode(progressBar);
            progressBar.progressProperty().bind(readImageTask.progressProperty());
            maskerPane.textProperty().bind(readImageTask.messageProperty());
            scrollPane.setContent(null); // Prevent content display issues.
            scrollPane.setCursor(Cursor.WAIT);
            new Thread(readImageTask).start();
        });
    }

    /**
     * @return supported mime types
     */
    @Override
    public List<String> getSupportedMimeTypes() {
        return Collections.unmodifiableList(Lists.newArrayList(supportedMimes));
    }

    /**
     * returns supported extensions (each starting with .)
     *
     * @return
     */
    @Override
    public List<String> getSupportedExtensions() {
        return getExtensions();
    }

    /**
     * returns supported extensions (each starting with .)
     *
     * @return
     */
    public List<String> getExtensions() {
        return Collections.unmodifiableList(supportedExtensions);
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        return ImageUtils.isImageThumbnailSupported(file);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jMenu1 = new javax.swing.JMenu();
        jPopupMenu1 = new javax.swing.JPopupMenu();
        jPopupMenu2 = new javax.swing.JPopupMenu();
        toolbar = new javax.swing.JToolBar();
        rotationTextField = new javax.swing.JTextField();
        rotateLeftButton = new javax.swing.JButton();
        rotateRightButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        zoomTextField = new javax.swing.JTextField();
        zoomOutButton = new javax.swing.JButton();
        zoomInButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        zoomResetButton = new javax.swing.JButton();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        createTagButton = new javax.swing.JButton();
        jSeparator4 = new javax.swing.JToolBar.Separator();
        deleteTagButton = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(jMenu1, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.jMenu1.text")); // NOI18N

        setBackground(new java.awt.Color(0, 0, 0));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setMaximumSize(new java.awt.Dimension(32767, 23));
        toolbar.setName(""); // NOI18N

        rotationTextField.setEditable(false);
        rotationTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        rotationTextField.setText(org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotationTextField.text")); // NOI18N
        rotationTextField.setMaximumSize(new java.awt.Dimension(50, 2147483647));
        rotationTextField.setMinimumSize(new java.awt.Dimension(50, 20));
        rotationTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        toolbar.add(rotationTextField);

        rotateLeftButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/rotate-left.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(rotateLeftButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotateLeftButton.text")); // NOI18N
        rotateLeftButton.setToolTipText(org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotateLeftButton.toolTipText")); // NOI18N
        rotateLeftButton.setFocusable(false);
        rotateLeftButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        rotateLeftButton.setMaximumSize(new java.awt.Dimension(24, 24));
        rotateLeftButton.setMinimumSize(new java.awt.Dimension(24, 24));
        rotateLeftButton.setPreferredSize(new java.awt.Dimension(24, 24));
        rotateLeftButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rotateLeftButtonActionPerformed(evt);
            }
        });
        toolbar.add(rotateLeftButton);

        rotateRightButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/rotate-right.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(rotateRightButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotateRightButton.text")); // NOI18N
        rotateRightButton.setFocusable(false);
        rotateRightButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        rotateRightButton.setMaximumSize(new java.awt.Dimension(24, 24));
        rotateRightButton.setMinimumSize(new java.awt.Dimension(24, 24));
        rotateRightButton.setPreferredSize(new java.awt.Dimension(24, 24));
        rotateRightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rotateRightButtonActionPerformed(evt);
            }
        });
        toolbar.add(rotateRightButton);

        jSeparator1.setMaximumSize(new java.awt.Dimension(6, 20));
        toolbar.add(jSeparator1);

        zoomTextField.setEditable(false);
        zoomTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        zoomTextField.setText(org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomTextField.text")); // NOI18N
        zoomTextField.setMaximumSize(new java.awt.Dimension(50, 2147483647));
        zoomTextField.setMinimumSize(new java.awt.Dimension(50, 20));
        zoomTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        toolbar.add(zoomTextField);

        zoomOutButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/zoom-out.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(zoomOutButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomOutButton.text")); // NOI18N
        zoomOutButton.setFocusable(false);
        zoomOutButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        zoomOutButton.setMaximumSize(new java.awt.Dimension(24, 24));
        zoomOutButton.setMinimumSize(new java.awt.Dimension(24, 24));
        zoomOutButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomOutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });
        toolbar.add(zoomOutButton);

        zoomInButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/zoom-in.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(zoomInButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomInButton.text")); // NOI18N
        zoomInButton.setFocusable(false);
        zoomInButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        zoomInButton.setMaximumSize(new java.awt.Dimension(24, 24));
        zoomInButton.setMinimumSize(new java.awt.Dimension(24, 24));
        zoomInButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomInButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInButtonActionPerformed(evt);
            }
        });
        toolbar.add(zoomInButton);

        jSeparator2.setMaximumSize(new java.awt.Dimension(6, 20));
        toolbar.add(jSeparator2);

        org.openide.awt.Mnemonics.setLocalizedText(zoomResetButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomResetButton.text")); // NOI18N
        zoomResetButton.setFocusable(false);
        zoomResetButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        zoomResetButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        zoomResetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomResetButtonActionPerformed(evt);
            }
        });
        toolbar.add(zoomResetButton);
        toolbar.add(jSeparator3);

        org.openide.awt.Mnemonics.setLocalizedText(createTagButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.createTagButton.text")); // NOI18N
        createTagButton.setFocusable(false);
        createTagButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        createTagButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        createTagButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createTagButtonActionPerformed(evt);
            }
        });
        toolbar.add(createTagButton);
        toolbar.add(jSeparator4);

        org.openide.awt.Mnemonics.setLocalizedText(deleteTagButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.deleteTagButton.text")); // NOI18N
        deleteTagButton.setFocusable(false);
        deleteTagButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        deleteTagButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        deleteTagButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteTagButtonActionPerformed(evt);
            }
        });
        toolbar.add(deleteTagButton);

        add(toolbar);
    }// </editor-fold>//GEN-END:initComponents

    private void rotateLeftButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateLeftButtonActionPerformed
        rotation = (rotation + 270) % 360;
        updateView();
    }//GEN-LAST:event_rotateLeftButtonActionPerformed

    private void rotateRightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateRightButtonActionPerformed
        rotation = (rotation + 90) % 360;
        updateView();
    }//GEN-LAST:event_rotateRightButtonActionPerformed

    private void zoomInButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        // Find the next zoom step.
        for (int i = 0; i < ZOOM_STEPS.length; i++) {
            if (zoomRatio < ZOOM_STEPS[i]) {
                zoomRatio = ZOOM_STEPS[i];
                break;
            }
        }
        updateView();
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void zoomOutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        // Find the next zoom step.
        for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
            if (zoomRatio > ZOOM_STEPS[i]) {
                zoomRatio = ZOOM_STEPS[i];
                break;
            }
        }
        updateView();
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void zoomResetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomResetButtonActionPerformed
        resetView();
    }//GEN-LAST:event_zoomResetButtonActionPerformed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        updateView();
    }//GEN-LAST:event_formComponentResized

    private void deleteTagButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteTagButtonActionPerformed
        imageGroup.deleteNode(focusedNode);
    }//GEN-LAST:event_deleteTagButtonActionPerformed

    private void createTagButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createTagButtonActionPerformed

    }//GEN-LAST:event_createTagButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton createTagButton;
    private javax.swing.JButton deleteTagButton;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JPopupMenu jPopupMenu2;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar.Separator jSeparator4;
    private javax.swing.JButton rotateLeftButton;
    private javax.swing.JButton rotateRightButton;
    private javax.swing.JTextField rotationTextField;
    private javax.swing.JToolBar toolbar;
    private javax.swing.JButton zoomInButton;
    private javax.swing.JButton zoomOutButton;
    private javax.swing.JButton zoomResetButton;
    private javax.swing.JTextField zoomTextField;
    // End of variables declaration//GEN-END:variables

    /**
     * Reset the zoom and rotation values to their defaults. The zoom level gets
     * defaulted to the current size of the panel. The rotation will be set to
     * zero.
     *
     * Note: This method will make a call to 'updateView()' after the values
     * have been reset.
     */
    private void resetView() {
        Image image = fxImageView.getImage();
        if (image == null) {
            return;
        }

        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double scrollPaneWidth = fxPanel.getWidth();
        double scrollPaneHeight = fxPanel.getHeight();
        double zoomRatioWidth = scrollPaneWidth / imageWidth;
        double zoomRatioHeight = scrollPaneHeight / imageHeight;

        // Use the smallest ratio size to fit the entire image in the view area.
        zoomRatio = zoomRatioWidth < zoomRatioHeight ? zoomRatioWidth : zoomRatioHeight;

        rotation = 0;

        scrollPane.setHvalue(0);
        scrollPane.setVvalue(0);

        updateView();
    }

    /**
     * Update the image to use the current zoom and rotation values.
     *
     * Note: For zoom levels less than 100%, special accomodations are made in
     * order to keep the image fully visible. This is because the viewport size
     * change occurs before any transforms execute, thus chopping off part of
     * the image. So the viewport is kept the same size. Scrolling adjustments
     * are also made to try and ensure when the user zooms out, they don't find
     * themselves looking at an entire screen of dead space.
     */
    private void updateView() {
        Image image = fxImageView.getImage();
        if (image == null) {
            return;
        }

        // Image dimensions
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        // Image dimensions with zooming applied
        double adjustedImageWidth = imageWidth * zoomRatio;
        double adjustedImageHeight = imageHeight * zoomRatio;

        // ImageView viewport dimensions
        double viewportWidth;
        double viewportHeight;

        // Panel dimensions
        double panelWidth = fxPanel.getWidth();
        double panelHeight = fxPanel.getHeight();

        // Coordinates to center the image on the panel
        double centerOffsetX = (panelWidth / 2) - (imageWidth / 2);
        double centerOffsetY = (panelHeight / 2) - (imageHeight / 2);

        // Coordinates to keep the image inside the left/top boundaries
        double leftOffsetX;
        double topOffsetY;

        // Scroll bar positions
        double scrollX = scrollPane.getHvalue();
        double scrollY = scrollPane.getVvalue();

        // Scroll bar position boundaries (work-around for viewport size bug)
        double maxScrollX;
        double maxScrollY;

        // Set viewport size and translation offsets.
        if ((rotation % 180) == 0) {
            // Rotation is 0 or 180.
            viewportWidth = adjustedImageWidth;
            viewportHeight = adjustedImageHeight;
            leftOffsetX = (adjustedImageWidth - imageWidth) / 2;
            topOffsetY = (adjustedImageHeight - imageHeight) / 2;
            maxScrollX = (adjustedImageWidth - panelWidth) / (imageWidth - panelWidth);
            maxScrollY = (adjustedImageHeight - panelHeight) / (imageHeight - panelHeight);
        } else {
            // Rotation is 90 or 270.
            viewportWidth = adjustedImageHeight;
            viewportHeight = adjustedImageWidth;
            leftOffsetX = (adjustedImageHeight - imageWidth) / 2;
            topOffsetY = (adjustedImageWidth - imageHeight) / 2;
            maxScrollX = (adjustedImageHeight - panelWidth) / (imageWidth - panelWidth);
            maxScrollY = (adjustedImageWidth - panelHeight) / (imageHeight - panelHeight);
        }

        // Work around bug that truncates image if dimensions are too small.
        if (viewportWidth < imageWidth) {
            viewportWidth = imageWidth;
            if (scrollX > maxScrollX) {
                scrollX = maxScrollX;
            }
        }
        if (viewportHeight < imageHeight) {
            viewportHeight = imageHeight;
            if (scrollY > maxScrollY) {
                scrollY = maxScrollY;
            }
        }

        // Update the viewport size.
        fxImageView.setViewport(new Rectangle2D(
                0, 0, viewportWidth, viewportHeight));

        // Step 1: Zoom
        Scale scale = new Scale();
        scale.setX(zoomRatio);
        scale.setY(zoomRatio);
        scale.setPivotX(imageWidth / 2);
        scale.setPivotY(imageHeight / 2);

        // Step 2: Rotate
        Rotate rotate = new Rotate();
        rotate.setPivotX(imageWidth / 2);
        rotate.setPivotY(imageHeight / 2);
        rotate.setAngle(rotation);

        // Step 3: Position
        Translate translate = new Translate();
        translate.setX(viewportWidth > fxPanel.getWidth() ? leftOffsetX : centerOffsetX);
        translate.setY(viewportHeight > fxPanel.getHeight() ? topOffsetY : centerOffsetY);

        // Add the transforms in reverse order of intended execution.
        // Note: They MUST be added in this order to ensure translate is
        // executed last.
        imageGroup.getTransforms().clear();
        imageGroup.getTransforms().addAll(translate, rotate, scale);

        // Adjust scroll bar positions for view changes.
        if (viewportWidth > fxPanel.getWidth()) {
            scrollPane.setHvalue(scrollX);
        }
        if (viewportHeight > fxPanel.getHeight()) {
            scrollPane.setVvalue(scrollY);
        }

        // Update all image controls to reflect the current values.
        zoomOutButton.setEnabled(zoomRatio > MIN_ZOOM_RATIO);
        zoomInButton.setEnabled(zoomRatio < MAX_ZOOM_RATIO);
        rotationTextField.setText((int) rotation + "°");
        zoomTextField.setText((Math.round(zoomRatio * 100.0)) + "%");
    }
}
