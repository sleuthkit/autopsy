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

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.FXMLConstructor;

public class MediaControl extends BorderPane implements Fitable {

    private final MediaPlayer mp;

    private MediaView mediaView;

    private final boolean repeat = false;

    private boolean stopRequested = false;

    private boolean atEndOfMedia = false;

    private Duration duration;

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private Button controlButton;

    @FXML
    private Label timeLabel;

    @FXML
    private Slider timeSlider;

    @FXML
    private Slider volumeSlider;

    @FXML
    void initialize() {
        assert controlButton != null : "fx:id=\"controlButton\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert timeLabel != null : "fx:id=\"timeLabel\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert timeSlider != null : "fx:id=\"timeSlider\" was not injected: check your FXML file 'MediaControl.fxml'.";
        assert volumeSlider != null : "fx:id=\"volumeSlider\" was not injected: check your FXML file 'MediaControl.fxml'.";

        mediaView = new MediaView(mp);
        mediaView.setPreserveRatio(true);
        mediaView.fitHeightProperty().bind(this.heightProperty().subtract(50));
        mediaView.fitWidthProperty().bind(this.widthProperty());
        setCenter(mediaView);

        controlButton.setOnAction((ActionEvent e) -> {
            try {
                Status status = mp.getStatus();

                if (status == Status.UNKNOWN || status == Status.HALTED) {
                    // don't do anything in these states
                    return;
                }

                if (status == Status.PAUSED
                        || status == Status.READY
                        || status == Status.STOPPED) {
                    // rewind the movie if we're sitting at the end
                    if (atEndOfMedia) {
                        mp.seek(mp.getStartTime());
                        atEndOfMedia = false;
                    }
                    mp.play();
                } else {
                    mp.pause();
                }
            } catch (Exception ex) {
                Logger.getAnonymousLogger().log(Level.SEVERE, "message", ex);
            }
        });
        mp.currentTimeProperty().addListener((Observable ov) -> {
            updateValues();
        });

        mp.setOnPlaying(() -> {
            if (stopRequested) {
                mp.pause();
                stopRequested = false;
            } else {
                controlButton.setText("||");
            }
        });

        mp.setOnPaused(() -> {
            System.out.println("onPaused");
            controlButton.setText(">");
        });

        mp.setOnReady(() -> {
            duration = mp.getMedia().getDuration();
            updateValues();
        });

        mp.setCycleCount(repeat ? MediaPlayer.INDEFINITE : 1);
        mp.setOnEndOfMedia(() -> {
            if (!repeat) {
                controlButton.setText(">");
                stopRequested = true;
                atEndOfMedia = true;
            }
        });

        // Add time slider
        timeSlider.setMinWidth(50);
        timeSlider.setMaxWidth(Double.MAX_VALUE);
        timeSlider.valueProperty().addListener((Observable ov) -> {
            if (timeSlider.isValueChanging()) {
                // multiply duration by percentage calculated by slider position
                mp.seek(duration.multiply(timeSlider.getValue() / 100.0));
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

    public MediaControl(final MediaPlayer mp) {
        this.mp = mp;

        FXMLConstructor.construct(this, "MediaControl.fxml");
    }

    protected void updateValues() {
        if (timeLabel != null && timeSlider != null && volumeSlider != null) {
            Platform.runLater(() -> {
                Duration currentTime = mp.getCurrentTime();
                timeLabel.setText(formatTime(currentTime, duration));
                timeSlider.setDisable(duration.isUnknown());
                if (!timeSlider.isDisabled()
                        && duration.greaterThan(Duration.ZERO)
                        && !timeSlider.isValueChanging()) {
                    timeSlider.setValue(currentTime.divide(duration.toMillis()).toMillis()
                            * 100.0);
                }
                if (!volumeSlider.isValueChanging()) {
                    volumeSlider.setValue((int) Math.round(mp.getVolume()
                            * 100));
                }
            });
        }
    }

    private static String formatTime(Duration elapsed, Duration duration) {
        int intElapsed = (int) Math.floor(elapsed.toSeconds());
        int elapsedHours = intElapsed / (60 * 60);
        if (elapsedHours > 0) {
            intElapsed -= elapsedHours * 60 * 60;
        }
        int elapsedMinutes = intElapsed / 60;
        int elapsedSeconds = intElapsed - elapsedHours * 60 * 60
                - elapsedMinutes * 60;

        if (duration.greaterThan(Duration.ZERO)) {
            int intDuration = (int) Math.floor(duration.toSeconds());
            int durationHours = intDuration / (60 * 60);
            if (durationHours > 0) {
                intDuration -= durationHours * 60 * 60;
            }
            int durationMinutes = intDuration / 60;
            int durationSeconds = intDuration - durationHours * 60 * 60
                    - durationMinutes * 60;
            if (durationHours > 0) {
                return String.format("%d:%02d:%02d/%d:%02d:%02d",
                                     elapsedHours, elapsedMinutes, elapsedSeconds,
                                     durationHours, durationMinutes, durationSeconds);
            } else {
                return String.format("%02d:%02d/%02d:%02d",
                                     elapsedMinutes, elapsedSeconds, durationMinutes,
                                     durationSeconds);
            }
        } else {
            if (elapsedHours > 0) {
                return String.format("%d:%02d:%02d", elapsedHours,
                                     elapsedMinutes, elapsedSeconds);
            } else {
                return String.format("%02d:%02d", elapsedMinutes,
                                     elapsedSeconds);
            }
        }
    }

    @Override
    public final void setFitWidth(double d) {
        mediaView.setFitWidth(d);
    }

    @Override
    public final double getFitWidth() {
        return mediaView.getFitWidth();
    }

    @Override
    public final DoubleProperty fitWidthProperty() {
        return mediaView.fitWidthProperty();
    }

    @Override
    public final void setFitHeight(double d) {
        mediaView.setFitHeight(d);
    }

    @Override
    public final double getFitHeight() {
        return mediaView.getFitHeight();
    }

    @Override
    public final DoubleProperty fitHeightProperty() {
        return mediaView.fitHeightProperty();
    }

    public void stopVideo() {
        mp.stop();
    }

    @Override
    public boolean isPreserveRatio() {
        return mediaView.isPreserveRatio();
    }

    @Override
    public void setPreserveRatio(boolean b) {
        mediaView.setPreserveRatio(b);
    }

    @Override
    public BooleanProperty preserveRatioProperty() {
        return mediaView.preserveRatioProperty();
    }
}
