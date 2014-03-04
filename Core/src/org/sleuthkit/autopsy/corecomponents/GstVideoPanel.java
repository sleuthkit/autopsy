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

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.gstreamer.ClockTime;
import org.gstreamer.Gst;
import org.gstreamer.GstException;
import org.gstreamer.State;
import org.gstreamer.StateChangeReturn;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.elements.RGBDataSink;
import org.gstreamer.swing.VideoComponent;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;


@ServiceProviders(value = {
    @ServiceProvider(service = FrameCapture.class)
})
public class GstVideoPanel extends MediaViewVideoPanel {

    private static final String[] EXTENSIONS = new String[]{".mov", ".m4v", ".flv", ".mp4", ".3gp", ".avi", ".mpg", ".mpeg", ".wmv"};
    
    private static final Logger logger = Logger.getLogger(GstVideoPanel.class.getName());
    private boolean gstInited;
    private static final long MIN_FRAME_INTERVAL_MILLIS = 500;
    private static final long FRAME_CAPTURE_TIMEOUT_MILLIS = 1000;
    private static final String MEDIA_PLAYER_ERROR_STRING = NbBundle.getMessage(GstVideoPanel.class, "GstVideoPanel.cannotProcFile.err");
    //playback
    private long durationMillis = 0;
    private VideoProgressWorker videoProgressWorker;
    private int totalHours, totalMinutes, totalSeconds;
    private volatile PlayBin2 gstPlaybin2;
    private VideoComponent gstVideoComponent;
    private boolean autoTracking = false; // true if the slider is moving automatically
    private final Object playbinLock = new Object(); // lock for synchronization of gstPlaybin2 player
    private AbstractFile currentFile;
    private Set<String> badVideoFiles = Collections.synchronizedSet(new HashSet<String>());

