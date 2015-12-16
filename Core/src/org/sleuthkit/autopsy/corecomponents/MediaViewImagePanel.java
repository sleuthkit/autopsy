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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
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
import javax.annotation.Nullable;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JPanel;
import org.controlsfx.control.MaskerPane;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Lists;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Image viewer part of the Media View layered pane. Uses JavaFX to display the
 * image.
 */
public class MediaViewImagePanel extends JPanel implements DataContentViewerMedia.MediaViewPanel {

    private static final Logger LOGGER = Logger.getLogger(MediaViewImagePanel.class.getName());

    private final boolean fxInited;

    private JFXPanel fxPanel;
    private ImageView fxImageView;
    private BorderPane borderpane;
    private final ProgressBar progressBar = new ProgressBar();
    private final MaskerPane maskerPane = new MaskerPane();

    @NbBundle.Messages({"MediaViewImagePanel.errorLabel.text=Could not load file into Media view."})
    private final Label errorLabel = new Label(Bundle.MediaViewImagePanel_errorLabel_text());

    /**
     * TODO: why is this passed to the action? it means we duplciate this string
     * all over the place -jm
     */
    @NbBundle.Messages({"MediaViewImagePanel.externalViewerButton.text=Open in External Viewer"})
    private final Button externalViewerButton = new Button(Bundle.MediaViewImagePanel_externalViewerButton_text());
    private final VBox errorNode = new VBox(10, errorLabel, externalViewerButton);

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

    private LoadImageTask readImageTask;

    /**
     * Creates new form MediaViewImagePanel
     */
    public MediaViewImagePanel() {
        initComponents();
        fxInited = org.sleuthkit.autopsy.core.Installer.isJavaFxInited();
        if (fxInited) {
            Platform.runLater(() -> {

                errorNode.setAlignment(Pos.CENTER);

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
            readImageTask = new LoadImageTask(file);

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

    private class LoadImageTask extends Task<Image> implements IIOReadProgressListener {

        private final AbstractFile file;
        volatile private BufferedImage bufferedImage = null;

        LoadImageTask(AbstractFile file) {
            this.file = file;
        }

        @Override
        @NbBundle.Messages({
            "# {0} - file name",
            "LoadImageTask.mesageText=Reading image: {0}"})
        protected Image call() throws Exception {
            updateMessage(Bundle.LoadImageTask_mesageText(file.getName()));
            try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file));) {
                ImageInputStream input = ImageIO.createImageInputStream(inputStream);
                if (input == null) {
                    throw new IIOException("Could not create ImageInputStream."); //NOI18N
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    reader.addIIOReadProgressListener(this);
                    reader.setInput(input);
                    /*
                     * This is the important part, get or create a ReadParam,
                     * create a destination image to hold the decoded result,
                     * then pass that image with the param.
                     */
                    ImageReadParam param = reader.getDefaultReadParam();

                    bufferedImage = reader.getImageTypes(0).next().createBufferedImage(reader.getWidth(0), reader.getHeight(0));
                    param.setDestination(bufferedImage);
                    try {
                        reader.read(0, param);
                    } catch (IOException iOException) {
                        // Ignore this exception or display a warning or similar, for exceptions happening during decoding
                        logError(iOException);
                    }
                    reader.removeIIOReadProgressListener(this);
                    return SwingFXUtils.toFXImage(bufferedImage, null);
                } else {
                    throw new IIOException("No ImageReader found for file."); //NOI18N
                }
            }
        }

        private void logError(@Nullable Throwable e) {
            String message = e == null ? "" : "It may be unsupported or corrupt: " + e.getLocalizedMessage(); //NOI18N
            try {
                LOGGER.log(Level.WARNING, "The MediaView tab could not read the image: {0}.  {1}", new Object[]{file.getUniquePath(), message}); //NOI18N
            } catch (TskCoreException tskCoreException) {
                LOGGER.log(Level.WARNING, "The MediaView tab could not read the image: {0}.  {1}", new Object[]{file.getName(), message}); //NOI18N
                LOGGER.log(Level.SEVERE, "Failes to get unique path for file", tskCoreException); //NOI18N
            }
        }

        @Override
        protected void failed() {
            super.failed();
            if (!Case.isCaseOpen()) {
                /*
                 * handle in-between condition when case is being closed and an
                 * image was previously selected
                 */
                reset();
                return;
            }

            handleError(getException());

            borderpane.setCursor(Cursor.DEFAULT);
        }

        private void handleError(Throwable e) {
            logError(e);
            externalViewerButton.setOnAction(actionEvent -> //fx ActionEvent
                    new ExternalViewerAction(Bundle.MediaViewImagePanel_externalViewerButton_text(), new FileNode(file))
                    .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "")) //Swing ActionEvent //NOI18N
            );
            borderpane.setCenter(errorNode);
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            if (!Case.isCaseOpen()) {
                /*
                 * handle in-between condition when case is being closed and an
                 * image was previously selected
                 */
                reset();
                return;
            }

            try {
                Image fxImage = get();
                if (fxImage == null) {
                    handleError(null);
                } else {
                    //we have non-null image show it

                    fxImageView.setImage(fxImage);
                    borderpane.setCenter(fxImageView);
                    if (fxImage.isError()) {
                        //if there was somekind of error, log it
                        logError(fxImage.getException());
                    }
                }
            } catch (InterruptedException | ExecutionException ex) {
                handleError(ex.getCause());
            }
            borderpane.setCursor(Cursor.DEFAULT);
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            //update this task with the progress reported by ImageReader.read
            updateProgress(percentageDone, 100);
        }

        @Override
        public void sequenceStarted(ImageReader source, int minIndex) {
        }

        @Override
        public void sequenceComplete(ImageReader source) {
        }

        @Override
        public void imageStarted(ImageReader source, int imageIndex) {
        }

        @Override
        public void imageComplete(ImageReader source) {
        }

        @Override
        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
        }

        @Override
        public void thumbnailProgress(ImageReader source, float percentageDone) {
        }

        @Override
        public void thumbnailComplete(ImageReader source) {
        }

        @Override
        public void readAborted(ImageReader source) {
        }
    }

}
