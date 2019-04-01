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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.ClockTime;
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
import javax.swing.event.ChangeListener;
import org.freedesktop.gstreamer.GstException;

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
    private PlayBin gstPlayBin;
    private JavaFxAppSink fxAppSink;
    private JFXPanel fxPanel;
    private volatile boolean livePlayBin;
    private volatile boolean hasError;

    //When a video is playing, update the UI every 75 ms
    private final Timer timer = new Timer(75, new VideoPanelUpdater());
    private static final int PROGRESS_SLIDER_SIZE = 2000;

    private ExtractMedia extractMediaWorker;

    /**
     * Creates new form MediaViewVideoPanel
     */
    public MediaPlayerPanel() throws GstException, UnsatisfiedLinkError {
        initComponents();
        initGst();
        customizeComponents();
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
                    long newPos = (long) (relativePosition * duration);
                    gstPlayBin.seek(newPos, TimeUnit.NANOSECONDS);
                    //Keep constantly updating the time label so users have a sense of
                    //where the slider they are dragging is in relation to the video time
                    updateTimeLabel(newPos, duration);
                }
            }
        });

        //Manage the audio level when the user is adjusting the volumn slider
        audioSlider.addChangeListener((ChangeEvent event) -> {
            if (audioSlider.getValueIsAdjusting()) {
                int audioPercent = audioSlider.getValue() * 2;
                gstPlayBin.setVolumePercent(audioPercent);
            }
        });

        videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
        fxPanel = new JFXPanel();
        videoPanel.add(fxPanel);//add jfx ui to JPanel
    }

    private void initGst() throws GstException, UnsatisfiedLinkError {
        logger.log(Level.INFO, "Attempting initializing of gstreamer for video/audio viewing"); //NON-NLS
        Gst.init();
        gstPlayBin = new PlayBin("VideoPlayer");
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
        reset();

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
     * Assume no support on a fresh reset until we begin loading the file
     * for play.
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
        timer.stop();
        if(livePlayBin && !hasError) {
            gstPlayBin.stop();
        }
        
        hasError = false;
        livePlayBin = false;        
        gstPlayBin.dispose();

        if (fxAppSink != null) {
            fxAppSink.clear();
        }

        videoPanel.removeAll();
        
        if (extractMediaWorker != null) {
            extractMediaWorker.cancel(true);
        }

        resetComponents();
        enableComponents(false);
    }

    /**
     * If the node has been reset but messages from the previous PlayBin are
     * still firing, ignore them.
     */
    synchronized void setLabelText(String msg) {
        if (livePlayBin) {
            infoLabel.setText(msg);
        }
    }

    private void enableComponents(boolean isEnabled) {
        playButton.setEnabled(isEnabled);
        progressSlider.setEnabled(isEnabled);
        videoPanel.setEnabled(isEnabled);
        audioSlider.setEnabled(isEnabled);
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
     * @param posNs
     * @param totalNs
     */
    private void updateTimeLabel(long start, long total) {
        progressLabel.setText(formatTime(start, false) + "/" + formatTime(total, true));
    }

    /**
     * Convert nanoseconds into an HH:MM:SS format.
     */
    @NbBundle.Messages({
        "MediaPlayerPanel.unknownTime=Unknown",
        "MediaPlayerPanel.timeFormat=%02d:%02d:%02d"
    })
    private String formatTime(long ns, boolean ceiling) {
        if (ns == -1) {
            return Bundle.MediaPlayerPanel_unknownTime();
        }

        double millis = ns / 1000000.0;
        double seconds;
        if (ceiling) {
            seconds = Math.ceil(millis / 1000);
        } else {
            seconds = millis / 1000;
        }
        double hours = seconds / 3600;
        seconds -= (int) hours * 3600;
        double minutes = seconds / 60;
        seconds -= (int) minutes * 60;

        return String.format(Bundle.MediaPlayerPanel_timeFormat(), (int) hours, (int) minutes, (int) seconds);
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
                progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progress.buffering"));
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

                //Video is ready for playback. Clean up previous components and create new ones
                gstPlayBin = new PlayBin("VideoPlayer", tempFile.toURI());
                //Create a custom AppSink that hooks into JavaFx panels for video display
                fxPanel = new JFXPanel();
                fxAppSink = new JavaFxAppSink("JavaFxAppSink", fxPanel);
                gstPlayBin.setVideoSink(fxAppSink);
                
                videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
                videoPanel.add(fxPanel);//add jfx ui to JPanel

                //Configure event handling
                attachEOSListener(gstPlayBin); //Handle end of video events
                attachStateListener(gstPlayBin); //Handle syncing play/pause button to the stream state
                attachErrorListener(gstPlayBin); //Handle errors gracefully when they are encountered

                //Customize components
                gstPlayBin.setVolumePercent(audioSlider.getValue() * 2);

                /**
                 * Prepare the PlayBin for playback.
                 */
                gstPlayBin.ready();
                livePlayBin = true;
                //Customize components
                enableComponents(true);
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Media buffering was canceled."); //NON-NLS
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Media buffering was interrupted."); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during media buffering.", ex); //NON-NLS
            }
        }

        /**
         * Listens for the end of stream event, in which case we conveniently
         * reset the video for the user.
         */
        private void attachEOSListener(PlayBin gstPlayBin) {
            gstPlayBin.getBus().connect(new Bus.EOS() {
                @Override
                public void endOfStream(GstObject go) {
                    gstPlayBin.seek(ClockTime.ZERO);
                    progressSlider.setValue(0);
                    /**
                     * Keep the video from automatically playing
                     */
                    Gst.getExecutorService().submit(() -> gstPlayBin.pause());
                }
            });
        }

        /**
         * Listen for state changes and update the play/pause button
         * accordingly. In addition, handle the state transition from 
         * READY -> PAUSED.
         */
        private void attachStateListener(PlayBin gstPlayBin) {
            gstPlayBin.getBus().connect(new Bus.STATE_CHANGED() {
                @Override
                public void stateChanged(GstObject go, State oldState, State currentState, State pendingState) {
                    /**
                     * If we are ready, it is safe to transition to the pause state
                     * to initiate data-flow for pre-roll frame and duration
                     * information.
                     */
                    if (State.READY.equals(currentState)) {
                        Gst.getExecutorService().submit(() -> gstPlayBin.pause());
                        timer.start();
                    }

                    if (State.PLAYING.equals(currentState)) {
                        playButton.setText("||");
                    } else {
                        playButton.setText("►");
                    }
                }
            });
        }

        /**
         * On error messages disable the UI and show the user an error was
         * encountered.
         */
        private void attachErrorListener(PlayBin gstPlayBin) {
            gstPlayBin.getBus().connect(new Bus.ERROR() {
                @Override
                public void errorMessage(GstObject go, int i, String string) {
                    enableComponents(false);
                    setLabelText(String.format("<html><font color='red'>%s</font></html>",
                            MEDIA_PLAYER_ERROR_STRING));
                    timer.stop();
                    hasError = true;
                }
            });
        }
    }

    /**
     * Updates the video time bar and the time label when a video is playing.
     */
    private class VideoPanelUpdater implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!progressSlider.getValueIsAdjusting()) {
                if(livePlayBin) {
                    long position = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
                    long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                    /**
                     * Duration may not be known until there is video data in the
                     * pipeline. We start this updater when data-flow has just been 
                     * initiated so buffering may still be in progress.
                     */
                    if (duration != -1) {
                        double relativePosition = (double) position / duration;
                        progressSlider.setValue((int) (relativePosition * PROGRESS_SLIDER_SIZE));
                    }

                    updateTimeLabel(position, duration);
                }
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

        videoPanel = new javax.swing.JPanel();
        controlPanel = new javax.swing.JPanel();
        progressSlider = new javax.swing.JSlider();
        infoLabel = new javax.swing.JLabel();
        playButton = new javax.swing.JButton();
        progressLabel = new javax.swing.JLabel();
        VolumeIcon = new javax.swing.JLabel();
        audioSlider = new javax.swing.JSlider();

        javax.swing.GroupLayout videoPanelLayout = new javax.swing.GroupLayout(videoPanel);
        videoPanel.setLayout(videoPanelLayout);
        videoPanelLayout.setHorizontalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        videoPanelLayout.setVerticalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 259, Short.MAX_VALUE)
        );

        progressSlider.setValue(0);
        progressSlider.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        progressSlider.setDoubleBuffered(true);
        progressSlider.setMinimumSize(new java.awt.Dimension(36, 21));
        progressSlider.setPreferredSize(new java.awt.Dimension(200, 21));

        org.openide.awt.Mnemonics.setLocalizedText(infoLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.infoLabel.text")); // NOI18N
        infoLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        org.openide.awt.Mnemonics.setLocalizedText(playButton, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.playButton.text")); // NOI18N
        playButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(progressLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.progressLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(VolumeIcon, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.VolumeIcon.text")); // NOI18N

        audioSlider.setMajorTickSpacing(10);
        audioSlider.setMaximum(50);
        audioSlider.setMinorTickSpacing(5);
        audioSlider.setPaintTicks(true);
        audioSlider.setToolTipText(org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaPlayerPanel.audioSlider.toolTipText")); // NOI18N
        audioSlider.setValue(25);
        audioSlider.setMinimumSize(new java.awt.Dimension(200, 21));
        audioSlider.setPreferredSize(new java.awt.Dimension(200, 21));

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, controlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addComponent(playButton, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 680, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(progressLabel))
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addComponent(infoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(VolumeIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(2, 2, 2)
                        .addComponent(audioSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 229, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(progressLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(playButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(audioSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(VolumeIcon, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(infoLabel)))
                .addGap(13, 13, 13))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(controlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void playButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playButtonActionPerformed
        if(gstPlayBin.isPlaying()) {
            gstPlayBin.pause();
        } else {
            gstPlayBin.play();
        }
    }//GEN-LAST:event_playButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel VolumeIcon;
    private javax.swing.JSlider audioSlider;
    private javax.swing.JPanel controlPanel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JButton playButton;
    private javax.swing.JLabel progressLabel;
    private javax.swing.JSlider progressSlider;
    private javax.swing.JPanel videoPanel;
    // End of variables declaration//GEN-END:variables
}
