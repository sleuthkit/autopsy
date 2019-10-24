/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.VideoUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;
import javafx.embed.swing.JFXPanel;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicSliderUI.TrackListener;
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.GstException;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.freedesktop.gstreamer.event.SeekType;

/**
 * This is a video player that is part of the Media View layered pane. It uses
 * GStreamer to process the video and JavaFX to display it.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class MediaPlayerPanel extends JPanel implements MediaFileViewer.MediaViewPanel {

    //Enumerate the accepted file extensions and mimetypes
    private static final String[] FILE_EXTENSIONS = new String[]{
        ".3g2",
        ".3gp",
        ".3gpp",
        ".aac",
        ".aif",
        ".aiff",
        ".amr",
        ".asf",
        ".au",
        ".avi",
        ".flac",
        ".flv",
        ".m4a",
        ".m4v",
        ".mka",
        ".mkv",
        ".mov",
        ".mp2",
        ".mp3",
        ".mp4",
        ".mpeg",
        ".mpg",
        ".mxf",
        ".ogg",
        ".wav",
        ".webm",
        ".wma",
        ".wmv",}; //NON-NLS
    private static final List<String> MIME_TYPES = Arrays.asList(
            "video/3gpp",
            "video/3gpp2",
            "audio/aiff",
            "audio/amr-wb",
            "audio/basic",
            "audio/mp4",
            "video/mp4",
            "audio/mpeg",
            "video/mpeg",
            "audio/mpeg3",
            "application/mxf",
            "application/ogg",
            "video/quicktime",
            "audio/vorbis",
            "audio/vnd.wave",
            "video/webm",
            "video/x-3ivx",
            "audio/x-aac",
            "audio/x-adpcm",
            "audio/x-alaw",
            "audio/x-cinepak",
            "video/x-divx",
            "audio/x-dv",
            "video/x-dv",
            "video/x-ffv",
            "audio/x-flac",
            "video/x-flv",
            "audio/x-gsm",
            "video/x-h263",
            "video/x-h264",
            "video/x-huffyuv",
            "video/x-indeo",
            "video/x-intel-h263",
            "audio/x-ircam",
            "video/x-jpeg",
            "audio/x-m4a",
            "video/x-m4v",
            "audio/x-mace",
            "audio/x-matroska",
            "video/x-matroska",
            "audio/x-mpeg",
            "video/x-mpeg",
            "audio/x-mpeg-3",
            "video/x-ms-asf",
            "audio/x-ms-wma",
            "video/x-ms-wmv",
            "video/x-msmpeg",
            "video/x-msvideo",
            "video/x-msvideocodec",
            "audio/x-mulaw",
            "audio/x-nist",
            "audio/x-oggflac",
            "audio/x-paris",
            "audio/x-qdm2",
            "audio/x-raw",
            "video/x-raw",
            "video/x-rle",
            "audio/x-speex",
            "video/x-svq",
            "audio/x-svx",
            "video/x-tarkin",
            "video/x-theora",
            "audio/x-voc",
            "audio/x-vorbis",
            "video/x-vp3",
            "audio/x-w64",
            "audio/x-wav",
            "audio/x-wma",
            "video/x-wmv",
            "video/x-xvid"
    ); //NON-NLS

    private static final Logger logger = Logger.getLogger(MediaPlayerPanel.class.getName());
    private static final String MEDIA_PLAYER_ERROR_STRING = NbBundle.getMessage(MediaPlayerPanel.class,
            "GstVideoPanel.cannotProcFile.err");

    //Video playback components
    private volatile PlayBin gstPlayBin;
    private JavaFxAppSink fxAppSink;
    private Bus.ERROR errorListener;
    private Bus.STATE_CHANGED stateChangeListener;
    private Bus.EOS endOfStreamListener;

    //Update progress bar and time label during video playback
    //Updating every 16 MS = 62.5 FPS.
    private final Timer timer = new Timer(16, new VideoPanelUpdater());
    private static final int PROGRESS_SLIDER_SIZE = 2000;
    private static final int SKIP_IN_SECONDS = 30;

    private ExtractMedia extractMediaWorker;

    //Serialize setting the value of the Video progress slider.
    //The slider is a shared resource between the VideoPanelUpdater
    //and the TrackListener of the JSliderUI.
    private final Semaphore sliderLock;

    /**
     * Creates new form MediaViewVideoPanel
     */
    public MediaPlayerPanel() throws GstException, UnsatisfiedLinkError {
        initComponents();
        customizeComponents();
        //True for fairness. In other words,
        //acquire() calls are processed in order of invocation.
        sliderLock = new Semaphore(1, true);
    }

    private void customizeComponents() {
        progressSlider.setEnabled(false); // disable slider; enable after user plays vid
        progressSlider.setMinimum(0);
        progressSlider.setMaximum(PROGRESS_SLIDER_SIZE);
        progressSlider.setValue(0);
        //Manage the gstreamer video position when a user is dragging the slider in the panel.
        progressSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (progressSlider.getValueIsAdjusting()) {
                    long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                    double relativePosition = progressSlider.getValue() * 1.0 / PROGRESS_SLIDER_SIZE;
                    long newStartTime = (long) (relativePosition * duration);
                    double playBackRate = getPlayBackRate();
                    gstPlayBin.seek(playBackRate,
                            Format.TIME,
                            //FLUSH - flushes the pipeline
                            //ACCURATE - video will seek exactly to the position requested
                            EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                            //Set the start position to newTime
                            SeekType.SET, newStartTime,
                            //Do nothing for the end position
                            SeekType.NONE, -1);
                    //Keep constantly updating the time label so users have a sense of
                    //where the slider they are dragging is in relation to the video time
                    updateTimeLabel(newStartTime, duration);
                }
            }
        });
        //Manage the audio level when the user is adjusting the volumn slider
        audioSlider.addChangeListener((ChangeEvent event) -> {
            if (audioSlider.getValueIsAdjusting()) {
                double audioPercent = (audioSlider.getValue() * 2.0) / 100.0;
                gstPlayBin.setVolume(audioPercent);
            }
        });
        errorListener = new Bus.ERROR() {
            @Override
            public void errorMessage(GstObject go, int i, String string) {
                SwingUtilities.invokeLater(() -> {
                    enableComponents(false);
                    infoLabel.setText(String.format(
                            "<html><font color='red'>%s</font></html>",
                            MEDIA_PLAYER_ERROR_STRING));
                });
                timer.stop();
            }
        };
        stateChangeListener = new Bus.STATE_CHANGED() {
            @Override
            public void stateChanged(GstObject go, State oldState, State currentState, State pendingState) {
                if (State.PLAYING.equals(currentState)) {
                    SwingUtilities.invokeLater(() -> {
                        playButton.setText("||");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        playButton.setText("►");
                    });
                }
            }
        };
        endOfStreamListener = new Bus.EOS() {
            @Override
            public void endOfStream(GstObject go) {
                gstPlayBin.seek(ClockTime.ZERO);
                /**
                 * Keep the video from automatically playing
                 */
                Gst.getExecutor().submit(() -> gstPlayBin.pause());
            }
        };
    }

    /**
     * Loads the file by spawning off a background task to handle file copying
     * and video component initializations.
     *
     * @param file Media file to play.
     */
    @NbBundle.Messages({"GstVideoPanel.noOpenCase.errMsg=No open case available."})
    void loadFile(final AbstractFile file) {
        //Ensure everything is back in the initial state
        infoLabel.setText("");
        if (file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            infoLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.setupVideo.infoLabel.text"));
            return;
        }

        try {
            //Pushing off initialization to the background
            extractMediaWorker = new ExtractMedia(file, VideoUtils.getVideoFileInTempDir(file));
            extractMediaWorker.execute();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            infoLabel.setText(String.format("<html><font color='red'>%s</font></html>", Bundle.GstVideoPanel_noOpenCase_errMsg()));
            enableComponents(false);
        }
    }

    /**
     * Assume no support on a fresh reset until we begin loading the file for
     * play.
     */
    @NbBundle.Messages({
        "MediaPlayerPanel.noSupport=File not supported."
    })
    void resetComponents() {
        progressLabel.setText(String.format("%s/%s", Bundle.MediaPlayerPanel_unknownTime(),
                Bundle.MediaPlayerPanel_unknownTime()));
        infoLabel.setText(Bundle.MediaPlayerPanel_noSupport());
        progressSlider.setValue(0);
    }

    /**
     * Return this panel to its initial state.
     */
    void reset() {
        if (extractMediaWorker != null) {
            extractMediaWorker.cancel(true);
        }
        timer.stop();
        if (gstPlayBin != null) {
            gstPlayBin.stop();
            gstPlayBin.getBus().disconnect(endOfStreamListener);
            gstPlayBin.getBus().disconnect(stateChangeListener);
            gstPlayBin.getBus().disconnect(errorListener);
            gstPlayBin.dispose();
            fxAppSink.clear();
            gstPlayBin = null;
        }
        videoPanel.removeAll();
        resetComponents();
        enableComponents(false);
    }

    private void enableComponents(boolean isEnabled) {
        playButton.setEnabled(isEnabled);
        progressSlider.setEnabled(isEnabled);
        videoPanel.setEnabled(isEnabled);
        audioSlider.setEnabled(isEnabled);
        rewindButton.setEnabled(isEnabled);
        fastForwardButton.setEnabled(isEnabled);
        playBackSpeedComboBox.setEnabled(isEnabled);
    }

    @Override
    public List<String> getSupportedExtensions() {
        return Arrays.asList(FILE_EXTENSIONS.clone());
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return MIME_TYPES;
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        String extension = file.getNameExtension();
        /**
         * Although it seems too restrictive, requiring both a supported
         * extension and a supported MIME type prevents two undesirable
         * behaviors:
         *
         * 1) Until AUT-1766 and AUT-1801 are fixed, we incorrectly identify all
         * iff files as audio/aiff. This means that if this panel went with the
         * looser 'mime type OR extension' criteria we use for images, then this
         * panel would attempt (and fail) to display all iff files, even non
         * audio ones.
         *
         * 2) The looser criteria means we are less confident about the files we
         * are potentialy sending to GStreamer on 32bit jvms. We are less
         * comfortable with the error handling for GStreamer, and don't want to
         * send it files which might cause it trouble.
         */
        if (getSupportedExtensions().contains("." + extension)) {
            SortedSet<String> mimeTypes = new TreeSet<>(getSupportedMimeTypes());
            try {
                String mimeType = new FileTypeDetector().getMIMEType(file);
                return mimeTypes.contains(mimeType);
            } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                logger.log(Level.WARNING, "Failed to look up mimetype for " + file.getName() + " using FileTypeDetector.  Fallingback on AbstractFile.isMimeType", ex);
                if (!mimeTypes.isEmpty() && file.isMimeType(mimeTypes) == AbstractFile.MimeMatchEnum.TRUE) {
                    return true;
                }
            }

            return getSupportedExtensions().contains("." + extension);
        }
        return false;
    }

    /**
     * Formats current time and total time as the following ratio: HH:MM:SS /
     * HH:MM:SS
     *
     * @param start
     * @param total
     */
    private void updateTimeLabel(long start, long total) {
        progressLabel.setText(formatTime(start) + "/" + formatTime(total));
    }

    /**
     * Reads the current selected playback rate from the speed combo box.
     *
     * @return The selected rate.
     */
    private double getPlayBackRate() {
        int selectIndex = playBackSpeedComboBox.getSelectedIndex();
        String selectText = playBackSpeedComboBox.getItemAt(selectIndex);
        return Double.valueOf(selectText.substring(0, selectText.length() - 1));
    }

    /**
     * Convert nanoseconds into an HH:MM:SS format.
     */
    @NbBundle.Messages({
        "MediaPlayerPanel.unknownTime=Unknown",
        "MediaPlayerPanel.timeFormat=%02d:%02d:%02d"
    })
    private String formatTime(long ns) {
        if (ns == -1) {
            return Bundle.MediaPlayerPanel_unknownTime();
        }

        long seconds = TimeUnit.SECONDS.convert(ns, TimeUnit.NANOSECONDS);
        long hours = TimeUnit.HOURS.convert(seconds, TimeUnit.SECONDS);
        seconds -= TimeUnit.SECONDS.convert(hours, TimeUnit.HOURS);
        long minutes = TimeUnit.MINUTES.convert(seconds, TimeUnit.SECONDS);
        seconds -= TimeUnit.SECONDS.convert(minutes, TimeUnit.MINUTES);

        return String.format(Bundle.MediaPlayerPanel_timeFormat(), hours, minutes, seconds);
    }

    /**
     * Thread that extracts a file and initializes all of the playback
     * components.
     */
    private class ExtractMedia extends SwingWorker<Void, Void> {

        private ProgressHandle progress;
        private final AbstractFile sourceFile;
        private final java.io.File tempFile;

        ExtractMedia(AbstractFile sFile, File jFile) {
            this.sourceFile = sFile;
            this.tempFile = jFile;
        }

        @Override
        protected Void doInBackground() throws Exception {
            if (!tempFile.exists() || tempFile.length() < sourceFile.getSize()) {
                progress = ProgressHandle.createHandle(NbBundle.getMessage(MediaPlayerPanel.class, "GstVideoPanel.ExtractMedia.progress.buffering", sourceFile.getName()), () -> this.cancel(true));

                SwingUtilities.invokeLater(() -> {
                    progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progress.buffering"));
                });

                progress.start(100);
                try {
                    Files.createParentDirs(tempFile);
                    ContentUtils.writeToFile(sourceFile, tempFile, progress, this, true);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error creating parent directory for copying video/audio in temp directory", ex); //NON-NLS
                } finally {
                    progress.finish();
                }
            }
            return null;
        }

        /*
         * Initialize the playback components if the extraction was successful.
         */
        @Override
        protected void done() {
            try {
                super.get();

                if (this.isCancelled()) {
                    return;
                }

                // Initialize Gstreamer. It is safe to call this for every file.
                // It was moved here from the constructor because having it happen
                // earlier resulted in conflicts on Linux.
                Gst.init();

                //Video is ready for playback. Create new components
                gstPlayBin = new PlayBin("VideoPlayer", tempFile.toURI());
                //Configure event handling
                Bus playBinBus = gstPlayBin.getBus();
                playBinBus.connect(endOfStreamListener);
                playBinBus.connect(stateChangeListener);
                playBinBus.connect(errorListener);

                if (this.isCancelled()) {
                    return;
                }

                JFXPanel fxPanel = new JFXPanel();
                videoPanel.removeAll();
                videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
                videoPanel.add(fxPanel);
                fxAppSink = new JavaFxAppSink("JavaFxAppSink", fxPanel);
                gstPlayBin.setVideoSink(fxAppSink);

                if (this.isCancelled()) {
                    return;
                }

                gstPlayBin.setVolume((audioSlider.getValue() * 2.0) / 100.0);
                gstPlayBin.pause();

                timer.start();
                enableComponents(true);
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Media buffering was canceled."); //NON-NLS
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Media buffering was interrupted."); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during media buffering.", ex); //NON-NLS
            }
        }
    }

    /**
     * Updates the video time bar and the time label when a video is playing.
     */
    private class VideoPanelUpdater implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!progressSlider.getValueIsAdjusting()) {
                sliderLock.acquireUninterruptibly();
                long position = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
                long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                /**
                 * Duration may not be known until there is video data in the
                 * pipeline. We start this updater when data-flow has just been
                 * initiated so buffering may still be in progress.
                 */
                if (duration >= 0 && position >= 0) {
                    double relativePosition = (double) position / duration;
                    progressSlider.setValue((int) (relativePosition * PROGRESS_SLIDER_SIZE));
                }

                SwingUtilities.invokeLater(() -> {
                    updateTimeLabel(position, duration);
                });
                sliderLock.release();
            }
        }
    }

    /**
     * Represents the default configuration for the circular JSliderUI.
     */
    private class CircularJSliderConfiguration {

        //Thumb configurations
        private final Color thumbColor;
        private final Dimension thumbDimension;

        //Track configurations
        //Progress bar can be bisected into a seen group 
        //and an unseen group.
        private final Color unseen;
        private final Color seen;

        /**
         * Default configuration
         *
         * JSlider is light blue RGB(0,130,255). Seen track is light blue
         * RGB(0,130,255). Unseen track is light grey RGB(192, 192, 192).
         *
         * @param thumbDimension Size of the oval thumb.
         */
        public CircularJSliderConfiguration(Dimension thumbDimension) {
            Color lightBlue = new Color(0, 130, 255);

            seen = lightBlue;
            unseen = Color.LIGHT_GRAY;

            thumbColor = lightBlue;

            this.thumbDimension = new Dimension(thumbDimension);
        }

        public Color getThumbColor() {
            return thumbColor;
        }

        public Color getUnseenTrackColor() {
            return unseen;
        }

        public Color getSeenTrackColor() {
            return seen;
        }

        public Dimension getThumbDimension() {
            return new Dimension(thumbDimension);
        }
    }

    /**
     * Custom view for the JSlider.
     */
    private class CircularJSliderUI extends BasicSliderUI {

        private final CircularJSliderConfiguration config;

        /**
         * Creates a custom view for the JSlider. This view draws a blue oval
         * thumb at the given width and height. It also paints the track blue as
         * the thumb progresses.
         *
         * @param slider JSlider component
         * @param config Configuration object. Contains info about thumb
         * dimensions and colors.
         */
        public CircularJSliderUI(JSlider slider, CircularJSliderConfiguration config) {
            super(slider);
            this.config = config;
        }

        @Override
        protected Dimension getThumbSize() {
            return config.getThumbDimension();
        }

        /**
         * Modifies the View to be an oval rather than the rectangle Controller.
         */
        @Override
        public void paintThumb(Graphics graphic) {
            Rectangle thumb = this.thumbRect;

            Color original = graphic.getColor();

            //Change the thumb view from the rectangle
            //controller to an oval.
            graphic.setColor(config.getThumbColor());
            Dimension thumbDimension = config.getThumbDimension();
            graphic.fillOval(thumb.x, thumb.y, thumbDimension.width, thumbDimension.height);

            //Preserve the graphics original color
            graphic.setColor(original);
        }

        @Override
        public void paintTrack(Graphics graphic) {
            //This rectangle is the bounding box for the progress bar
            //portion of the slider. The track is painted in the middle
            //of this rectangle and the thumb laid overtop.
            Rectangle track = this.trackRect;

            //Get the location of the thumb, this point splits the
            //progress bar into 2 line segments, seen and unseen.
            Rectangle thumb = this.thumbRect;
            int thumbX = thumb.x;
            int thumbY = thumb.y;

            Color original = graphic.getColor();

            //Paint the seen side
            graphic.setColor(config.getSeenTrackColor());
            graphic.drawLine(track.x, track.y + track.height / 2,
                    thumbX, thumbY + track.height / 2);

            //Paint the unseen side
            graphic.setColor(config.getUnseenTrackColor());
            graphic.drawLine(thumbX, thumbY + track.height / 2,
                    track.x + track.width, track.y + track.height / 2);

            //Preserve the graphics color.
            graphic.setColor(original);
        }

        @Override
        protected TrackListener createTrackListener(JSlider slider) {
            return new CustomTrackListener();
        }

        @Override
        protected void scrollDueToClickInTrack(int direction) {
            //Set the thumb position to the mouse press location, as opposed
            //to the closest "block" which is the default behavior.
            Point mousePosition = slider.getMousePosition();
            if (mousePosition == null) {
                return;
            }
            int value = this.valueForXPosition(mousePosition.x);

            //Lock the slider down, which is a shared resource.
            //The VideoPanelUpdater (dedicated thread) keeps the
            //slider in sync with the video position, so without 
            //proper locking our change could be overwritten.
            sliderLock.acquireUninterruptibly();
            slider.setValueIsAdjusting(true);
            slider.setValue(value);
            slider.setValueIsAdjusting(false);
            sliderLock.release();
        }

        /**
         * Applies anti-aliasing if available.
         */
        @Override
        public void update(Graphics graphic, JComponent component) {
            if (graphic instanceof Graphics2D) {
                Graphics2D graphic2 = (Graphics2D) graphic;
                graphic2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
            }

            super.update(graphic, component);
        }

        /**
         * This track listener will force the thumb to be snapped to the mouse
         * location. This makes grabbing and dragging the JSlider much easier.
         * Using the default track listener, the user would have to click
         * exactly on the slider thumb to drag it. Now the thumb positions
         * itself under the mouse so that it can always be dragged.
         */
        private class CustomTrackListener extends CircularJSliderUI.TrackListener {

            @Override
            public void mousePressed(MouseEvent e) {
                if (!slider.isEnabled()) {
                    return;
                }
                //Snap the thumb to position of the mouse
                scrollDueToClickInTrack(0);

                //Pause the video for convenience
                gstPlayBin.pause();

                //Handle the event as normal.
                super.mousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!slider.isEnabled()) {
                    return;
                }

                super.mouseReleased(e);

                //Unpause once the mouse has been released.
                gstPlayBin.play();
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        videoPanel = new javax.swing.JPanel();
        controlPanel = new javax.swing.JPanel();
        progressSlider = new javax.swing.JSlider();
        progressLabel = new javax.swing.JLabel();
        buttonPanel = new javax.swing.JPanel();
        playButton = new javax.swing.JButton();
        fastForwardButton = new javax.swing.JButton();
        rewindButton = new javax.swing.JButton();
        VolumeIcon = new javax.swing.JLabel();
        audioSlider = new javax.swing.JSlider();
        infoLabel = new javax.swing.JLabel();
        playBackPanel = new javax.swing.JPanel();
        playBackSpeedComboBox = new javax.swing.JComboBox<>();
        playBackSpeedLabel = new javax.swing.JLabel();

        javax.swing.GroupLayout videoPanelLayout = new javax.swing.GroupLayout(videoPanel);
        videoPanel.setLayout(videoPanelLayout);
        videoPanelLayout.setHorizontalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        videoPanelLayout.setVerticalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 131, Short.MAX_VALUE)
        );

        progressSlider.setValue(0);
        progressSlider.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        progressSlider.setDoubleBuffered(true);
        progressSlider.setMinimumSize(new java.awt.Dimension(36, 21));
        progressSlider.setPreferredSize(new java.awt.Dimension(200, 21));
        progressSlider.setUI(new CircularJSliderUI(progressSlider, new CircularJSliderConfiguration(new Dimension(18,18))));

        org.openide.awt.Mnemonics.setLocalizedText(progressLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.progressLabel.text")); // NOI18N

        buttonPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(playButton, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.playButton.text")); // NOI18N
        playButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipadx = 21;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 0, 0);
        buttonPanel.add(playButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(fastForwardButton, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.fastForwardButton.text")); // NOI18N
        fastForwardButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fastForwardButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 6, 0, 0);
        buttonPanel.add(fastForwardButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(rewindButton, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.rewindButton.text")); // NOI18N
        rewindButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rewindButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 1, 0);
        buttonPanel.add(rewindButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(VolumeIcon, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.VolumeIcon.text")); // NOI18N
        VolumeIcon.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipadx = 8;
        gridBagConstraints.ipady = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 14, 0, 0);
        buttonPanel.add(VolumeIcon, gridBagConstraints);

        audioSlider.setMajorTickSpacing(10);
        audioSlider.setMaximum(50);
        audioSlider.setMinorTickSpacing(5);
        audioSlider.setToolTipText(org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.audioSlider.toolTipText")); // NOI18N
        audioSlider.setValue(25);
        audioSlider.setMinimumSize(new java.awt.Dimension(200, 21));
        audioSlider.setPreferredSize(new java.awt.Dimension(200, 21));
        audioSlider.setUI(new CircularJSliderUI(audioSlider, new CircularJSliderConfiguration(new Dimension(15,15))));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipadx = -116;
        gridBagConstraints.ipady = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 1, 0, 10);
        buttonPanel.add(audioSlider, gridBagConstraints);

        infoLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        org.openide.awt.Mnemonics.setLocalizedText(infoLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.infoLabel.text")); // NOI18N
        infoLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        playBackSpeedComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "0.25x", "0.50x", "0.75x", "1x", "1.25x", "1.50x", "1.75x", "2x" }));
        playBackSpeedComboBox.setSelectedIndex(3);
        playBackSpeedComboBox.setMaximumSize(new java.awt.Dimension(53, 23));
        playBackSpeedComboBox.setMinimumSize(new java.awt.Dimension(53, 23));
        playBackSpeedComboBox.setPreferredSize(new java.awt.Dimension(53, 23));
        playBackSpeedComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playBackSpeedComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(playBackSpeedLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.playBackSpeedLabel.text")); // NOI18N

        javax.swing.GroupLayout playBackPanelLayout = new javax.swing.GroupLayout(playBackPanel);
        playBackPanel.setLayout(playBackPanelLayout);
        playBackPanelLayout.setHorizontalGroup(
            playBackPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(playBackPanelLayout.createSequentialGroup()
                .addComponent(playBackSpeedLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(playBackSpeedComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(13, 13, 13))
        );
        playBackPanelLayout.setVerticalGroup(
            playBackPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(playBackPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(playBackPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(playBackSpeedComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(playBackSpeedLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(infoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, controlPanelLayout.createSequentialGroup()
                        .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(buttonPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 623, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(progressLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(playBackPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                        .addGap(10, 10, 10)))
                .addGap(0, 0, 0))
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(progressLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(5, 5, 5)
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(buttonPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(playBackPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(14, 14, 14)
                .addComponent(infoLabel))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(controlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void rewindButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rewindButtonActionPerformed
        long currentTime = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
        //Skip 30 seconds.
        long rewindDelta = TimeUnit.NANOSECONDS.convert(SKIP_IN_SECONDS, TimeUnit.SECONDS);
        //Ensure new video position is within bounds
        long newTime = Math.max(currentTime - rewindDelta, 0);
        double playBackRate = getPlayBackRate();
        gstPlayBin.seek(playBackRate,
                Format.TIME,
                //FLUSH - flushes the pipeline
                //ACCURATE - video will seek exactly to the position requested
                EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                //Set the start position to newTime
                SeekType.SET, newTime,
                //Do nothing for the end position
                SeekType.NONE, -1);
    }//GEN-LAST:event_rewindButtonActionPerformed

    private void fastForwardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fastForwardButtonActionPerformed
        long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
        long currentTime = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
        //Skip 30 seconds.
        long fastForwardDelta = TimeUnit.NANOSECONDS.convert(SKIP_IN_SECONDS, TimeUnit.SECONDS);

        //Ignore fast forward requests if there are less than 30 seconds left.
        if (currentTime + fastForwardDelta >= duration) {
            return;
        }

        long newTime = currentTime + fastForwardDelta;
        double playBackRate = getPlayBackRate();
        gstPlayBin.seek(playBackRate,
                Format.TIME,
                //FLUSH - flushes the pipeline
                //ACCURATE - video will seek exactly to the position requested
                EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                //Set the start position to newTime
                SeekType.SET, newTime,
                //Do nothing for the end position
                SeekType.NONE, -1);
    }//GEN-LAST:event_fastForwardButtonActionPerformed

    private void playButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playButtonActionPerformed
        if (gstPlayBin.isPlaying()) {
            gstPlayBin.pause();
        } else {
            double playBackRate = getPlayBackRate();
            long currentTime = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
            //Set playback rate before play.
            gstPlayBin.seek(playBackRate,
                    Format.TIME,
                    //FLUSH - flushes the pipeline
                    //ACCURATE - video will seek exactly to the position requested
                    EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                    //Set the start position to newTime
                    SeekType.SET, currentTime,
                    //Do nothing for the end position
                    SeekType.NONE, -1);
            gstPlayBin.play();
        }
    }//GEN-LAST:event_playButtonActionPerformed

    private void playBackSpeedComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playBackSpeedComboBoxActionPerformed
        double playBackRate = getPlayBackRate();
        long currentTime = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
        gstPlayBin.seek(playBackRate,
                Format.TIME,
                //FLUSH - flushes the pipeline
                //ACCURATE - video will seek exactly to the position requested
                EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                //Set the position to the currentTime, we are only adjusting the
                //playback rate.
                SeekType.SET, currentTime,
                SeekType.NONE, 0);
    }//GEN-LAST:event_playBackSpeedComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel VolumeIcon;
    private javax.swing.JSlider audioSlider;
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JPanel controlPanel;
    private javax.swing.JButton fastForwardButton;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JPanel playBackPanel;
    private javax.swing.JComboBox<String> playBackSpeedComboBox;
    private javax.swing.JLabel playBackSpeedLabel;
    private javax.swing.JButton playButton;
    private javax.swing.JLabel progressLabel;
    private javax.swing.JSlider progressSlider;
    private javax.swing.JButton rewindButton;
    private javax.swing.JPanel videoPanel;
    // End of variables declaration//GEN-END:variables
}
