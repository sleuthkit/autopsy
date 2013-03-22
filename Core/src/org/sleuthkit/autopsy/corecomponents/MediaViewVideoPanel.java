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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
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
import org.gstreamer.ClockTime;
import org.gstreamer.Gst;
import org.gstreamer.GstException;
import org.gstreamer.State;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.elements.RGBDataSink;
import org.gstreamer.swing.VideoComponent;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Video viewer part of the Media View layered pane
 */
@ServiceProviders(value = {
    @ServiceProvider(service = FrameCapture.class)
})
public class MediaViewVideoPanel extends javax.swing.JPanel implements FrameCapture {

    private static final Logger logger = Logger.getLogger(MediaViewVideoPanel.class.getName());
    private boolean gstInited;
    //frame capture
    private BufferedImage currentImage = null;
    private static final long MIN_FRAME_INTERVAL_MILLIS = 500;
    //playback
    private long durationMillis = 0;
    private VideoProgressWorker videoProgressWorker;
    private int totalHours, totalMinutes, totalSeconds;
    private volatile PlayBin2 gstPlaybin2;
    private VideoComponent gstVideoComponent;
    private boolean autoTracking = false; // true if the slider is moving automatically
    private final Object playbinLock = new Object(); // lock for synchronization of gstPlaybin2 player
    private AbstractFile currentFile;

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

    public VideoComponent getVideoComponent() {
        return gstVideoComponent;
    }

    public boolean isInited() {
        return gstInited;
    }

    private void customizeComponents() {
        initGst();

        progressSlider.setEnabled(false); // disable slider; enable after user plays vid
        progressSlider.setValue(0);

        if (gstInited) {
            progressSlider.addChangeListener(new ChangeListener() {
                /**
                 * Should always try to synchronize any call to
                 * progressSlider.setValue() to avoid a different thread
                 * changing playbin while stateChanged() is processing
                 */
                @Override
                public void stateChanged(ChangeEvent e) {
                    int time = progressSlider.getValue();
                    synchronized (playbinLock) {
                        if (gstPlaybin2 != null && !autoTracking) {
                            State orig = gstPlaybin2.getState();
                            gstPlaybin2.pause();
                            gstPlaybin2.seek(ClockTime.fromMillis(time));
                            gstPlaybin2.setState(orig);
                        }
                    }
                }
            });


        }
    }

