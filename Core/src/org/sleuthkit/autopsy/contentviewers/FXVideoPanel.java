/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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

import com.google.common.io.Files;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.concurrent.Task;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import static javafx.scene.media.MediaPlayer.Status.PAUSED;
import static javafx.scene.media.MediaPlayer.Status.PLAYING;
import static javafx.scene.media.MediaPlayer.Status.READY;
import static javafx.scene.media.MediaPlayer.Status.STOPPED;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import javax.swing.JPanel;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.corecomponents.FrameCapture;
import org.sleuthkit.autopsy.corecomponents.VideoFrame;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.VideoUtils;
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

    // Refer to https://docs.oracle.com/javafx/2/api/javafx/scene/media/package-summary.html
    // for Javafx supported formats 
    private static final String[] EXTENSIONS = new String[]{".m4v", ".fxm", ".flv", ".m3u8", ".mp4", ".aif", ".aiff", ".mp3", "m4a", ".wav"}; //NON-NLS
    private static final List<String> MIMETYPES = Arrays.asList("audio/x-aiff", "video/x-javafx", "video/x-flv", "application/vnd.apple.mpegurl", " audio/mpegurl", "audio/mpeg", "video/mp4", "audio/x-m4a", "video/x-m4v", "audio/x-wav"); //NON-NLS
    private static final Logger logger = Logger.getLogger(FXVideoPanel.class.getName());

    private boolean fxInited = false;

    private MediaPane mediaPane;

    private AbstractFile currentFile;

    public FXVideoPanel() {
        fxInited = Installer.isJavaFxInited();
        initComponents();
        if (fxInited) {
            Platform.runLater(() -> {

                mediaPane = new MediaPane();
                Scene fxScene = new Scene(mediaPane);
                jFXPanel.setScene(fxScene);
            });
        }
    }

    @Deprecated
    public JPanel getVideoPanel() {
        return this;
    }

    @Override
    void setupVideo(final AbstractFile file, final Dimension dims) {
        if (file.equals(currentFile)) {
            return;
        }
        if (!Case.isCaseOpen()) {
            //handle in-between condition when case is being closed
            //and an image was previously selected
            return;
        }
        reset();
        currentFile = file;
        final boolean deleted = file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
        if (deleted) {
            mediaPane.setInfoLabelText(NbBundle.getMessage(this.getClass(), "FXVideoPanel.mediaPane.infoLabel"));
            removeAll();
            return;
        }
        mediaPane.setFit(dims);

        String path = "";
        try {
            path = file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Cannot get unique path of video file", ex); //NON-NLS
        }
        mediaPane.setInfoLabelText(path);
        mediaPane.setInfoLabelToolTipText(path);

        final File tempFile;
        try {
            tempFile = VideoUtils.getTempVideoFile(currentFile);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }

        new Thread(mediaPane.new ExtractMedia(currentFile, tempFile)).start();

    }

    @Override
    void reset() {
        Platform.runLater(() -> {
            if (mediaPane != null) {
                mediaPane.reset();
            }
        });
        currentFile = null;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFXPanel = new javafx.embed.swing.JFXPanel();

        setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout jFXPanelLayout = new javax.swing.GroupLayout(jFXPanel);
        jFXPanel.setLayout(jFXPanelLayout);
        jFXPanelLayout.setHorizontalGroup(
            jFXPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        jFXPanelLayout.setVerticalGroup(
            jFXPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jFXPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jFXPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javafx.embed.swing.JFXPanel jFXPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public boolean isInited() {
        return fxInited;
    }

    private class MediaPane extends BorderPane {

        private MediaPlayer mediaPlayer;

        private final MediaView mediaView;

        /**
         * The Duration of the media. *
         */
        private Duration duration;

        /**
         * The container for the media controls. *
         */
        private final HBox mediaTools;

        /**
         * The container for the media video output. *
         */
        private final HBox mediaViewPane;

        private final VBox controlPanel;

        private final Slider progressSlider;

        private final Button pauseButton;

        private final Button stopButton;

        private final Label progressLabel;

        private final Label infoLabel;

        private int totalHours;

        private int totalMinutes;

        private int totalSeconds;

        private final String durationFormat = "%02d:%02d:%02d/%02d:%02d:%02d  "; //NON-NLS

        private static final String PLAY_TEXT = "►";

        private static final String PAUSE_TEXT = "||";

        private static final String STOP_TEXT = "X"; //NON-NLS

        public MediaPane() {
            // Video Display
            mediaViewPane = new HBox();
            mediaViewPane.setStyle("-fx-background-color: black"); //NON-NLS
            mediaViewPane.setAlignment(Pos.CENTER);
            mediaView = new MediaView();
            mediaViewPane.getChildren().add(mediaView);
            setCenter(mediaViewPane);

            // Media Controls
            controlPanel = new VBox();
            mediaTools = new HBox();
            mediaTools.setAlignment(Pos.CENTER);
            mediaTools.setPadding(new Insets(5, 10, 5, 10));

            pauseButton = new Button(PLAY_TEXT);
            stopButton = new Button(STOP_TEXT);
            mediaTools.getChildren().add(pauseButton);
            mediaTools.getChildren().add(new Label("  "));
            mediaTools.getChildren().add(stopButton);
            mediaTools.getChildren().add(new Label("  "));
            progressSlider = new Slider();
            HBox.setHgrow(progressSlider, Priority.ALWAYS);
            progressSlider.setMinWidth(50);
            progressSlider.setMaxWidth(Double.MAX_VALUE);
            mediaTools.getChildren().add(progressSlider);
            progressLabel = new Label();
            progressLabel.setPrefWidth(135);
            progressLabel.setMinWidth(135);
            mediaTools.getChildren().add(progressLabel);

            controlPanel.getChildren().add(mediaTools);
            controlPanel.setStyle("-fx-background-color: white"); //NON-NLS
            infoLabel = new Label("");
            controlPanel.getChildren().add(infoLabel);
            setBottom(controlPanel);
            setProgressActionListeners();
        }

        /**
         * Reset this MediaPane.
         *
         */
        public void reset() {
            if (mediaPlayer != null) {
                setInfoLabelText("");
                if (mediaPlayer.getStatus() == Status.PLAYING) {
                    mediaPlayer.stop();
                }
                mediaPlayer = null;
                mediaView.setMediaPlayer(null);
            }
            resetProgress();
        }

        /**
         * Set the Information Label of this MediaPane.
         *
         * @param text
         */
        public void setInfoLabelText(final String text) {
            logger.log(Level.INFO, "Setting Info Label Text: {0}", text); //NON-NLS
            Platform.runLater(() -> {
                infoLabel.setText(text);
            });
        }

        /**
         * Set the size of the MediaPane and it's components.
         *
         * @param dims the current dimensions of the DataContentViewer
         */
        public void setFit(final Dimension dims) {
            Platform.runLater(() -> {
                setPrefSize(dims.getWidth(), dims.getHeight());
                // Set the Video output to fit the size allocated for it. give an
                // extra few px to ensure the info label will be shown
                mediaView.setFitHeight(dims.getHeight() - controlPanel.getHeight());
            });
        }

        /**
         * Set the action listeners for the pause button and progress slider.
         */
        private void setProgressActionListeners() {
            pauseButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent e) {
                    if (mediaPlayer == null) {
                        return;
                    }

                    Status status = mediaPlayer.getStatus();

                    switch (status) {
                        // If playing, pause
                        case PLAYING:
                            mediaPlayer.pause();
                            break;
                        // If ready, paused or stopped, continue playing
                        case READY:
                        case PAUSED:
                        case STOPPED:
                            mediaPlayer.play();
                            break;
                        default:
                            logger.log(Level.INFO, "MediaPlayer in unexpected state: {0}", status.toString()); //NON-NLS
                            // If the MediaPlayer is in an unexpected state, stop playback.
                            mediaPlayer.stop();
                            setInfoLabelText(NbBundle.getMessage(this.getClass(),
                                    "FXVideoPanel.pauseButton.infoLabel.playbackErr"));
                            break;
                    }
                }
            });

            stopButton.setOnAction((ActionEvent e) -> {
                if (mediaPlayer == null) {
                    return;
                }

                mediaPlayer.stop();
            });

            progressSlider.valueProperty().addListener((Observable o) -> {
                if (mediaPlayer == null) {
                    return;
                }

                if (progressSlider.isValueChanging()) {
                    mediaPlayer.seek(duration.multiply(progressSlider.getValue() / 100.0));
                }
            });
        }

        /**
         * Reset the progress label and slider to zero.
         */
        private void resetProgress() {
            totalHours = 0;
            totalMinutes = 0;
            totalSeconds = 0;
            progressSlider.setValue(0.0);
            updateTime(Duration.ZERO);
        }

        /**
         * Construct a MediaPlayer from the given Media URI.
         *
         * Also adds the necessary listeners to MediaPlayer events.
         *
         * @param mediaUri the location of the media.
         *
         * @return a MediaPlayer
         */
        private MediaPlayer createMediaPlayer(String mediaUri) {
            Media media = new Media(mediaUri);

            MediaPlayer player = new MediaPlayer(media);
            player.setOnReady(new ReadyListener());
            final Runnable pauseListener = () -> {
                pauseButton.setText(PLAY_TEXT);
            };
            player.setOnPaused(pauseListener);
            player.setOnStopped(pauseListener);
            player.setOnPlaying(() -> {
                pauseButton.setText(PAUSE_TEXT);
            });
            player.setOnEndOfMedia(new EndOfMediaListener());

            player.currentTimeProperty().addListener((observable, oldTime, newTime) -> {
                updateSlider(newTime);
                updateTime(newTime);
            });

            return player;
        }

        /**
         * Update the progress slider and label with the current time of the
         * media.
         */
        private void updateProgress() {
            if (mediaPlayer == null) {
                return;
            }
            Duration currentTime = mediaPlayer.getCurrentTime();
            updateSlider(currentTime);
            updateTime(currentTime);
        }

        /**
         * Update the slider with the current time.
         *
         * @param currentTime
         */
        private void updateSlider(Duration currentTime) {
            if (progressSlider != null) {
                progressSlider.setDisable(currentTime.isUnknown());
                if (!progressSlider.isDisabled() && duration.greaterThan(Duration.ZERO)
                        && !progressSlider.isValueChanging()) {
                    progressSlider.setValue(currentTime.divide(duration.toMillis()).toMillis() * 100.0);
                }
            }
        }

        /**
         * Update the progress label with the current time.
         *
         * @param currentTime
         */
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
            Platform.runLater(() -> {
                progressLabel.setText(durationStr);
            });
        }

        private void setInfoLabelToolTipText(final String text) {
            Platform.runLater(() -> {
                infoLabel.setTooltip(new Tooltip(text));
            });
        }

        /**
         * Responds to MediaPlayer onReady events.
         *
         * Updates the progress label with the duration of the media.
         */
        private class ReadyListener implements Runnable {

            @Override
            public void run() {
                if (mediaPlayer == null) {
                    return;
                }

                duration = mediaPlayer.getMedia().getDuration();
                long durationInMillis = (long) mediaPlayer.getMedia().getDuration().toMillis();

                // pick out the total hours, minutes, seconds
                long durationSeconds = (int) durationInMillis / 1000;
                totalHours = (int) durationSeconds / 3600;
                durationSeconds -= totalHours * 3600;
                totalMinutes = (int) durationSeconds / 60;
                durationSeconds -= totalMinutes * 60;
                totalSeconds = (int) durationSeconds;
                updateProgress();
            }
        }

        /**
         * Responds to MediaPlayer onEndOfMediaEvents.
         *
         * Prepares the media to be replayed.
         */
        private class EndOfMediaListener implements Runnable {

            @Override
            public void run() {
                if (mediaPlayer == null) {
                    return;
                }

                Duration beginning = mediaPlayer.getStartTime();
                mediaPlayer.stop();
                mediaPlayer.pause();
                pauseButton.setText(PLAY_TEXT);
                updateSlider(beginning);
                updateTime(beginning);
            }
        }

        /**
         * Thread that extracts Media from a Sleuthkit file representation to a
         * Java file representation that the Media Player can take as input.
         */
        private class ExtractMedia extends Task<Long> {

            private ProgressHandle progress;

            private final AbstractFile sourceFile;

            private final java.io.File tempFile;

            ExtractMedia(AbstractFile sFile, java.io.File jFile) {
                this.sourceFile = sFile;
                this.tempFile = jFile;
            }

            /**
             * Get the URI of the media file.
             *
             * @return the URI of the media file.
             */
            public String getMediaUri() {
                return Paths.get(tempFile.getAbsolutePath()).toUri().toString();
            }

            @Override
            protected Long call() throws Exception {
                if (tempFile.exists() == false || tempFile.length() < sourceFile.getSize()) {
                    progress = ProgressHandle.createHandle(
                            NbBundle.getMessage(this.getClass(),
                                    "FXVideoPanel.progress.bufferingFile",
                                    sourceFile.getName()
                            ),
                            () -> ExtractMedia.this.cancel(true));

                    Platform.runLater(() -> {
                        progressLabel.setText(NbBundle.getMessage(this.getClass(), "FXVideoPanel.progressLabel.buffering"));
                    });

                    progress.start(100);
                    try {
                        Files.createParentDirs(tempFile);
                        return ContentUtils.writeToFile(sourceFile, tempFile, progress, this, true);
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Error buffering file", ex); //NON-NLS
                        return 0L;
                    } finally {
                        logger.log(Level.INFO, "Done buffering: {0}", tempFile.getName()); //NON-NLS
                    }
                }
                return 0L;
            }

            @Override
            protected void failed() {
                super.failed();
                onDone();
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                onDone();
            }

            @Override
            protected void cancelled() {
                super.cancelled();
                onDone();
            }

            private void onDone() {
                progressLabel.setText("");
                try {
                    super.get(); //block and get all exceptions thrown while doInBackground()
                } catch (CancellationException ex) {
                    logger.log(Level.INFO, "Media buffering was canceled."); //NON-NLS
                    progressLabel.setText(NbBundle.getMessage(this.getClass(), "FXVideoPanel.progress.bufferingCancelled"));
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, "Media buffering was interrupted."); //NON-NLS
                    progressLabel.setText(NbBundle.getMessage(this.getClass(), "FXVideoPanel.progress.bufferingInterrupted"));
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Fatal error during media buffering.", ex); //NON-NLS
                    progressLabel.setText(NbBundle.getMessage(this.getClass(), "FXVideoPanel.progress.errorWritingVideoToDisk"));
                } finally {
                    if (null != progress) {
                        progress.finish();
                    }
                    if (!this.isCancelled()) {
                        logger.log(Level.INFO, "ExtractMedia is done: {0}", tempFile.getName()); //NON-NLS
                        try {
                            mediaPane.mediaPlayer = mediaPane.createMediaPlayer(getMediaUri());
                            mediaView.setMediaPlayer(mediaPane.mediaPlayer);
                        } catch (MediaException ex) {
                            progressLabel.setText("");
                            mediaPane.setInfoLabelText(NbBundle.getMessage(this.getClass(), "FXVideoPanel.media.unsupportedFormat"));
                        }
                    }
                }
            }
        }
    }

    /**
     * @param file      a video file from which to capture frames
     * @param numFrames the number of frames to capture. These frames will be
     *                  captured at successive intervals given by
     *                  durationOfVideo/numFrames. If this frame interval is
     *                  less than MIN_FRAME_INTERVAL_MILLIS, then only one frame
     *                  will be captured and returned.
     *
     * @return a List of VideoFrames representing the captured frames.
     */
    @Override
    public List<VideoFrame> captureFrames(java.io.File file, int numFrames) throws Exception {
        //What is/was the point of this method /interface.
        return null;
    }

    @Override
    public String[] getExtensions() {
        return EXTENSIONS.clone();
    }

    @Override
    public List<String> getMimeTypes() {
        return MIMETYPES;
    }
}
