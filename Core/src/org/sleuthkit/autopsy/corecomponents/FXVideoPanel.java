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

import com.sun.javafx.application.PlatformImpl;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import static javafx.scene.media.MediaPlayer.Status.READY;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.modules.ModuleInstall;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Video viewer part of the Media View layered pane.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = FrameCapture.class)
})
public class FXVideoPanel extends MediaViewVideoPanel {

    private static final Logger logger = Logger.getLogger(MediaViewVideoPanel.class.getName());
    private boolean fxInited = false;
    // FX Components
    private MediaPlayer fxMediaPlayer;
    private MediaPane mediaPane;
    // Current media content representations
    private AbstractFile currentFile;
    // FX UI Components
    private JFXPanel videoComponent;
    
    /**
     * Creates new form MediaViewVideoPanel
     */
    public FXVideoPanel() {
        org.sleuthkit.autopsy.core.Installer coreInstaller =
                ModuleInstall.findObject(org.sleuthkit.autopsy.core.Installer.class, false);
        if (coreInstaller != null) {
            fxInited = coreInstaller.isJavaFxInited();
        }
        initComponents();
        customizeComponents();
    }

    public JPanel getVideoPanel() {
        return videoPanel;
    }

    public Component getVideoComponent() {
        return videoComponent;
    }

    private void customizeComponents() {
        setupFx();
    }
    

    @Override
    synchronized void setupVideo(final AbstractFile file, final Dimension dims) {
        currentFile = file;
        final boolean deleted = file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
        if (deleted) {
            mediaPane.setInfoLabelText("Playback of deleted videos is not supported, use an external player.");
            videoPanel.removeAll();
            return;
        }

        String path = "";
        try {
            path = file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Cannot get unique path of video file");
        }
        mediaPane.setInfoLabelText(path);
        mediaPane.setInfoLabelToolTipText(path);
        
        ExtractMedia em = new ExtractMedia(currentFile, getJFile(currentFile));
        em.execute();
    }
    