    /**
     * Creates new form MediaViewVideoPanel
     */
    public GstVideoPanel() {
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

    @Override
    public boolean isInited() {
        return gstInited;
    }

    private void customizeComponents() {
        if (!initGst()) {
            return;
        }

        progressSlider.setEnabled(false); // disable slider; enable after user plays vid
        progressSlider.setValue(0);

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
                        if (gstPlaybin2.pause() == StateChangeReturn.FAILURE) {
                            logger.log(Level.WARNING, "Attempt to call PlayBin2.pause() failed.");
                            infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                            return;
                        }
                        if (gstPlaybin2.seek(ClockTime.fromMillis(time)) == false) {
                            logger.log(Level.WARNING, "Attempt to call PlayBin2.seek() failed.");
                            infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                            return;
                        }
                        gstPlaybin2.setState(orig);
                    }
                }
            }
        });
    }

    private boolean initGst() {
        try {
            logger.log(Level.INFO, "Initializing gstreamer for video/audio viewing");
            Gst.init();
            gstInited = true;
        } catch (GstException e) {
            gstInited = false;
            logger.log(Level.SEVERE, "Error initializing gstreamer for audio/video viewing and frame extraction capabilities", e);
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(this.getClass(), "GstVideoPanel.initGst.gstException.msg"),
                    e.getMessage());
            return false;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | Exception e) {
            gstInited = false;
            logger.log(Level.SEVERE, "Error initializing gstreamer for audio/video viewing and extraction capabilities", e);
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(this.getClass(), "GstVideoPanel.initGst.otherException.msg"),
                    e.getMessage());
            return false;
        }

        return true;
    }

    @Override
    void setupVideo(final AbstractFile file, final Dimension dims) {
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
            
            if (gstPlaybin2.setState(State.READY) == StateChangeReturn.FAILURE) {
                logger.log(Level.WARNING, "Attempt to call PlayBin2.setState(State.READY) failed.");
                infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
            }
        }

    }

    @Override
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

        synchronized (playbinLock) {
            if (gstPlaybin2 != null) {
                if (gstPlaybin2.isPlaying()) {
                    if (gstPlaybin2.stop() == StateChangeReturn.FAILURE) {
                        logger.log(Level.WARNING, "Attempt to call PlayBin2.stop() failed.");
                        infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                        return;
                    }
                }
                if (gstPlaybin2.setState(State.NULL) == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.setState(State.NULL) failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                if (gstPlaybin2.getState().equals(State.NULL)) {
                    gstPlaybin2.dispose();
                }
                gstPlaybin2 = null;
            }
            gstVideoComponent = null;
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
        
        Object lock = new Object();
        FrameCaptureRGBListener rgbListener = new FrameCaptureRGBListener(lock);
        
        if (!isInited()) {
            return frames;
        }
        
        // throw exception if this file is known to be problematic
        if (badVideoFiles.contains(file.getName())) {
            throw new Exception(
                    NbBundle.getMessage(this.getClass(), "GstVideoPanel.exception.problemFile.msg", file.getName()));
        }

        // set up a PlayBin2 object
        RGBDataSink videoSink = new RGBDataSink("rgb", rgbListener);
        PlayBin2 playbin = new PlayBin2("VideoFrameCapture");
        playbin.setInputFile(file);
        playbin.setVideoSink(videoSink);

        // this is necessary to get a valid duration value
        StateChangeReturn ret = playbin.play();
        if (ret == StateChangeReturn.FAILURE) {
            // add this file to the set of known bad ones
            badVideoFiles.add(file.getName());
            throw new Exception(NbBundle.getMessage(this.getClass(), "GstVideoPanel.exception.problemPlay.msg"));
        }
        ret = playbin.pause();
        if (ret == StateChangeReturn.FAILURE) {
            // add this file to the set of known bad ones
            badVideoFiles.add(file.getName());
            throw new Exception(NbBundle.getMessage(this.getClass(), "GstVideoPanel.exception.problemPause.msg"));
        }
        playbin.getState();

        // get the duration of the video
        TimeUnit unit = TimeUnit.MILLISECONDS;
        long myDurationMillis = playbin.queryDuration(unit);
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

            ret = playbin.pause();
            if (ret == StateChangeReturn.FAILURE) {
                // add this file to the set of known bad ones
                badVideoFiles.add(file.getName());
                throw new Exception(
                        NbBundle.getMessage(this.getClass(), "GstVideoPanel.exception.problemPauseCaptFrame.msg"));
            }
            playbin.getState();

            //System.out.println("Seeking to " + timeStamp + "milliseconds.");
            if (!playbin.seek(timeStamp, unit)) {
                logger.log(Level.INFO, "There was a problem seeking to " + timeStamp + " " + unit.name().toLowerCase());
            }
            
            ret = playbin.play();
            if (ret == StateChangeReturn.FAILURE) {
                // add this file to the set of known bad ones
                badVideoFiles.add(file.getName());
                throw new Exception(
                        NbBundle.getMessage(this.getClass(), "GstVideoPanel.exception.problemPlayCaptFrame.msg"));
            }

            // wait for FrameCaptureRGBListener to finish
            synchronized(lock) {
                try {
                    lock.wait(FRAME_CAPTURE_TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    logger.log(Level.INFO, "InterruptedException occurred while waiting for frame capture.", e);
                }
            }
            Image image = rgbListener.getImage();

            ret = playbin.stop();
            if (ret == StateChangeReturn.FAILURE) {
                // add this file to the set of known bad ones
                badVideoFiles.add(file.getName());
                throw new Exception(
                        NbBundle.getMessage(this.getClass(), "GstVideoPanel.exception.problemStopCaptFrame.msg"));
            }
            
            if (image == null) {
                logger.log(Level.WARNING, "There was a problem while trying to capture a frame from file " + file.getName());
                badVideoFiles.add(file.getName());
                break;
            }

            frames.add(new VideoFrame(image, timeStamp));
        }

        return frames;
    }
    
    private class FrameCaptureRGBListener implements RGBDataSink.Listener {

        public FrameCaptureRGBListener(Object waiter) {
            this.waiter = waiter;
        }
        
        private BufferedImage bi;
        private final Object waiter;

        @Override
        public void rgbFrame(boolean bln, int w, int h, IntBuffer rgbPixels) {
            synchronized (waiter) {
                bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                bi.setRGB(0, 0, w, h, rgbPixels.array(), 0, w);
                waiter.notify();
            }
        }

        public Image getImage() {
            synchronized (waiter) {
                Image image = bi;
                bi = null;
                return image;
            }
        }
        
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

        org.openide.awt.Mnemonics.setLocalizedText(pauseButton, org.openide.util.NbBundle.getMessage(GstVideoPanel.class, "MediaViewVideoPanel.pauseButton.text")); // NOI18N
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(progressLabel, org.openide.util.NbBundle.getMessage(GstVideoPanel.class, "MediaViewVideoPanel.progressLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(infoLabel, org.openide.util.NbBundle.getMessage(GstVideoPanel.class, "MediaViewVideoPanel.infoLabel.text")); // NOI18N

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
            State state = gstPlaybin2.getState();
            if (state.equals(State.PLAYING)) {
                if (gstPlaybin2.pause() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.pause() failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                pauseButton.setText("►");
                // Is this call necessary considering we just called gstPlaybin2.pause()?
                if (gstPlaybin2.setState(State.PAUSED) == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.setState(State.PAUSED) failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
            } else if (state.equals(State.PAUSED)) {
                if (gstPlaybin2.play() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.play() failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                pauseButton.setText("||");
                // Is this call necessary considering we just called gstPlaybin2.play()?
                if (gstPlaybin2.setState(State.PLAYING) == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.setState(State.PLAYING) failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
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
        private boolean hadError = false;

        private boolean isPlayBinReady() {
            synchronized (playbinLock) {
                return gstPlaybin2 != null && !gstPlaybin2.getState().equals(State.NULL);
            }
        }

        private void resetVideo() throws Exception {
            synchronized (playbinLock) {
                if (gstPlaybin2 != null) {
                    if (gstPlaybin2.stop() == StateChangeReturn.FAILURE) {
                        logger.log(Level.WARNING, "Attempt to call PlayBin2.stop() failed.");
                        infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    }
                    // ready to be played again
                    if (gstPlaybin2.setState(State.READY) == StateChangeReturn.FAILURE) {
                        logger.log(Level.WARNING, "Attempt to call PlayBin2.setState(State.READY) failed.");
                        infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    }
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
        
        
        @Override
        protected void done() {
            // see if any exceptions were thrown
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Error updating video progress: " + ex.getMessage());
                infoLabel.setText("Error updating video progress: " + ex.getMessage());
            }
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
            progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progress.buffering"));
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
                progressLabel.setText(NbBundle.getMessage(this.getClass(), "GstVideoPanel.progressLabel.bufferingErr"));
                return;
            }
            ClockTime dur = null;
            synchronized (playbinLock) {
                // must play, then pause and get state to get duration.
                if (gstPlaybin2.play() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.play() failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                if (gstPlaybin2.pause() == StateChangeReturn.FAILURE) {
                    logger.log(Level.WARNING, "Attempt to call PlayBin2.pause() failed.");
                    infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                    return;
                }
                gstPlaybin2.getState();
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
                        if (gstPlaybin2.play() == StateChangeReturn.FAILURE) {
                            logger.log(Level.WARNING, "Attempt to call PlayBin2.play() failed.");
                            infoLabel.setText(MEDIA_PLAYER_ERROR_STRING);
                        }
                    }
                    pauseButton.setText("||");
                    videoProgressWorker = new VideoProgressWorker();
                    videoProgressWorker.execute();
                }
            });
        }
    }
    
    @Override
    public String[] getExtensions() {
        return EXTENSIONS;
    }
}