    private boolean initGst() {
        try {
            logger.log(Level.INFO, "Initializing gstreamer for video/audio viewing");
            Gst.init();
            gstInited = true;
        } catch (GstException e) {
            gstInited = false;
            logger.log(Level.SEVERE, "Error initializing gstreamer for audio/video viewing and frame extraction capabilities", e);
            MessageNotifyUtil.Notify.error("Error initializing gstreamer for audio/video viewing and frame extraction capabilities. "
                    + " Video and audio viewing will be disabled. ",
                    e.getMessage());
            return false;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | Exception e) {
            gstInited = false;
            logger.log(Level.SEVERE, "Error initializing gstreamer for audio/video viewing and extraction capabilities", e);
            MessageNotifyUtil.Notify.error("Error initializing gstreamer for audio/video viewing frame extraction capabilities. "
                    + " Video and audio viewing will be disabled. ",
                    e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Initialize all the necessary vars to play a video/audio file.
     *
     * @param file video file to play
     * @param dims dimension of the parent window
     */
    void setupVideo(final AbstractFile file, final Dimension dims) {
        currentFile = file;
        final boolean deleted = file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
        if (deleted) {
            infoLabel.setText("Playback of deleted videos is not supported, use an external player. ");
            return;
        } else {
            try {
                String path = file.getUniquePath();
                infoLabel.setText(path);
                infoLabel.setToolTipText(path);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Cannot get unique path of video file");
            }
        }

        java.io.File ioFile = getJFile(file);

          gstVideoComponent = new VideoComponent();
        synchronized (playbinLock) {
            if (gstPlaybin2 != null) {
                gstPlaybin2.dispose();
            }
            gstPlaybin2 = new PlayBin2("VideoPlayer");
            gstPlaybin2.setVideoSink(gstVideoComponent.getElement());

            videoPanel.removeAll();
          

            videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
            videoPanel.add(gstVideoComponent);
           
            videoPanel.setVisible(true);

            gstPlaybin2.setInputFile(ioFile);
            gstPlaybin2.setState(State.READY);
        }

    }

    void reset() {

        // reset the progress label text on the event dispatch thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressLabel.setText("");
              //  infoLabel.setText("");

            }
        });


        if (!isInited()) {
            return;
        }

        synchronized (playbinLock) {
            if (gstPlaybin2 != null) {
                if (gstPlaybin2.isPlaying()) {
                    gstPlaybin2.stop();
                }
                gstPlaybin2.setState(State.NULL);
                if (gstPlaybin2.getState().equals(State.NULL)) {
                    gstPlaybin2.dispose();
                }
                gstPlaybin2 = null;
            }
             gstVideoComponent = null;
            //videoComponent.setBackground(Color.BLACK);
            //videoComponent.repaint();


            //videoPanel.repaint();
        }
        
          // get rid of any existing videoProgressWorker thread
        if (videoProgressWorker != null) {
            videoProgressWorker.cancel(true);
            videoProgressWorker = null;
        }

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

    @Override
    public List<VideoFrame> captureFrames(java.io.File file, int numFrames) {

        List<VideoFrame> frames = new ArrayList<>();

        if (!isInited()) {
            return frames;
        }

        RGBDataSink.Listener listener1 = new RGBDataSink.Listener() {
            @Override
            public void rgbFrame(boolean bln, int w, int h, IntBuffer rgbPixels) {
                BufferedImage curImage = new BufferedImage(w, h,
                        BufferedImage.TYPE_INT_ARGB);
                curImage.setRGB(0, 0, w, h, rgbPixels.array(), 0, w);
                currentImage = curImage;
            }
        };

        // set up a PlayBin2 object
        RGBDataSink videoSink = new RGBDataSink("rgb", listener1);
        PlayBin2 playbin = new PlayBin2("VideoFrameCapture");
        playbin.setInputFile(file);
        playbin.setVideoSink(videoSink);

        // this is necessary to get a valid duration value
        playbin.play();
        playbin.pause();
        playbin.getState();

        // get the duration of the video
        TimeUnit unit = TimeUnit.MILLISECONDS;
        long myDurationMillis = playbin.queryDuration(unit);
        if (myDurationMillis <= 0) {
            return frames;
        }

        // create a list of timestamps at which to get frames
        int numFramesToGet = numFrames;
        long frameInterval = myDurationMillis / numFrames;
        if (frameInterval < MIN_FRAME_INTERVAL_MILLIS) {
            numFramesToGet = 1;
        }

        // for each timeStamp, grap a frame
        for (int i = 0; i < numFramesToGet; ++i) {
            long timeStamp = i * frameInterval;

            playbin.pause();
            playbin.getState();

            currentImage = null;
            if (!playbin.seek(timeStamp, unit)) {
                logger.log(Level.INFO, "There was a problem seeking to " + timeStamp + " " + unit.name().toLowerCase());
            }
            playbin.play();

            while (currentImage == null) {
                System.out.flush(); // not sure why this is needed
            }

            playbin.stop();

            frames.add(new VideoFrame(currentImage, timeStamp));
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
            .addGap(0, 146, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 36, Short.MAX_VALUE)
        );

        org.openide.awt.Mnemonics.setLocalizedText(pauseButton, org.openide.util.NbBundle.getMessage(MediaViewVideoPanel.class, "MediaViewVideoPanel.pauseButton.text")); // NOI18N
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(progressLabel, org.openide.util.NbBundle.getMessage(MediaViewVideoPanel.class, "MediaViewVideoPanel.progressLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(infoLabel, org.openide.util.NbBundle.getMessage(MediaViewVideoPanel.class, "MediaViewVideoPanel.infoLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pauseButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 329, Short.MAX_VALUE))
                    .addComponent(infoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(controlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(32, 32, 32))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pauseButton)
                            .addComponent(progressSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(progressLabel)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(infoLabel))
                    .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void pauseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButtonActionPerformed
        synchronized (playbinLock) {
            State state = gstPlaybin2.getState();
            if (state.equals(State.PLAYING)) {
                gstPlaybin2.pause();
                pauseButton.setText("►");
                gstPlaybin2.setState(State.PAUSED);
            } else if (state.equals(State.PAUSED)) {
                gstPlaybin2.play();
                pauseButton.setText("||");
                gstPlaybin2.setState(State.PLAYING);
            } else if (state.equals(State.READY)) {
                ExtractMedia em = new ExtractMedia(currentFile, getJFile(currentFile));
                em.execute();
                em.getExtractedBytes();
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

    private class VideoProgressWorker extends SwingWorker<Object, Object> {

        private String durationFormat = "%02d:%02d:%02d/%02d:%02d:%02d  ";
        private long millisElapsed = 0;
        private final long INTER_FRAME_PERIOD_MS = 20;
        private final long END_TIME_MARGIN_MS = 50;

        private boolean isPlayBinReady() {
            synchronized (playbinLock) {
                return gstPlaybin2 != null && !gstPlaybin2.getState().equals(State.NULL);
            }
        }

        private void resetVideo() {
            synchronized (playbinLock) {
                if (gstPlaybin2 != null) {
                    gstPlaybin2.stop();
                    gstPlaybin2.setState(State.READY); // ready to be played again
                    gstPlaybin2.getState(); //NEW
                }
            }
            pauseButton.setText("►");
            progressSlider.setValue(0);

            String durationStr = String.format(durationFormat, 0, 0, 0,
                    totalHours, totalMinutes, totalSeconds);
            progressLabel.setText(durationStr);
        }

        /**
         * @return true while millisElapsed is greater than END_TIME_MARGIN_MS
         * from durationMillis. This is used to indicate when the video has
         * ended because for some videos the time elapsed never becomes equal to
         * the reported duration of the video.
         */
        private boolean hasNotEnded() {
            return (durationMillis - millisElapsed) > END_TIME_MARGIN_MS;
        }

        @Override
        protected Object doInBackground() throws Exception {

            // enable the slider
            progressSlider.setEnabled(true);

            int elapsedHours = -1, elapsedMinutes = -1, elapsedSeconds = -1;
            ClockTime pos = null;
            while (hasNotEnded() && isPlayBinReady() && !isCancelled()) {

                synchronized (playbinLock) {
                    pos = gstPlaybin2.queryPosition();
                }
                millisElapsed = pos.toMillis();

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
                progressSlider.setValue((int) millisElapsed);
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
                    playMedia();
                }
            }
        }

        void playMedia() {
            if (jFile == null || !jFile.exists()) {
                progressLabel.setText("Error buffering file");
                return;
            }
            ClockTime dur = null;
            synchronized (playbinLock) {
                gstPlaybin2.play(); // must play, then pause and get state to get duration.
                gstPlaybin2.pause();
                State state = gstPlaybin2.getState();
                dur = gstPlaybin2.queryDuration();
            }
            duration = dur.toString();
            durationMillis = dur.toMillis();

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
                    progressSlider.setMaximum((int) durationMillis);
                    progressSlider.setMinimum(0);

                    synchronized (playbinLock) {
                        gstPlaybin2.play();
                    }
                    pauseButton.setText("||");
                    videoProgressWorker = new VideoProgressWorker();
                    videoProgressWorker.execute();
                }
            });
        }
    }
}
