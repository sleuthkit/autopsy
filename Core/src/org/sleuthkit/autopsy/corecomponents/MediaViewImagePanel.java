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
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.python.google.common.collect.Lists;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

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

    private final Label errorLabel = new Label("Could not load file into media view.");
    private final Label tooLargeLabel = new Label("Could not load file into media view (too large).");

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
            .map("."::concat)
            .collect(Collectors.toList());

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
                borderpane.getStyleClass().add("bg");
                fxPanel = new JFXPanel(); // bridge jfx-swing
                Scene scene = new Scene(borderpane); //root of jfx tree
                scene.getStylesheets().add(MediaViewImagePanel.class.getResource("MediaViewImagePanel.css").toExternalForm());
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
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void showImageFx(final AbstractFile file, final Dimension dims) {
        if (!fxInited) {
            return;
        }

        //hide the panel during loading/transformations
        //TODO: repalce this with a progress indicator
        fxPanel.setVisible(false);

        // load the image
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (!Case.isCaseOpen()) {
                    /*
                     * handle in-between condition when case is being closed and
                     * an image was previously selected
                     */
                    return;
                }
                try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file));) {

                    BufferedImage bufferedImage = ImageIO.read(inputStream);
                    if (bufferedImage == null) {
                        LOGGER.log(Level.WARNING, "Image reader not found for file: {0}", file.getName()); //NON-NLS
                        borderpane.setCenter(errorLabel);
                    } else {
                        Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                        if (fxImage.isError()) {
                            LOGGER.log(Level.WARNING, "Could not load image file into media view: " + file.getName(), fxImage.getException()); //NON-NLS
                            borderpane.setCenter(errorLabel);
                            return;
                        } else {
                            fxImageView.setImage(fxImage);
                            borderpane.setCenter(fxImageView);
                        }
                    }
                } catch (IllegalArgumentException | IOException ex) {
                    LOGGER.log(Level.WARNING, "Could not load image file into media view: " + file.getName(), ex); //NON-NLS
                    borderpane.setCenter(errorLabel);
                } catch (OutOfMemoryError ex) {  // this might be redundant since we are not attempting to rescale the image anymore
                    LOGGER.log(Level.WARNING, "Could not load image file into media view (too large): " + file.getName(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.warn(
                            NbBundle.getMessage(this.getClass(), "MediaViewImagePanel.imgFileTooLarge.msg", file.getName()),
                            ex.getMessage());
                    borderpane.setCenter(tooLargeLabel);
                }

                SwingUtilities.invokeLater(() -> {
                    //show the panel after fully loaded
                    fxPanel.setVisible(true);
                });
            }
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
