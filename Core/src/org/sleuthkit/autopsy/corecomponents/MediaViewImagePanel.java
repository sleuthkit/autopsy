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
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
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

    /**
     * Creates new form MediaViewImagePanel
     */
    public MediaViewImagePanel() {
        initComponents();



        fxInited = org.sleuthkit.autopsy.core.Installer.isJavaFxInited();


        if (fxInited) {
            setupFx();
        }
    }
    
    public boolean isInited() {
        return fxInited;
    }

    /**
     * Setup FX components
     */
    private void setupFx() {
        // load the image
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                fxPanel = new JFXPanel();
                fxImageView = new ImageView();
                // resizes the image to have width of 100 while preserving the ratio and using
                // higher quality filtering method; this ImageView is also cached to
                // improve performance
                fxImageView.setPreserveRatio(true);
                fxImageView.setSmooth(true);
                fxImageView.setCache(true);

                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        add(fxPanel);

                        //TODO
                        // setVisible(true);
                    }
                });



            }
        });


    }

    public void reset() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                fxImageView.setImage(null);
            }
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

                final InputStream inputStream = new ReadContentInputStream(file);

                final Image fxImage;
                try {
                    //original input stream
                    BufferedImage bi = ImageIO.read(inputStream);
                    if (bi == null) {
                        logger.log(Level.WARNING, "Could image reader not found for file: " + fileName);
                        return;
                    }
                    //scale image using Scalr
                    BufferedImage biScaled = ScalrWrapper.resizeHighQuality(bi, (int) dims.getWidth(), (int) dims.getHeight());
                    //convert from awt imageto fx image
                    fxImage = SwingFXUtils.toFXImage(biScaled, null);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not load image file into media view: " + fileName, ex);
                    return;
                } catch (OutOfMemoryError ex) {
                    logger.log(Level.WARNING, "Could not load image file into media view (too large): " + fileName, ex);
                    MessageNotifyUtil.Notify.warn(
                            NbBundle.getMessage(this.getClass(), "MediaViewImagePanel.imgFileTooLarge.msg", file.getName()),
                            ex.getMessage());
                    return;
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Could not close input stream after loading image in media view: " + fileName, ex);
                    }
                }

                if (fxImage == null || fxImage.isError()) {
                    logger.log(Level.WARNING, "Could not load image file into media view: " + fileName);
                    return;
                }

                //use border pane to center the image in the scene
                BorderPane borderpane = new BorderPane();
                borderpane.setCenter(fxImageView);

                fxImageView.setImage(fxImage);
                fxImageView.setFitWidth(dims.getWidth());
                fxImageView.setFitHeight(dims.getHeight());

                //Group fxRoot = new Group();

                //Scene fxScene = new Scene(fxRoot, dims.getWidth(), dims.getHeight(), javafx.scene.paint.Color.BLACK);
                Scene fxScene = new Scene(borderpane, javafx.scene.paint.Color.BLACK);
                // borderpane.getChildren().add(fxImageView);

                fxPanel.setScene(fxScene);

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        //show the panel after fully loaded
                        fxPanel.setVisible(true);
                    }
                });

            }
        });

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
