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

import com.sun.jna.Native;
import java.awt.Dimension;
import java.awt.Image;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.MediaPlayer;
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;

/**
 * Video viewer part of the Media View layered pane
 */
@ServiceProviders(value = {
    @ServiceProvider(service = FrameCapture.class)
})
public class MediaViewVideoPanel extends javax.swing.JPanel implements FrameCapture {

    private static final Logger logger = Logger.getLogger(MediaViewVideoPanel.class.getName());
    private boolean vlcInited;
    private static final long MIN_FRAME_INTERVAL_MILLIS = 500;
    private static final long FRAME_CAPTURE_TIMEOUT_MILLIS = 1000;
    private static final String MEDIA_PLAYER_ERROR_STRING = "The media player cannot process this file.";
    private static final int POS_FACTOR = 10000;
    //playback
    private long durationMillis = 0;
    private VideoProgressWorker videoProgressWorker;
    private int totalHours, totalMinutes, totalSeconds;
    private MediaPlayer vlcMediaPlayer;
    private EmbeddedMediaPlayerComponent vlcVideoComponent;
    private boolean autoTracking = false; // true if the slider is moving automatically
    private AbstractFile currentFile;
    private java.io.File currentVideoFile;
    private boolean replay;
    private Set<String> badVideoFiles = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Creates new form MediaViewVideoPanel
     */
    public MediaViewVideoPanel() {
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

    public EmbeddedMediaPlayerComponent getVideoComponent() {
        return vlcVideoComponent;
    }

    public boolean isInited() {
        return vlcInited;
    }

    private void customizeComponents() {
        if (!initVlc()) {
            return;
        }

        progressSlider.setEnabled(false); // disable slider; enable after user plays vid
        progressSlider.setValue(0);

        progressSlider.addChangeListener(new ChangeListener() {
            /**
             * Should always try to synchronize any call to
             * progressSlider.setValue() to avoid a different thread changing
             * playbin while stateChanged() is processing
             */
            @Override
            public void stateChanged(ChangeEvent e) {
                if (vlcMediaPlayer != null && !autoTracking) {
                    float positionValue = progressSlider.getValue() / (float) POS_FACTOR;
                    // Avoid end of file freeze-up
                    if (positionValue > 0.99f) {
                        positionValue = 0.99f;
                    }
                    vlcMediaPlayer.setPosition(positionValue);
                }
            }
        });
    }
    
    private void pause() {
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.setPause(true);
            pauseButton.setText("►");
        }
    }
    
