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
package org.sleuthkit.autopsy.imageanalyzer.gui;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.media.MediaView;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.FXMLConstructor;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.VideoFile;

public class MediaControl extends BorderPane {

    private static final Image PLAY = new Image("/org/sleuthkit/autopsy/imageanalyzer/images/media_controls_play_small.png", true);
    private static final Image PAUSE = new Image("/org/sleuthkit/autopsy/imageanalyzer/images/media_controls_pause_small.png", true);

    private final MediaPlayer mp;

    private final boolean repeat = false;

    private boolean stopRequested = false;

    private boolean atEndOfMedia = false;

    private Duration duration;
    @FXML
    private MediaView mediaView;
    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private Button controlButton;

    @FXML
    private Slider timeSlider;

    @FXML
    private Slider volumeSlider;
    @FXML
    private Label timeLabel;
    @FXML
    private ImageView controlImageView;
    @FXML
    private HBox playControlBar;
    InvalidationListener seekListener;
    private final VideoFile<?> file;

    public static Node create(VideoFile<?> file) {
        try {
            return new MediaControl(new MediaPlayer(file.getMedia()), file);
        } catch (IOException ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, "failed to initialize MediaControl for file " + file.getName(), ex);
            return new Text(ex.getLocalizedMessage() + "\nSee the logs for details.");
        } catch (MediaException ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, ex.getType() + " Failed to initialize MediaControl for file " + file.getName(), ex);
            return new Text(ex.getType() + "\nSee the logs for details.");
        } catch (OutOfMemoryError ex) {
            Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, "failed to initialize MediaControl for file " + file.getName(), ex);
            return new Text("There was a problem playing video file.\nSee the logs for details.");
        }
    }

    @FXML
    void initialize() {
        assert controlButton != null : "fx:id=\"controlButton\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert timeSlider != null : "fx:id=\"timeSlider\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert volumeSlider != null : "fx:id=\"volumeSlider\" was not injected: check your FXML file 'MediaControl.fxml'.";
        mp.errorProperty().addListener((Observable observable) -> {
            final MediaException ex = mp.getError();
            if (ex != null) {
                Platform.runLater(() -> {
                    Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, ex.getType() + " Failed to initialize MediaControl for file " + file.getName(), ex);
                    setCenter(new Text(ex.getType() + "\nSee the logs for details."));
                    setBottom(null);
                });
            }
        });
        mp.statusProperty().addListener((observableStatus, oldStatus, newStatus) -> {
            Logger.getAnonymousLogger().log(Level.INFO, "media player: {0}", newStatus);
        });
        mediaView.setMediaPlayer(mp);
        mediaView.fitHeightProperty().bind(this.heightProperty().subtract(playControlBar.heightProperty()));
        mediaView.fitWidthProperty().bind(this.widthProperty());

        controlButton.setOnAction((ActionEvent e) -> {
            Status status = mp.getStatus();
            switch (status) {
                case UNKNOWN:
                case HALTED:
                    // don't do anything in these states
                    return;
                case PAUSED:
                case READY:
                case STOPPED:
                    // rewind the movie if we're sitting at the end
                    if (atEndOfMedia) {
                        mp.seek(mp.getStartTime());
                        atEndOfMedia = false;
                    }
                    mp.play();
                    break;
                default:
                    mp.pause();
            }
        });

        mp.currentTimeProperty().addListener((Observable ov) -> {
            updateTime();
        });

        mp.setOnPlaying(() -> {
            if (stopRequested) {
                mp.pause();
                stopRequested = false;
            } else {
                controlImageView.setImage(PAUSE);
            }
        });

        mp.setOnPaused(() -> {
            controlImageView.setImage(PLAY);
        });

        mp.setOnReady(() -> {
            duration = mp.getMedia().getDuration();
            timeSlider.setMax(duration.toMillis());
            timeSlider.setMajorTickUnit(duration.toMillis());
            updateTime();
            updateVolume();
        });

        mp.setCycleCount(repeat ? MediaPlayer.INDEFINITE : 1);
        mp.setOnEndOfMedia(() -> {
            if (!repeat) {
                controlImageView.setImage(PLAY);
                stopRequested = true;
                atEndOfMedia = true;
            }
        });
        seekListener = (Observable ov) -> {
//            if (timeSlider.isValueChanging()) {
            mp.seek(Duration.millis(timeSlider.getValue()));
//            }
        };

        // Add time slider
        timeSlider.setLabelFormatter(new StringConverter<Double>() {
            @Override
            public String toString(Double object) {
                return formatTime(Duration.millis(object));
            }

            @Override
            public Double fromString(String string) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        });

        // Add Volume slider
        volumeSlider.setPrefWidth(70);
        volumeSlider.setMaxWidth(Region.USE_PREF_SIZE);
        volumeSlider.setMinWidth(30);
        volumeSlider.valueProperty().addListener((Observable ov) -> {
            if (volumeSlider.isValueChanging()) {
                mp.setVolume(volumeSlider.getValue() / 100.0);
            }
        });
    }

    private MediaControl(MediaPlayer mp, VideoFile<?> file) {
        this.file = file;
        this.mp = mp;
        FXMLConstructor.construct(this, "MediaControl.fxml");
    }

    protected void updateTime() {
        Platform.runLater(() -> {
            Duration currentTime = mp.getCurrentTime();
            timeSlider.setDisable(duration.isUnknown());
            timeLabel.setText(formatTime(currentTime));
            if (!timeSlider.isDisabled()
                    && duration.greaterThan(Duration.ZERO)
                    && !timeSlider.isValueChanging()) {
                timeSlider.valueProperty().removeListener(seekListener);
                timeSlider.setValue(currentTime.toMillis());
                timeSlider.valueProperty().addListener(seekListener);
            }

        });
    }

    private void updateVolume() {
        Platform.runLater(() -> {
            if (!volumeSlider.isValueChanging()) {
                volumeSlider.setValue((int) Math.round(mp.getVolume()
                        * 100));
            }
        });
    }

    private static String formatTime(Duration elapsed) {
        int totalSeconds = (int) Math.floor(elapsed.toSeconds());
        int elapsedHours = totalSeconds / (60 * 60);
        totalSeconds -= elapsedHours * 60 * 60;
        int elapsedMinutes = totalSeconds / 60;
        int elapsedSeconds = totalSeconds - elapsedMinutes * 60;

        if (elapsedHours > 0) {
            return String.format("%d:%02d:%02d", elapsedHours,
                    elapsedMinutes, elapsedSeconds);
        } else {
            return String.format("%02d:%02d", elapsedMinutes,
                    elapsedSeconds);
        }
    }

    public void stopVideo() {
        mp.stop();
    }

}
