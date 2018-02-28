/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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


import java.awt.Dimension;
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
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import org.controlsfx.control.MaskerPane;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Lists;
import org.sleuthkit.autopsy.casemodule.Case;
//import org.sleuthkit.autopsy.corecomponents.Bundle;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Image viewer part of the Media View layered pane. Uses JavaFX to display the
 * image.
 */
@NbBundle.Messages({"MediaViewImagePanel.externalViewerButton.text=Open in External Viewer",
    "MediaViewImagePanel.errorLabel.text=Could not load file into Media View.",
    "MediaViewImagePanel.errorLabel.OOMText=Could not load file into Media View: insufficent memory."})
public class MediaViewImagePanel extends JPanel implements MediaFileViewer.MediaViewPanel {

    private static final Image EXTERNAL = new Image(MediaViewImagePanel.class.getResource("/org/sleuthkit/autopsy/images/external.png").toExternalForm());

    private static final Logger LOGGER = Logger.getLogger(MediaViewImagePanel.class.getName());

    private final boolean fxInited;

    private JFXPanel fxPanel;
    private ImageView fxImageView;
    private BorderPane borderpane;
    private final ProgressBar progressBar = new ProgressBar();
    private final MaskerPane maskerPane = new MaskerPane();

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
                borderpane = new BorderPane(fxImageView); // centers and sizes imageview
                borderpane.getStyleClass().add("bg"); //NOI18N
                fxPanel = new JFXPanel(); // bridge jfx-swing
                Scene scene = new Scene(borderpane); //root of jfx tree
                scene.getStylesheets().add(MediaViewImagePanel.class.getResource("MediaViewImagePanel.css").toExternalForm()); //NOI18N
                fxPanel.setScene(scene);

                //bind size of image to that of scene, while keeping proportions
                fxImageView.fitWidthProperty().bind(scene.widthProperty());
                fxImageView.fitHeightProperty().bind(scene.heightProperty());
                fxImageView.setPreserveRatio(true);
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
     * clear the displayed image
     */
    public void reset() {
        Platform.runLater(() -> {
            fxImageView.setImage(null);
            borderpane.setCenter(null);
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
        borderpane.setCenter(errorNode);
    }

    /**
     * Show the contents of the given AbstractFile as a visual image.
     *
     * @param file image file to show
     * @param dims dimension of the parent window (ignored)
     */
    void showImageFx(final AbstractFile file, final Dimension dims) {
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
                    if (nonNull(fxImage)) {
                        //we have non-null image show it
                        fxImageView.setImage(fxImage);
                        borderpane.setCenter(fxImageView);
                    } else {
                        showErrorNode(Bundle.MediaViewImagePanel_errorLabel_text(), file);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showErrorNode(Bundle.MediaViewImagePanel_errorLabel_text(), file);
                }
                borderpane.setCursor(Cursor.DEFAULT);
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

                borderpane.setCursor(Cursor.DEFAULT);
            });

            maskerPane.setProgressNode(progressBar);
            progressBar.progressProperty().bind(readImageTask.progressProperty());
            maskerPane.textProperty().bind(readImageTask.messageProperty());
            borderpane.setCenter(maskerPane);
            borderpane.setCursor(Cursor.WAIT);
            new Thread(readImageTask).start();
        });
    }

    /**
     * @return supported mime types
     */
    @Override
    public List<String> getMimeTypes() {
        return Collections.unmodifiableList(Lists.newArrayList(supportedMimes));
    }

    /**
     * returns supported extensions (each starting with .)
     *
     * @return
     */
    @Override
    public List<String> getExtensionsList() {
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

        setBackground(new java.awt.Color(0, 0, 0));
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

}