    private void unPause() {
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.setPause(false);
            pauseButton.setText("||");
        }
    }
    
    /**
     * Reset the video for replaying the current file.
     * 
     * Called when finished Event is fired from MediaPlayer.
     */
    private void finished() {
        if (videoProgressWorker != null) {
            videoProgressWorker.cancel(true);
            videoProgressWorker = null;
        }
        
        logger.log(Level.INFO, "Resetting media");
        replay = true;
    }

    /**
     * Load the VLC library dll using JNA.
     * 
     * @return <code>true</code>, if the library was loaded correctly. 
     *         <code>false</code>, otherwise.
     */
    private boolean initVlc() {
        try {
            Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);
            vlcInited = true;
        } catch (UnsatisfiedLinkError e) {
            logger.log(Level.SEVERE, "Error initalizing vlc for audio/video viewing and extraction capabilities", e);
            MessageNotifyUtil.Notify.error("Error initializing vlc for audio/video viewing and frame extraction capabilities. "
                    + " Video and audio viewing will be disabled. ", e.getMessage());
            vlcInited = false;
        }
        return vlcInited;
    }

    /**
     * Initialize all the necessary vars to play a video/audio file.
     *
     * @param file video file to play
     * @param dims dimension of the parent window
     */
    void setupVideo(final AbstractFile file, final Dimension dims) {
        infoLabel.setText("");
        currentFile = file;
        replay = false;
        final boolean deleted = file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
        if (deleted) {
            infoLabel.setText("Playback of deleted videos is not supported, use an external player.");
            videoPanel.removeAll();
            pauseButton.setEnabled(false);
            progressSlider.setEnabled(false);
            return;
        }

        String path = "";
        try {
            path = file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Cannot get unique path of video file");
        }
        infoLabel.setText(path);
        infoLabel.setToolTipText(path);
        pauseButton.setEnabled(true);
        progressSlider.setEnabled(true);
        
        vlcVideoComponent = new EmbeddedMediaPlayerComponent();
        vlcMediaPlayer = vlcVideoComponent.getMediaPlayer();
        vlcMediaPlayer.setPlaySubItems(true);
        vlcMediaPlayer.addMediaPlayerEventListener(new VlcMediaPlayerEventListener());
        videoPanel.removeAll();
        videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
        videoPanel.add(vlcVideoComponent);
        videoPanel.setVisible(true);
        logger.log(Level.INFO, "Created media player.");
        
        ExtractMedia em = new ExtractMedia(currentFile, getJFile(currentFile));
        em.execute();
    }

    void reset() {
        // reset the progress label text on the event dispatch thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressLabel.setText("");
            }
        });

        if (!isInited()) {
            return;
        }
        
        // get rid of any existing videoProgressWorker thread
        if (videoProgressWorker != null) {
            videoProgressWorker.cancel(true);
            videoProgressWorker = null;
        }
        
        if (vlcMediaPlayer != null) {
            if (vlcMediaPlayer.isPlaying()) {
                vlcMediaPlayer.stop();
            }
            vlcMediaPlayer.release();
            vlcMediaPlayer = null;
            logger.log(Level.INFO, "Released media player");
        }
        if (vlcVideoComponent != null) {
            vlcVideoComponent.release(true);
            vlcVideoComponent = null;
            logger.log(Level.INFO, "Released video component");
        }

        currentFile = null;
        currentVideoFile = null;
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
    
    void playMedia() {
        logger.log(Level.INFO, "In play media");
        if (currentVideoFile == null || !currentVideoFile.exists()) {
            progressLabel.setText("Error buffering file");
            return;
        }

        boolean mediaPrepared = vlcMediaPlayer.prepareMedia(currentVideoFile.getAbsolutePath());
        if (mediaPrepared) {
            vlcMediaPlayer.parseMedia();
            durationMillis = vlcMediaPlayer.getMediaMeta().getLength();
            logger.log(Level.INFO, "Media loaded correctly");
        } else {
            progressLabel.setText(MEDIA_PLAYER_ERROR_STRING);
        }

        // pick out the total hours, minutes, seconds
        long durationSeconds = (int) durationMillis / 1000;
        totalHours = (int) durationSeconds / 3600;
        durationSeconds -= totalHours * 3600;
        totalMinutes = (int) durationSeconds / 60;
        durationSeconds -= totalMinutes * 60;
        totalSeconds = (int) durationSeconds;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressSlider.setMaximum(POS_FACTOR);
                progressSlider.setMinimum(0);

                logger.log(Level.INFO, "Starting the media...");
                vlcMediaPlayer.start();
                pauseButton.setText("||");
                videoProgressWorker = new VideoProgressWorker();
                videoProgressWorker.execute();
            }
        });
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

        List<VideoFrame> frames = new ArrayList<>();

        if (!isInited()) {
            return frames;
        }

        // throw exception if this file is known to be problematic
        if (badVideoFiles.contains(file.getName())) {
            throw new Exception("Cannot capture frames from this file (" + file.getName() + ").");
        }

        MediaPlayerFactory mediaPlayerFactory = new MediaPlayerFactory();
        MediaPlayer mediaPlayer = mediaPlayerFactory.newHeadlessMediaPlayer();
        boolean mediaPrepared = mediaPlayer.prepareMedia(file.getAbsolutePath());
        if (!mediaPrepared) {
            return frames;
        }
        // get the duration of the video
        long myDurationMillis = mediaPlayer.getLength();
        if (myDurationMillis <= 0) {
            return frames;
        }

        // calculate the number of frames to capture
        int numFramesToGet = numFrames;
        long frameInterval = myDurationMillis / numFrames;
        if (frameInterval < MIN_FRAME_INTERVAL_MILLIS) {
            numFramesToGet = 1;
        }

        // for each timeStamp, grap a frame
        for (int i = 0; i < numFramesToGet; ++i) {
            long timeStamp = i * frameInterval;

            mediaPlayer.setTime(timeStamp);
            mediaPlayer.pause();

            Image snapShot = mediaPlayer.getSnapshot();

            frames.add(new VideoFrame(snapShot, timeStamp));
        }

        return frames;
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
            .addGap(0, 188, Short.MAX_VALUE)
        );

        org.openide.awt.Mnemonics.setLocalizedText(pauseButton, org.openide.util.NbBundle.getMessage(MediaViewVideoPanel.class, "MediaViewVideoPanel.pauseButton.text")); // NOI18N
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        progressSlider.setSnapToTicks(true);

        org.openide.awt.Mnemonics.setLocalizedText(progressLabel, org.openide.util.NbBundle.getMessage(MediaViewVideoPanel.class, "MediaViewVideoPanel.progressLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(infoLabel, org.openide.util.NbBundle.getMessage(MediaViewVideoPanel.class, "MediaViewVideoPanel.infoLabel.text")); // NOI18N

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addComponent(pauseButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 357, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressLabel)
                .addContainerGap())
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addComponent(infoLabel)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pauseButton)
                    .addComponent(progressSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(progressLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(infoLabel))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(videoPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(controlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void pauseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButtonActionPerformed
        if (replay) {
            // File has completed playing. Play button now replays
            logger.log(Level.INFO, "Replaying video.");
            replay = false;
            playMedia();
        } else if (vlcMediaPlayer.isPlaying()) {
            logger.log(Level.INFO, "Pausing.");
            this.pause();
        } else {
            logger.log(Level.INFO, "Playing");
            this.unPause();
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

    void releaseVlcComponents() {
        if (vlcMediaPlayer != null) {
            vlcMediaPlayer.release();
            vlcMediaPlayer = null;
        }
        
        if (vlcVideoComponent != null) {
            vlcVideoComponent.release();
            vlcVideoComponent = null;
        }
    }

    private class VideoProgressWorker extends SwingWorker<Object, Object> {

        private String durationFormat = "%02d:%02d:%02d/%02d:%02d:%02d  ";
        private long millisElapsed = 0;
        private float currentPosition = 0.0f;
        private final long INTER_FRAME_PERIOD_MS = 20;
        private final long END_TIME_MARGIN_MS = 50;
        private boolean hadError = false;

        private boolean isMediaPlayerReady() {
            return vlcMediaPlayer != null;
        }

        private void resetVideo() throws Exception {
            pauseButton.setText("►");

            String durationStr = String.format(durationFormat, 0, 0, 0,
                    totalHours, totalMinutes, totalSeconds);
            progressLabel.setText(durationStr);
        }

//        /**
//         * @return true while millisElapsed is greater than END_TIME_MARGIN_MS
//         * from durationMillis. This is used to indicate when the video has
//         * ended because for some videos the time elapsed never becomes equal to
//         * the reported duration of the video.
//         */
//        private boolean hasNotEnded() {
//            boolean ended = (durationMillis - millisElapsed) > END_TIME_MARGIN_MS;
//            return ended;
//        }

        @Override
        protected Object doInBackground() throws Exception {

            // enable the slider
            progressSlider.setEnabled(true);

            int elapsedHours = -1, elapsedMinutes = -1, elapsedSeconds = -1;
            while (isMediaPlayerReady() && !isCancelled()) {
                
                millisElapsed = vlcMediaPlayer.getTime();
                currentPosition = vlcMediaPlayer.getPosition();
                
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
                autoTracking = true;
                progressSlider.setValue((int) (currentPosition * POS_FACTOR));
                autoTracking = false;

                try {
                    Thread.sleep(INTER_FRAME_PERIOD_MS);
                } catch (InterruptedException ex) {
                    break;
                }
            }

            // disable the slider
            progressSlider.setEnabled(false);

            resetVideo();

            return null;
        }
    } //end class progress worker

    /* Thread that extracts and plays a file */
    private class ExtractMedia extends SwingWorker<Object, Void> {

        private ProgressHandle progress;
        boolean success = false;
        private AbstractFile sFile;
        private java.io.File jFile;
        private String duration;
        private String position;
        private long extractedBytes;

        ExtractMedia(org.sleuthkit.datamodel.AbstractFile sFile, java.io.File jFile) {
            this.sFile = sFile;
            this.jFile = jFile;
        }

        public long getExtractedBytes() {
            return extractedBytes;
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
            progressLabel.setText("Buffering...  ");
            progress.start();
            progress.switchToDeterminate(100);
            try {
                extractedBytes = ContentUtils.writeToFile(sFile, jFile, progress, this, true);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error buffering file", ex);
            }
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
                    logger.log(Level.INFO, "Loaded media file");
                    currentVideoFile = jFile;
                    playMedia();
                }
            }
        }
    }
    
    private class VlcMediaPlayerEventListener extends MediaPlayerEventAdapter {
        
        @Override
        public void finished(MediaPlayer mediaPlayer) {
            logger.log(Level.INFO, "Media Player finished playing the media");
            finished();
        }
        
        @Override
        public void error(MediaPlayer mediaPlayer) {
            logger.log(Level.INFO, "an Error occured.");
        }
        
        @Override
        public void mediaDurationChanged(MediaPlayer mediaPlayer, long newDuration) {
            logger.log(Level.INFO, "DURATION CHANGED: " + newDuration);
        }
        
        @Override
        public void mediaFreed(MediaPlayer mediaPlayer) {
            logger.log(Level.INFO, "Media was freed");
        }
        
        @Override
        public void lengthChanged(MediaPlayer mediaPlayer, long newLength) {
            logger.log(Level.INFO, "LENGTH CHANGED: " + newLength);
        }
    }
}