    synchronized void setupFx() {
        if(!fxInited) {
            return;
        }
        logger.log(Level.INFO, "In Setup FX");
        PlatformImpl.runLater(new Runnable() {
            @Override
            public void run() {
                mediaPane = new MediaPane();
                logger.log(Level.INFO, "Created MediaPane");
                Scene fxScene = new Scene(mediaPane);
                videoComponent = new JFXPanel();
                videoComponent.setScene(fxScene);
                
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        // Configure VideoPanel
                        videoPanel.removeAll();
                        videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
                        videoPanel.add(videoComponent);
                        videoPanel.setVisible(true);
                    }
                });
            }
        });
    }
    
    
    @Override
    void reset() {

        PlatformImpl.runLater(new Runnable() {
            @Override
            public void run() {
                if (fxMediaPlayer != null) {
                    if (fxMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING ) {
                        fxMediaPlayer.stop();
                    }
                    fxMediaPlayer = null;
                }
                
                
                if (videoComponent != null) {
                    videoComponent = null;
                }
            }
        });
        

        currentFile = null;
    }

    private java.io.File getJFile(AbstractFile file) {
        // Get the temp folder path of the case
        String tempPath = Case.getCurrentCase().getTempDirectory();
        String name = file.getName();
        int extStart = name.lastIndexOf(".");
        String ext = "";
        if (extStart != -1) {
            ext = name.substring(extStart, name.length()).toLowerCase();
        }
        tempPath = tempPath + java.io.File.separator + file.getId() + ext;

        java.io.File tempFile = new java.io.File(tempPath);
        return tempFile;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        videoPanel = new javax.swing.JPanel();

        javax.swing.GroupLayout videoPanelLayout = new javax.swing.GroupLayout(videoPanel);
        videoPanel.setLayout(videoPanelLayout);
        videoPanelLayout.setHorizontalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 448, Short.MAX_VALUE)
        );
        videoPanelLayout.setVerticalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 248, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(videoPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>                        

    // Variables declaration - do not modify                     
    private javax.swing.JPanel videoPanel;
    // End of variables declaration                   

    @Override
    public boolean isInited() {
        return fxInited;
    }

    /**
     * Thread that extracts Media from a Sleuthkit file representation to a
     * Java file representation that the Media Player can take as input.
     */
    private class ExtractMedia extends SwingWorker<Object, Void> {

        private ProgressHandle progress;
        boolean success = false;
        private AbstractFile sFile;
        private java.io.File jFile;
        private long extractedBytes;

        ExtractMedia(org.sleuthkit.datamodel.AbstractFile sFile, java.io.File jFile) {
            this.sFile = sFile;
            this.jFile = jFile;
        }

        public long getExtractedBytes() {
            return extractedBytes;
        }
        
        public Media getMedia() {
            return new Media(Paths.get(jFile.getAbsolutePath()).toUri().toString());
        }

        @Override
        protected Object doInBackground() throws Exception {
            success = false;
            progress = ProgressHandleFactory.createHandle("Buffering " + sFile.getName(), new Cancellable() {
                @Override
                public boolean cancel() {
                    return ExtractMedia.this.cancel(true);
                }
            });
            mediaPane.setProgressLabelText("Buffering...  ");
            progress.start();
            progress.switchToDeterminate(100);
            try {
                extractedBytes = ContentUtils.writeToFile(sFile, jFile, progress, this, true);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error buffering file", ex);
            }
            logger.log(Level.INFO, "Done buffering: " + jFile.getName());
            success = true;
            return null;
        }

        /* clean up or start the worker threads */
        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Media buffering was canceled.");
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Media buffering was interrupted.");
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Fatal error during media buffering.", ex);
            } finally {
                progress.finish();
                if (!this.isCancelled()) {
                    logger.log(Level.INFO, "ExtractMedia in done: " + jFile.getName());
                    try {
                        PlatformImpl.runLater(new Runnable() {
                            @Override
                            public void run() {
                                fxMediaPlayer = new MediaPlayer(getMedia());
                                logger.log(Level.INFO, "Fx Media Player null? " + (fxMediaPlayer == null));
                                logger.log(Level.INFO, "Media Tools null? " + (mediaPane == null));
                                mediaPane.setMediaPlayer(fxMediaPlayer);
                            }
                        });
                    } catch(MediaException e) {
                        logger.log(Level.WARNING, "something went wrong with javafx", e);
                        reset();
                        mediaPane.setInfoLabelText(e.getMessage());
                        return;
                    }
                }
            }
        }
    }
    
    private class MediaPane extends BorderPane {
        private MediaPlayer mediaPlayer;
        private MediaView mediaView;
        private Duration duration;
        private HBox mediaTools;
        private HBox mediaViewPane;
        private Slider progressSlider;
        private Button pauseButton;
        private Label progressLabel;
        private Label infoLabel;
        private int totalHours;
        private int totalMinutes;
        private int totalSeconds;
        private String durationFormat = "%02d:%02d:%02d/%02d:%02d:%02d  ";
        
        public MediaPane() {
            // Video Display
            mediaViewPane = new HBox();
            mediaViewPane.setStyle("-fx-background-color: black");
            mediaViewPane.setAlignment(Pos.CENTER);
            mediaView = new MediaView();
            mediaViewPane.getChildren().add(mediaView);
            setAlignment(mediaViewPane, Pos.CENTER);
            setCenter(mediaViewPane);
            
            // Media Controls
            VBox controlPanel = new VBox();
            mediaTools = new HBox();
            mediaTools.setAlignment(Pos.CENTER);
            mediaTools.setPadding(new Insets(5, 10, 5, 10));
            
            pauseButton  = new Button("►");
            mediaTools.getChildren().add(pauseButton);
            mediaTools.getChildren().add(new Label("    "));
            progressSlider = new Slider();
            HBox.setHgrow(progressSlider,Priority.ALWAYS);
            progressSlider.setMinWidth(50);
            progressSlider.setMaxWidth(Double.MAX_VALUE);
            mediaTools.getChildren().add(progressSlider);
            progressLabel = new Label();
            progressLabel.setPrefWidth(130);
            progressLabel.setMinWidth(50);
            mediaTools.getChildren().add(progressLabel);
            
            controlPanel.getChildren().add(mediaTools);
            
            infoLabel = new Label("");
            controlPanel.getChildren().add(infoLabel);
            setBottom(controlPanel);
            setProgressActionListeners();
        }
        
        public void setInfoLabelText(final String text) {
            PlatformImpl.runLater(new Runnable() {
                @Override
                public void run() {
                    infoLabel.setText(text);
                }
            });
        }
        
        public void setMediaPlayer(MediaPlayer mp) {
            pauseButton.setDisable(true);
            mediaPlayer = mp;
            mediaView.setMediaPlayer(mp);
            pauseButton.setDisable(false);
            
            setMediaActionListeners();
        }
        
        private void setMediaActionListeners() {
            mediaPlayer.setOnReady(new Runnable() {
                @Override
                public void run() {
                    duration = mediaPlayer.getMedia().getDuration();
                    long durationInMillis = (long) fxMediaPlayer.getMedia().getDuration().toMillis();
                
                    // pick out the total hours, minutes, seconds
                    long durationSeconds = (int) durationInMillis / 1000;
                    totalHours = (int) durationSeconds / 3600;
                    durationSeconds -= totalHours * 3600;
                    totalMinutes = (int) durationSeconds / 60;
                    durationSeconds -= totalMinutes * 60;
                    totalSeconds = (int) durationSeconds;
                    updateProgress();
                } 
            });
            
            mediaPlayer.setOnEndOfMedia(new Runnable() {
                @Override
                public void run() {
                    Duration beginning = mediaPlayer.getStartTime();
                    mediaPlayer.stop();
                    mediaPlayer.pause();
                    pauseButton.setText("►");
                    updateSlider(beginning);
                    updateTime(beginning);
                }
            });
            
            mediaPlayer.currentTimeProperty().addListener(new ChangeListener<Duration>() {
                @Override
                public void changed(ObservableValue<? extends Duration> observable, Duration oldValue, Duration newValue) {
                    updateSlider(newValue);
                    updateTime(newValue);
                }
            });
        }
        
        private void setProgressActionListeners() {
            pauseButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent e) {
                    Status status = mediaPlayer.getStatus();

                    switch (status) {
                        // If playing, pause
                        case PLAYING:
                            pauseButton.setText("►");
                            mediaPlayer.pause();
                            break;
                        // If ready, paused or stopped, continue playing
                        case READY:
                        case PAUSED:
                        case STOPPED:
                            pauseButton.setText("||");
                            mediaPlayer.play();
                            break;
                        default:
                            break;
                    }
                }
            });
            
            progressSlider.valueProperty().addListener(new InvalidationListener() {
                @Override
                public void invalidated(Observable o) {
                    if (progressSlider.isValueChanging()) {
                        mediaPlayer.seek(duration.multiply(progressSlider.getValue() / 100.0));
                    }
                }
            });
        }
        
        private void updateProgress() {
            Duration currentTime = mediaPlayer.getCurrentTime();
            updateSlider(currentTime);
            updateTime(currentTime);
        }
        
        private void updateSlider(Duration currentTime) {
            if (progressSlider != null) {
                progressSlider.setDisable(duration.isUnknown());
                if (!progressSlider.isDisabled() && duration.greaterThan(Duration.ZERO) 
                  && !progressSlider.isValueChanging()) {
                    progressSlider.setValue(currentTime.divide(duration.toMillis()).toMillis() * 100.0);
                }
            }
        }
        
        private void updateTime(Duration currentTime) {
            long millisElapsed = (long) currentTime.toMillis();
                
            long elapsedHours, elapsedMinutes, elapsedSeconds;
            // pick out the elapsed hours, minutes, seconds
            long secondsElapsed = millisElapsed / 1000;
            elapsedHours = (int) secondsElapsed / 3600;
            secondsElapsed -= elapsedHours * 3600;
            elapsedMinutes = (int) secondsElapsed / 60;
            secondsElapsed -= elapsedMinutes * 60;
            elapsedSeconds = (int) secondsElapsed;
            
            String durationStr = String.format(durationFormat,
                        elapsedHours, elapsedMinutes, elapsedSeconds,
                        totalHours, totalMinutes, totalSeconds);
            progressLabel.setText(durationStr);
        }

        private void setProgressLabelText(final String text) {
            PlatformImpl.runLater(new Runnable() {
                @Override
                public void run() {
                    progressLabel.setText(text);
                }
            });
        }

        private void setInfoLabelToolTipText(final String text) {
            PlatformImpl.runLater(new Runnable() {
                @Override
                public void run() {
                    infoLabel.setTooltip(new Tooltip(text));
                }
            });
        }
    }
    
    /**
     * @param file a video file from which to capture frames
     * @param numFrames the number of frames to capture. These frames will be
     * captured at successive intervals given by durationOfVideo/numFrames. If
     * this frame interval is less than MIN_FRAME_INTERVAL_MILLIS, then only one
     * frame will be captured and returned.
     * @return a List of VideoFrames representing the captured frames.
     */
    @Override
    public List<VideoFrame> captureFrames(java.io.File file, int numFrames) throws Exception {
//
//        try {
//        List<VideoFrame> frames = new ArrayList<>();
//
//        FrameCapturer fc = new FrameCapturer(file);
//        logger.log(Level.INFO, "Fc is null? " + (fc == null));
//        frames = fc.getFrames(numFrames);
//        
//        return frames;
//        }
//        catch (NullPointerException e) {
//            e.printStackTrace();
//            return null;
//        }
        throw new UnsupportedOperationException("Frame Capture not implemented with JavaFx");
    }

