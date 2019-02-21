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
import java.awt.Dimension;
import java.awt.EventQueue;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstException;
import org.freedesktop.gstreamer.State;
import org.freedesktop.gstreamer.StateChangeReturn;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.VideoUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * This is a video player that is part of the Media View layered pane. It uses
 * GStreamer to process the video and JavaFX to display it.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class MediaPlayerPanel extends JPanel implements MediaFileViewer.MediaViewPanel {

    private static final String[] FILE_EXTENSIONS = new String[] {
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
        ".wmv",
    }; //NON-NLS
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
    private boolean gstInited;
    private static final String MEDIA_PLAYER_ERROR_STRING = NbBundle.getMessage(MediaPlayerPanel.class, "GstVideoPanel.cannotProcFile.err");
    //playback
    private long durationMillis = 0;
    private int totalHours, totalMinutes, totalSeconds;
    private volatile PlayBin gstPlayBin;
    private GstVideoRendererPanel gstVideoRenderer;
    private final Object playbinLock = new Object(); // lock for synchronization of gstPlayBin player
    private AbstractFile currentFile;
    
    private Timer timer;
    private ExtractMedia extractMediaWorker;
    
    private static final long END_TIME_MARGIN_NS = 50000000;
    private static final int PLAYER_STATUS_UPDATE_INTERVAL_MS = 50;

    /**
     * Creates new form MediaViewVideoPanel
     */
    public MediaPlayerPanel() {
        initComponents();
        customizeComponents();
    }

    public JButton getPauseButton() {
        return pauseButton;
    }

    public JLabel getProgressLabel() {
        return progressLabel;
    }

    public JSlider getProgressSlider() {
        return progressSlider;
    }

    public JPanel getVideoPanel() {
        return videoPanel;
    }

    /**
     * Has this MediaPlayerPanel been initialized correctly?
     *
     * @return
     */
    public boolean isInited() {
        return gstInited;
    }

    private void customizeComponents() {
        if (!initGst()) {
            return;
        }

        progressSlider.setEnabled(false); // disable slider; enable after user plays vid
        progressSlider.setMinimum(0);
        progressSlider.setMaximum(2000);
        progressSlider.setValue(0);
        progressSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                if (gstPlayBin == null) {
                    return;
                }
                if (progressSlider.getValueIsAdjusting()) {
                    synchronized (playbinLock) {
                        long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                        long position = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
                        if (duration > 0) {
                            double relativePosition = progressSlider.getValue() / 2000.0;
                            gstPlayBin.seek((long) (relativePosition * duration), TimeUnit.NANOSECONDS);
                        } else if (position > 0 || progressSlider.getValue() > 0) {
                            gstPlayBin.seek(ClockTime.ZERO);
                            progressSlider.setValue(0);
                        }
                    }
                }
            }
        });
    }

    private boolean initGst() {
        try {
            logger.log(Level.INFO, "Initializing gstreamer for video/audio viewing"); //NON-NLS
            Gst.init();
            gstInited = true;
        } catch (GstException ex) {
            gstInited = false;
            logger.log(Level.SEVERE, "Error initializing gstreamer for audio/video viewing and frame extraction capabilities", ex); //NON-NLS
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(this.getClass(), "GstVideoPanel.initGst.gstException.msg"),
                    ex.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Initialize all the necessary variables to play an audio/video file.
     *
     * @param file Media file to play.
     * @param dims Dimension of the parent window.
     */
    @NbBundle.Messages ({"GstVideoPanel.noOpenCase.errMsg=No open case available."})
    void loadFile(final AbstractFile file, final Dimension dims) {
        EventQueue.invokeLater(() -> {
            reset();
            infoLabel.setText("");
            currentFile = file;
            final boolean deleted = file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
            if (deleted) {
                infoLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.setupVideo.infoLabel.text"));
                videoPanel.removeAll();
                pauseButton.setEnabled(false);
                progressSlider.setEnabled(false);
                return;
            }

            java.io.File ioFile;
            try {
                ioFile = VideoUtils.getVideoFileInTempDir(file);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
                infoLabel.setText(Bundle.GstVideoPanel_noOpenCase_errMsg());
                pauseButton.setEnabled(false);
                progressSlider.setEnabled(false);

                return;
            }

            String path = "";
            try {
                path = file.getUniquePath();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Cannot get unique path of video file.", ex); //NON-NLS
            }
            infoLabel.setText(path);
            infoLabel.setToolTipText(path);
            pauseButton.setEnabled(true);
            progressSlider.setEnabled(true);
            timer = new Timer(PLAYER_STATUS_UPDATE_INTERVAL_MS, event -> {
                if (!progressSlider.getValueIsAdjusting()) {
                    long duration;
                    long position;
                    synchronized (playbinLock) {
                        duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                        position = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
                        if (duration > 0) {
                            long positionDelta = duration - position;
                            if (positionDelta <= END_TIME_MARGIN_NS && gstPlayBin.isPlaying()) {
                                gstPlayBin.pause();
                                if (gstPlayBin.seek(ClockTime.ZERO) == false) {
                                    logger.log(Level.WARNING, "Attempt to call PlayBin.seek() failed."); //NON-NLS
                                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                                    return;
                                }
                                progressSlider.setValue(0);
                                pauseButton.setText("►");
                            } else {
                                double relativePosition = (double) position / duration;
                                progressSlider.setValue((int) (relativePosition * 2000));
                            }
                        }
                    }

                    durationMillis = duration / 1000000;
                    // pick out the total hours, minutes, seconds
                    long durationSeconds = (int) durationMillis / 1000;
                    totalHours = (int) durationSeconds / 3600;
                    durationSeconds -= totalHours * 3600;
                    totalMinutes = (int) durationSeconds / 60;
                    durationSeconds -= totalMinutes * 60;
                    totalSeconds = (int) durationSeconds;

                    long millisElapsed = position / 1000000;
                    // pick out the elapsed hours, minutes, seconds
                    long secondsElapsed = millisElapsed / 1000;
                    int elapsedHours = (int) secondsElapsed / 3600;
                    secondsElapsed -= elapsedHours * 3600;
                    int elapsedMinutes = (int) secondsElapsed / 60;
                    secondsElapsed -= elapsedMinutes * 60;
                    int elapsedSeconds = (int) secondsElapsed;

                    String durationFormat = "%02d:%02d:%02d/%02d:%02d:%02d  "; //NON-NLS
                    String durationStr = String.format(durationFormat,
                            elapsedHours, elapsedMinutes, elapsedSeconds,
                            totalHours, totalMinutes, totalSeconds);
                    progressLabel.setText(durationStr);
                }
            });
            timer.start();

            gstVideoRenderer = new GstVideoRendererPanel();
            synchronized (playbinLock) {
                if (gstPlayBin != null) {
                    gstPlayBin.dispose();
                }
                gstPlayBin = new PlayBin("VideoPlayer"); //NON-NLS
                gstPlayBin.setVideoSink(gstVideoRenderer.getVideoSink());

                videoPanel.removeAll();

                videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));

                videoPanel.add(gstVideoRenderer);//add jfx ui to JPanel

                videoPanel.setVisible(true);

                gstPlayBin.setInputFile(ioFile);
            }
        });
    }

    /**
     * Prepare this MediaViewVideoPanel to accept a different media file.
     */
    void reset() {
        if (timer != null) {
            timer.stop();
        }
        
        // reset the progress label text on the event dispatch thread
        SwingUtilities.invokeLater(() -> {
            progressLabel.setText("");
        });

        if (!isInited()) {
            return;
        }

        synchronized (playbinLock) {
            if (gstPlayBin != null) {
                if (gstPlayBin.isPlaying() && gstPlayBin.stop() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin.stop() failed."); //NON-NLS
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                gstPlayBin.dispose();
                gstPlayBin = null;
            }
            gstVideoRenderer = null;
        }

        progressSlider.setValue(0);
        pauseButton.setText("►");

        currentFile = null;
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
        controlPanel = new javax.swing.JPanel();
        pauseButton = new javax.swing.JButton();
        progressSlider = new javax.swing.JSlider();
        progressLabel = new javax.swing.JLabel();
        infoLabel = new javax.swing.JLabel();

        javax.swing.GroupLayout videoPanelLayout = new javax.swing.GroupLayout(videoPanel);
        videoPanel.setLayout(videoPanelLayout);
        videoPanelLayout.setHorizontalGroup(
                videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGap(0, 0, Short.MAX_VALUE)
        );
        videoPanelLayout.setVerticalGroup(
                videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGap(0, 231, Short.MAX_VALUE)
        );

        org.openide.awt.Mnemonics.setLocalizedText(pauseButton, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaViewVideoPanel.pauseButton.text")); // NOI18N
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(progressLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaViewVideoPanel.progressLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(infoLabel, org.openide.util.NbBundle.getMessage(MediaPlayerPanel.class, "MediaViewVideoPanel.infoLabel.text")); // NOI18N

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
                controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(controlPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(controlPanelLayout.createSequentialGroup()
                                        .addGap(6, 6, 6)
                                        .addComponent(infoLabel)
                                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGroup(controlPanelLayout.createSequentialGroup()
                                        .addComponent(pauseButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 265, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(progressLabel)
                                        .addContainerGap())))
        );
        controlPanelLayout.setVerticalGroup(
                controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(controlPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(progressSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(pauseButton)
                                .addComponent(progressLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(infoLabel)
                        .addContainerGap())
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
    }// </editor-fold>

    private void pauseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButtonActionPerformed
        synchronized (playbinLock) {
            if (gstPlayBin == null) {
                return;
            }
            State state = gstPlayBin.getState();
            if (state.equals(State.PLAYING)) {
                if (gstPlayBin.pause() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin.pause() failed."); //NON-NLS
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                pauseButton.setText("►");
            } else if (state.equals(State.PAUSED)) {
                if (gstPlayBin.play() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin.play() failed."); //NON-NLS
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                pauseButton.setText("||");
            } else if (state.equals(State.READY) || state.equals(State.NULL)) {
                final File tempVideoFile;
                try {
                    tempVideoFile = VideoUtils.getVideoFileInTempDir(currentFile);
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.WARNING, "Exception while getting open case."); //NON-NLS
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }

                if (extractMediaWorker != null) {
                    extractMediaWorker.cancel(true);
                    extractMediaWorker = null;
                }
                extractMediaWorker = new ExtractMedia(currentFile, tempVideoFile);
                extractMediaWorker.execute();

            }
        }
    }//GEN-LAST:event_pauseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlPanel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JButton pauseButton;
    private javax.swing.JLabel progressLabel;
    private javax.swing.JSlider progressSlider;
    private javax.swing.JPanel videoPanel;
    // End of variables declaration//GEN-END:variables

    /**
     * Thread that extracts and plays a file
     */
    private class ExtractMedia extends SwingWorker<Long, Void> {

        private ProgressHandle progress;
        private final AbstractFile sourceFile;
        private final java.io.File tempFile;

        ExtractMedia(AbstractFile sFile, java.io.File jFile) {
            this.sourceFile = sFile;
            this.tempFile = jFile;
        }

        @Override
        protected Long doInBackground() throws Exception {
            if (tempFile.exists() == false || tempFile.length() < sourceFile.getSize()) {
                progress = ProgressHandle.createHandle(NbBundle.getMessage(MediaPlayerPanel.class, "GstVideoPanel.ExtractMedia.progress.buffering", sourceFile.getName()), () -> this.cancel(true));
                progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progress.buffering"));
                progress.start(100);
                try {
                    Files.createParentDirs(tempFile);
                    return ContentUtils.writeToFile(sourceFile, tempFile, progress, this, true);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error buffering file", ex); //NON-NLS
                    return 0L;
                }
            }
            return 0L;
        }

        /*
         * clean up or start the worker threads
         */
        @Override
        protected void done() {
            try {
                super.get(); //block and get all exceptions thrown while doInBackground()
            } catch (CancellationException ex) {
                logger.log(Level.INFO, "Media buffering was canceled."); //NON-NLS
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Media buffering was interrupted."); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Fatal error during media buffering.", ex); //NON-NLS
            } finally {
                if (progress != null) {
                    progress.finish();
                }
                if (!this.isCancelled()) {
                    playMedia();
                }
            }
        }

        void playMedia() {
            if (tempFile == null || !tempFile.exists()) {
                progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progressLabel.bufferingErr"));
                return;
            }
            synchronized (playbinLock) {
                gstPlayBin.seek(ClockTime.ZERO);
                // must play, then pause and get state to get duration.
                if (gstPlayBin.play() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin.play() failed."); //NON-NLS
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                pauseButton.setText("||");
            }
        }
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

}
