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
package org.sleuthkit.autopsy.imagegallery.gui;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.media.MediaView;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FXMLConstructor;
import org.sleuthkit.autopsy.imagegallery.datamodel.VideoFile;

public class VideoPlayer extends BorderPane {

    private static final Image VOLUME_HIGH = new Image("/org/sleuthkit/autopsy/imagegallery/images/speaker-volume.png"); //NON-NLS
    private static final Image VOLUME_LOW = new Image("/org/sleuthkit/autopsy/imagegallery/images/speaker-volume-low.png"); //NON-NLS
    private static final Image VOLUME_ZERO = new Image("/org/sleuthkit/autopsy/imagegallery/images/speaker-volume-none.png"); //NON-NLS
    private static final Image VOLUME_MUTE = new Image("/org/sleuthkit/autopsy/imagegallery/images/speaker-volume-control-mute.png"); //NON-NLS

    private static final Image PLAY = new Image("/org/sleuthkit/autopsy/imagegallery/images/media_controls_play_small.png", true); //NON-NLS
    private static final Image PAUSE = new Image("/org/sleuthkit/autopsy/imagegallery/images/media_controls_pause_small.png", true); //NON-NLS

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
    private ImageView volumeImageView;

    @FXML
    private HBox playControlBar;

    @FXML
    private Button volumeButton;

    InvalidationListener seekListener = new InvalidationListener() {
        @Override
        public void invalidated(Observable ov) {
            mp.seek(Duration.millis(timeSlider.getValue()));
        }
    };
    private final VideoFile file;

    @FXML
    @NbBundle.Messages({"# {0} - exception type",
            "VideoPlayer.errNotice={0}\nSee the logs for details."})
    void initialize() {
        assert controlButton != null : "fx:id=\"controlButton\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert timeSlider != null : "fx:id=\"timeSlider\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert volumeSlider != null : "fx:id=\"volumeSlider\" was not injected: check your FXML file 'MediaControl.fxml'.";
        mp.errorProperty().addListener((Observable observable) -> {
            final MediaException ex = mp.getError();
            if (ex != null) {
                Platform.runLater(() -> {
                    Logger.getLogger(VideoFile.class.getName()).log(Level.WARNING, ex.getType() + " Failed to initialize MediaControl for file " + file.getName(), ex); //NON-NLS
                    setCenter(new Text(Bundle.VideoPlayer_errNotice(ex.getType())));
                    setBottom(null);
                });
            }
        });
        mp.statusProperty().addListener((observableStatus, oldStatus, newStatus) -> {
            Logger.getAnonymousLogger().log(Level.INFO, "media player: {0}", newStatus); //NON-NLS
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

        mp.volumeProperty().addListener((observable, oldVolume, newVolume) -> {
            setVolumeIcon(newVolume);
        });

        mp.muteProperty().addListener((observable, oldMute, newMute) -> {
            if (newMute) {
                volumeImageView.setImage(VOLUME_MUTE);
            } else {
                setVolumeIcon(mp.getVolume());
            }
        });

        timeSlider.valueProperty().addListener(seekListener);

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
        volumeSlider.valueProperty().addListener((Observable ov) -> {
            if (volumeSlider.isValueChanging()) {
                mp.setVolume(volumeSlider.getValue());
                mp.setMute(false);
            }
        });

        volumeButton.setOnAction(event -> {
            mp.setMute(!mp.isMute());
        });
    }

    private void setVolumeIcon(Number newVolume) {
        if (newVolume.doubleValue() < .1) {
            volumeImageView.setImage(VOLUME_ZERO);
        } else if (newVolume.doubleValue() <= .6) {
            volumeImageView.setImage(VOLUME_LOW);
        } else {
            volumeImageView.setImage(VOLUME_HIGH);
        }
    }

    public VideoPlayer(MediaPlayer mp, VideoFile file) {
        this.file = file;
        this.mp = mp;
        FXMLConstructor.construct(this, "MediaControl.fxml"); //NON-NLS
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
            final double volume = mp.getVolume();

            volumeSlider.setValue(volume);

            if (mp.isMute()) {
                volumeImageView.setImage(VOLUME_MUTE);
            } else {
                setVolumeIcon(volume);
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
            return String.format("%d:%02d:%02d", elapsedHours, //NON-NLS
                    elapsedMinutes, elapsedSeconds);
        } else {
            return String.format("%02d:%02d", elapsedMinutes, //NON-NLS
                    elapsedSeconds);
        }
    }

    public void stopVideo() {
        mp.stop();
    }
}