//    private class FrameCapturer {
//        
//        private MediaPlayer mediaPlayer;
//        private JFXPanel panel;
//        private boolean isReady = false;
//        
//        FrameCapturer(java.io.File file) {
//            initFx(file);
//        }
//        
//        boolean isReady() {
//            return isReady;
//        }
//        
//        private void initFx(final java.io.File file) {
//            PlatformImpl.runAndWait(new Runnable() {
//                @Override
//                public void run() {
//                    logger.log(Level.INFO, "In initFX.");
//                    // Create Media Player with no video output
//                    Media media = new Media(Paths.get(file.getAbsolutePath()).toUri().toString());
//                    mediaPlayer = new MediaPlayer(media);
//                    MediaView mediaView = new MediaView(mediaPlayer);
//                    mediaView.setStyle("-fx-background-color: black");
//                    Pane mediaViewPane = new Pane();
//                    mediaViewPane.getChildren().add(mediaView);
//                    Scene scene = new Scene(mediaViewPane);
//                    panel = new JFXPanel();
//                    panel.setScene(scene);
//                    isReady = true;
//                }
//            });
//        }
//        
//        List<VideoFrame> getFrames(int numFrames) {
//            logger.log(Level.INFO, "in get frames");
//            List<VideoFrame> frames = new ArrayList<VideoFrame>(0);
//            
//            if (mediaPlayer.getStatus() != Status.READY) {
//                try {
//                   Thread.sleep(500);
//               } catch (InterruptedException e) {
//                   return frames;
//               }
//            }
//
//            // get the duration of the video
//            long myDurationMillis = (long) mediaPlayer.getMedia().getDuration().toMillis();
//            if (myDurationMillis <= 0) {
//               return frames;
//            }
//
//            // calculate the frame interval
//            int numFramesToGet = numFrames;
//            long frameInterval = (myDurationMillis - INTER_FRAME_PERIOD_MS) / numFrames;
//            if (frameInterval < MIN_FRAME_INTERVAL_MILLIS) {
//               numFramesToGet = 1;
//            }
//
//            final Object frameLock = new Object();
//            BufferedImage frame;
//            final int width = (int) panel.getSize().getWidth();
//            final int height = (int) panel.getSize().getHeight();
//            // for each timeStamp, grap a frame
//            for (int i = 0; i < numFramesToGet; ++i) {
//               frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//               logger.log(Level.INFO, "Grabbing a frame...");
//               final long timeStamp = i * frameInterval + INTER_FRAME_PERIOD_MS;
//
//            //            PlatformImpl.runLater(new Runnable() {
//            //                @Override
//            //                public void run() {
//            //                    synchronized (frameLock) {
//                           logger.log(Level.INFO, "seeking.");
//                           mediaPlayer.seek(new Duration(timeStamp));
//            //                    }
//            //                }
//            //            });
//
//               synchronized (frameLock) {
//                   panel.paint(frame.createGraphics());
//                   logger.log(Level.INFO, "Adding image to frames");
//               }
//               frames.add(new VideoFrame(frame, timeStamp));
//            }
//            return frames;
//        }
//    }
}
