/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Container for the image viewer part of media view, on a layered pane. To be
 * used with JavaFx image viewer only.
 */
public class MediaViewImagePanel extends javax.swing.JPanel {

    private JFXPanel fxPanel;
    private ImageView fxImageView;
    private static final Logger logger = Logger.getLogger(MediaViewImagePanel.class.getName());
    private boolean fxInited = false;

    static private final List<String> supportedExtensions = new ArrayList<>();
    static private final List<String> supportedMimes = new ArrayList<>();

    static {
        ImageIO.scanForPlugins();
        for (String suffix : ImageIO.getReaderFileSuffixes()) {
            supportedExtensions.add("." + suffix);
        }

        for (String type : ImageIO.getReaderMIMETypes()) {
            supportedMimes.add(type);
        }
        supportedMimes.add("image/x-ms-bmp"); //NON-NLS)
    }
    private BorderPane borderpane;

    /**
     * Creates new form MediaViewImagePanel
     */
    public MediaViewImagePanel() {
        initComponents();
        fxInited = org.sleuthkit.autopsy.core.Installer.isJavaFxInited();
        if (fxInited) {
            Platform.runLater(() -> {
                fxImageView = new ImageView();
                borderpane = new BorderPane(fxImageView);
                borderpane.setBackground(new Background(new BackgroundFill(javafx.scene.paint.Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
                fxPanel = new JFXPanel();
                Scene scene = new Scene(borderpane, javafx.scene.paint.Color.BLACK);

                fxImageView.fitWidthProperty().bind(scene.widthProperty());
                fxImageView.fitHeightProperty().bind(scene.heightProperty());
                fxPanel.setScene(scene);

                // resizes the image to have width of 100 while preserving the ratio and using
                // higher quality filtering method; this ImageView is also cached to
                // improve performance
                fxImageView.setPreserveRatio(true);
                fxImageView.setSmooth(true);
                fxImageView.setCache(true);

                EventQueue.invokeLater(() -> {
                    add(fxPanel);
                });
            });
        }
    }

    public boolean isInited() {
        return fxInited;
    }

    public void reset() {
        Platform.runLater(() -> {
            fxImageView.setImage(null);
        });
    }

    /**
     * Show image
     *
     * @param file image file to show
     * @param dims dimension of the parent window
     */
    void showImageFx(final AbstractFile file, final Dimension dims) {
        if (!fxInited) {
            return;
        }

        final String fileName = file.getName();

        //hide the panel during loading/transformations
        //TODO: repalce this with a progress indicator
        fxPanel.setVisible(false);

        // load the image
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (!Case.isCaseOpen()) {
                    //handle in-between condition when case is being closed
                    //and an image was previously selected
                    return;
                }

                final Image fxImage;
                try (InputStream inputStream = new ReadContentInputStream(file);) {

//                    fxImage = new Image(inputStream);
                    //original input stream
                    BufferedImage bi = ImageIO.read(inputStream);
                    if (bi == null) {
                        logger.log(Level.WARNING, "Could image reader not found for file: " + fileName); //NON-NLS
                        return;
                    }
                    //convert from awt imageto fx image
                    fxImage = SwingFXUtils.toFXImage(bi, null);
                } catch (IllegalArgumentException | IOException ex) {
                    logger.log(Level.WARNING, "Could not load image file into media view: " + fileName, ex); //NON-NLS
                    return;
                } catch (OutOfMemoryError ex) {
                    logger.log(Level.WARNING, "Could not load image file into media view (too large): " + fileName, ex); //NON-NLS
                    MessageNotifyUtil.Notify.warn(
                            NbBundle.getMessage(this.getClass(), "MediaViewImagePanel.imgFileTooLarge.msg", file.getName()),
                            ex.getMessage());
                    return;
                }

                if (fxImage.isError()) {
                    logger.log(Level.WARNING, "Could not load image file into media view: " + fileName, fxImage.getException()); //NON-NLS
                    return;
                }
                fxImageView.setImage(fxImage);
                borderpane.setCenter(fxImageView);

                SwingUtilities.invokeLater(() -> {
                    //show the panel after fully loaded
                    fxPanel.setVisible(true);
                });
            }
        });
    }

    /**
     * returns supported mime types
     *
     * @return
     */
    public List<String> getMimeTypes() {
        return Collections.unmodifiableList(supportedMimes);
    }

    /**
     * returns supported extensions (each starting with .)
     *
     * @return
     */
    public List<String> getExtensions() {
        return Collections.unmodifiableList(supportedExtensions);
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
