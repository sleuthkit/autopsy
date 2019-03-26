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
import org.freedesktop.gstreamer.ClockTime;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstException;
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
    private static final String MEDIA_PLAYER_ERROR_STRING = NbBundle.getMessage(MediaPlayerPanel.class, "GstVideoPanel.cannotProcFile.err");

    private PlayBin gstPlayBin;
    private final Object playbinLock = new Object(); // lock for synchronization of gstPlayBin player
    private boolean gstInited;

    private final Timer timer = new Timer(PLAYER_STATUS_UPDATE_INTERVAL_MS, new VideoPanelUpdater());
    private volatile ExtractMedia extractMediaWorker;

    private static final long END_TIME_MARGIN_NS = 50000000;
    private static final int PLAYER_STATUS_UPDATE_INTERVAL_MS = 50;

    /**
     * Creates new form MediaViewVideoPanel
     */
    public MediaPlayerPanel() {
        initComponents();
        customizeComponents();
    }

    /**
     * Has this MediaPlayerPanel been initialized correctly?
     *
     * @return if GST was successfully initialized
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

        //Manage the gstreamer video position when a user is dragging the slider in the panel.
        progressSlider.addChangeListener((ChangeEvent event) -> {
            if (progressSlider.getValueIsAdjusting()) {
                synchronized (playbinLock) {
                    long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                    long position = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);
                    if (duration > 0) {
                        double relativePosition = progressSlider.getValue() / 2000.0;
                        gstPlayBin.seek((long) (relativePosition * duration), TimeUnit.NANOSECONDS);
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
            gstPlayBin = new PlayBin("VideoPlayer");
        } catch (GstException | UnsatisfiedLinkError ex) {
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
     * Loads the file by spawning off a background task to handle file copying
     * and video component initializations.
     *
     * @param file Media file to play.
     */
    @NbBundle.Messages({"GstVideoPanel.noOpenCase.errMsg=No open case available."})
    void loadFile(final AbstractFile file) {
        //Ensure everything is back in the inital state
        reset();

        if (file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            infoLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.setupVideo.infoLabel.text"));
            return;
        }

        try {
            String path = file.getUniquePath();
            infoLabel.setText(path);
            infoLabel.setToolTipText(path);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Cannot get unique path of video file.", ex); //NON-NLS
        }

        try {
            extractMediaWorker = new ExtractMedia(file, VideoUtils.getVideoFileInTempDir(file));
            extractMediaWorker.execute();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            infoLabel.setText(Bundle.GstVideoPanel_noOpenCase_errMsg());
            pauseButton.setEnabled(false);
            progressSlider.setEnabled(false);
        }
    }

    /**
     * Return this panel to its initial state.
     */
    void reset() {
        if (!isInited()) {
            return;
        }

        timer.stop();
        synchronized (playbinLock) {
            gstPlayBin.dispose();
        }

        if (extractMediaWorker != null) {
            extractMediaWorker.cancel(true);
        }
        
        videoPanel.removeAll();

        pauseButton.setEnabled(false);
        progressSlider.setEnabled(false);
        progressLabel.setText("00:00:00/00:00:00");
        infoLabel.setText("");
        progressSlider.setValue(0);
        pauseButton.setText("►");
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
            switch (gstPlayBin.getState()) {
                case PLAYING:
                    pauseButton.setText("►");
                    if (gstPlayBin.pause() == StateChangeReturn.FAILURE) {
                        infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    }
                    break;
                case PAUSED:
                case READY:
                case NULL:
                    pauseButton.setText("||");
                    if (gstPlayBin.play() == StateChangeReturn.FAILURE) {
                        infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    }
                    break;
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
            if (tempFile.exists() == false || tempFile.length() < sourceFile.getSize()) {
                progress = ProgressHandle.createHandle(NbBundle.getMessage(MediaPlayerPanel.class, "GstVideoPanel.ExtractMedia.progress.buffering", sourceFile.getName()), () -> this.cancel(true));
                progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progress.buffering"));
                progress.start(100);
                try {
                    Files.createParentDirs(tempFile);
                    ContentUtils.writeToFile(sourceFile, tempFile, progress, this, true);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error buffering file", ex); //NON-NLS
                }
                progress.finish();
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
                //PlayBin file is ready for playback, initialize all components.
                synchronized (playbinLock) {
                    gstPlayBin = new PlayBin("VideoPlayer"); //NON-NLS
                    gstPlayBin.setInputFile(tempFile);
                    GstVideoRendererPanel gstVideoRenderer = new GstVideoRendererPanel();
                    gstPlayBin.setVideoSink(gstVideoRenderer.getVideoSink());
                    videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
                    videoPanel.add(gstVideoRenderer);//add jfx ui to JPanel
                    
                    /*
                     * It seems like PlayBin cannot be queried for duration
                     * until the video is actually being played. This call
                     * to pause below is used to 'initialize' the PlayBin to
                     * display the duration in the content viewer before the
                     * play button is pressed. This is a suggested solution
                     * in the gstreamer google groups page as this use case 
                     * doesn't seem to be supported out of the box.
                     */
                    gstPlayBin.pause();
                    timer.start();

                    videoPanel.setVisible(true);
                    pauseButton.setEnabled(true);
                    progressSlider.setEnabled(true);
                }
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
                synchronized (playbinLock) {
                    long duration = gstPlayBin.queryDuration(TimeUnit.NANOSECONDS);
                    long position = gstPlayBin.queryPosition(TimeUnit.NANOSECONDS);

                    //Duration is -1 when the PlayBin is not playing or paused. Do
                    //nothing in this case.
                    if (duration <= 0) {
                        return;
                    }

                    long positionDelta = duration - position;
                    //NOTE: This conditional is problematic and is responsible for JIRA-4863
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

                    String durationStr = String.format("%s/%s", formatTime(position), formatTime(duration));
                    progressLabel.setText(durationStr);
                }
            }
        }

        /**
         * Convert nanoseconds into an HH:MM:SS format.
         */
        private String formatTime(long ns) {
            long millis = ns / 1000000;
            long seconds = (int) millis / 1000;
            long hours = (int) seconds / 3600;
            seconds -= hours * 3600;
            long minutes = (int) seconds / 60;
            seconds -= minutes * 60;
            seconds = (int) seconds;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
}
