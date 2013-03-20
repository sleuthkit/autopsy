/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import com.sun.javafx.application.PlatformImpl;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.BoxLayout;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.gstreamer.*;
import org.gstreamer.elements.PlayBin2;
import org.gstreamer.elements.RGBDataSink;
import org.gstreamer.swing.VideoComponent;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * Media content viewer for videos, sounds and images. Using gstreamer.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = DataContentViewer.class, position = 5),
    @ServiceProvider(service = FrameCapture.class)
})
public class DataContentViewerMedia extends javax.swing.JPanel implements DataContentViewer, FrameCapture {

    private String[] IMAGES; // use javafx supported 
    private static final String[] VIDEOS = new String[]{".mov", ".m4v", ".flv", ".mp4", ".3gp", ".avi", ".mpg", ".mpeg"};
    private static final String[] AUDIOS = new String[]{".mp3", ".wav", ".wma"};
    private static final int NUM_FRAMES = 12;
    private static final long MIN_FRAME_INTERVAL_MILLIS = 500;
    private static final Logger logger = Logger.getLogger(DataContentViewerMedia.class.getName());
    private VideoComponent videoComponent;
    private PlayBin2 playbin2;
    private AbstractFile currentFile;
    private long durationMillis = 0;
    private boolean autoTracking = false; // true if the slider is moving automatically
    private final Object playbinLock = new Object(); // lock for synchronization of playbin2 player
    private VideoProgressWorker videoProgressWorker;
    private int totalHours, totalMinutes, totalSeconds;
    private BufferedImage currentImage = null;
    private boolean gstInited = false;
    private AbstractFile lastFile;
    private boolean inImageMode; //keeps track if already in image mode to minimize UI setup

    /**
     * Creates new form DataContentViewerVideo
     */
    public DataContentViewerMedia() {
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        inImageMode = false;

        Platform.setImplicitExit(false);
        PlatformImpl.startup(new Runnable() {
            @Override
            public void run() {
                logger.log(Level.INFO, "Initializing JavaFX for image viewing");
            }
        });
        logger.log(Level.INFO, "Supported image formats by javafx image viewer: ");

        //initialize supported image types
        //TODO use mime-types instead once we have support
        String[] fxSupportedImagesSuffixes = ImageIO.getReaderFileSuffixes();
        IMAGES = new String[fxSupportedImagesSuffixes.length];
        for (int i = 0; i < fxSupportedImagesSuffixes.length; ++i) {
            String suffix = fxSupportedImagesSuffixes[i];
            logger.log(Level.INFO, "suffix: " + suffix);
            IMAGES[i] = "." + suffix;
        }

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
            return;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | Exception e) {
            gstInited = false;
            logger.log(Level.SEVERE, "Error initializing gstreamer for audio/video viewing and extraction capabilities", e);
            MessageNotifyUtil.Notify.error("Error initializing gstreamer for audio/video viewing frame extraction capabilities. "
                    + " Video and audio viewing will be disabled. ",
                    e.getMessage());
            return;
        }


        progressSlider.setEnabled(false); // disable slider; enable after user plays vid
        progressSlider.addChangeListener(new ChangeListener() {
            /**
             * Should always try to synchronize any call to
             * progressSlider.setValue() to avoid a different thread changing
             * playbin while stateChanged() is processing
             */
            @Override
            public void stateChanged(ChangeEvent e) {
                int time = progressSlider.getValue();
                synchronized (playbinLock) {
                    if (playbin2 != null && !autoTracking) {
                        State orig = playbin2.getState();
                        playbin2.pause();
                        playbin2.seek(ClockTime.fromMillis(time));
                        playbin2.setState(orig);
                    }
                }
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pauseButton = new javax.swing.JButton();
        videoPanel = new javax.swing.JPanel();
        progressSlider = new javax.swing.JSlider();
        progressLabel = new javax.swing.JLabel();

        pauseButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerMedia.class, "DataContentViewerMedia.pauseButton.text")); // NOI18N
        pauseButton.setMaximumSize(new java.awt.Dimension(45, 23));
        pauseButton.setMinimumSize(new java.awt.Dimension(45, 23));
        pauseButton.setPreferredSize(new java.awt.Dimension(45, 23));
        pauseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout videoPanelLayout = new javax.swing.GroupLayout(videoPanel);
        videoPanel.setLayout(videoPanelLayout);
        videoPanelLayout.setHorizontalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        videoPanelLayout.setVerticalGroup(
            videoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 242, Short.MAX_VALUE)
        );

        progressSlider.setValue(0);

        progressLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        progressLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerMedia.class, "DataContentViewerMedia.progressLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pauseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(videoPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(pauseButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(progressSlider, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(progressLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void pauseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseButtonActionPerformed
        synchronized (playbinLock) {
            State state = playbin2.getState();
            if (state.equals(State.PLAYING)) {
                playbin2.pause();
                pauseButton.setText("►");
                playbin2.setState(State.PAUSED);
            } else if (state.equals(State.PAUSED)) {
                playbin2.play();
                pauseButton.setText("||");
                playbin2.setState(State.PLAYING);
            } else if (state.equals(State.READY)) {
                ExtractMedia em = new ExtractMedia(currentFile, getJFile(currentFile));
                em.execute();
                em.getExtractedBytes();
            }
        }
    }//GEN-LAST:event_pauseButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton pauseButton;
    private javax.swing.JLabel progressLabel;
    private javax.swing.JSlider progressSlider;
    private javax.swing.JPanel videoPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void setNode(Node selectedNode) {

        if (selectedNode == null) {
            return;
        }

        AbstractFile file = selectedNode.getLookup().lookup(AbstractFile.class);
        if (file == null) {
            return;
        }

        if (file.equals(lastFile)) {
            return; //prevent from loading twice if setNode() called mult. times
        } else {
            lastFile = file;
        }

        reset();
        setComponentsVisibility(false);

        // get rid of any existing videoProgressWorker thread
        if (videoProgressWorker != null) {
            videoProgressWorker.cancel(true);
            videoProgressWorker = null;
        }


        currentFile = file;
        if (containsExt(file.getName(), IMAGES)) {
            showImageFx(file);
        } else if (gstInited
                && (containsExt(file.getName(), VIDEOS) || containsExt(file.getName(), AUDIOS))) {
            inImageMode = false;
            setupVideo(file);
        }
    }

    /**
     * Initialize vars and display the image on the panel.
     *
     * @param file
     */
    private void showImageFx(final AbstractFile file) {
        final String fileName = file.getName();

        // load the image
        PlatformImpl.runLater(new Runnable() {
            @Override
            public void run() {
                Dimension dims = DataContentViewerMedia.this.getSize();

                final InputStream inputStream = new ReadContentInputStream(file);

                final Image fxImage;
                try {
                    //original input stream
                    BufferedImage bi = ImageIO.read(inputStream);
                    //scale image using Scalr
                    BufferedImage biScaled = ScalrWrapper.resizeHighQuality(bi, (int) dims.getWidth(), (int) dims.getHeight());
                    //convert from awt imageto fx image
                    fxImage = SwingFXUtils.toFXImage(biScaled, null);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not load image file into media view: " + fileName, ex);
                    return;
                } catch (OutOfMemoryError ex) {
                    logger.log(Level.WARNING, "Could not load image file into media view (too large): " + fileName, ex);
                    MessageNotifyUtil.Notify.warn("Could not load image file (too large): " + file.getName(), ex.getMessage());
                    return;
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Could not close input stream after loading image in media view: " + fileName, ex);
                    }
                }

                if (fxImage == null) {
                    logger.log(Level.WARNING, "Could not load image file into media view: " + fileName);
                    return;
                }

                // simple displays ImageView the image as is
                ImageView fxImageView = new ImageView();
                fxImageView.setImage(fxImage);
                // resizes the image to have width of 100 while preserving the ratio and using
                // higher quality filtering method; this ImageView is also cached to
                // improve performance
                fxImageView.setPreserveRatio(true);
                fxImageView.setSmooth(true);
                fxImageView.setCache(true);
                fxImageView.setFitWidth(dims.getWidth());
                fxImageView.setFitHeight(dims.getHeight());

                Group fxRoot = new Group();

                //Scene fxScene = new Scene(fxRoot, dims.getWidth(), dims.getHeight(), javafx.scene.paint.Color.BLACK);
                Scene fxScene = new Scene(fxRoot, javafx.scene.paint.Color.BLACK);
                fxRoot.getChildren().add(fxImageView);

                if (inImageMode) {
                    final JFXPanel fxPanel = (JFXPanel) videoPanel.getComponent(0);
                    fxPanel.setScene(fxScene);
                    videoPanel.setVisible(true);
                } else {
                    final JFXPanel fxPanel = new JFXPanel();
                    fxPanel.setScene(fxScene);
                    

                    //when done, join with the swing panel
                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            inImageMode = true;
                            //remove video panels and recreate image view panel
                            //TODO use swing layered pane to switch between different modes
                            videoPanel.removeAll();
                            videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
                            videoPanel.add(fxPanel);
                            videoPanel.setVisible(true);

                            if (fxImage.isError()) {
                                logger.log(Level.WARNING, "Could not load image file into media view: " + fileName);
                                //MessageNotifyUtil.Message.warn("Could not load image file: " +  file.getName());
                            }
                        }
                    });
                }
            }
        });



    }

    /**
     * Initialize vars and display the image on the panel.
     *
     * @param file
     * @deprecated using javafx for image display
     */
    @Deprecated
    private void showImageGst(AbstractFile file) {
        if (!gstInited) {
            return;
        }
        java.io.File ioFile = getJFile(file);
        if (!ioFile.exists()) {
            try {
                ContentUtils.writeToFile(file, ioFile);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error buffering file", ex);
            }
        }

        videoComponent = new VideoComponent();
        synchronized (playbinLock) {
            if (playbin2 != null) {
                playbin2.dispose();
            }
            playbin2 = new PlayBin2("ImageViewer");
            playbin2.setVideoSink(videoComponent.getElement());
        }

        videoPanel.removeAll();
        videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
        videoPanel.add(videoComponent);
        videoPanel.revalidate();
        videoPanel.repaint();

        synchronized (playbinLock) {
            playbin2.setInputFile(ioFile);
            playbin2.play();
        }
        videoPanel.setVisible(true);
    }

    /**
     * Initialize all the necessary vars to play a video/audio file.
     *
     * @param file the file to play
     */
    private void setupVideo(AbstractFile file) {
        java.io.File ioFile = getJFile(file);

        pauseButton.setText("►");
        progressSlider.setValue(0);

        videoComponent = new VideoComponent();
        synchronized (playbinLock) {
            if (playbin2 != null) {
                playbin2.dispose();
            }
            playbin2 = new PlayBin2("VideoPlayer");
            playbin2.setVideoSink(videoComponent.getElement());
        }

        videoPanel.removeAll();
        videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
        videoPanel.add(videoComponent);
        videoPanel.revalidate();
        videoPanel.repaint();

        synchronized (playbinLock) {
            playbin2.setInputFile(ioFile);
            playbin2.setState(State.READY);
        }
        setComponentsVisibility(true);
    }

    /**
     * To set the visibility of specific components in this class.
     *
     * @param isVisible whether to show or hide the specific components
     */
    private void setComponentsVisibility(boolean isVisible) {
        pauseButton.setVisible(isVisible);
        progressLabel.setVisible(isVisible);
        progressSlider.setVisible(isVisible);
        videoPanel.setVisible(isVisible);
    }

    @Override
    public String getTitle() {
        return "Media View";
    }

    @Override
    public String getToolTip() {
        return "Displays supported multimedia files";
    }

    @Override
    public DataContentViewer getInstance() {
        return new DataContentViewerMedia();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        // we don't want this to do anything
        // because we already reset on each selected node
    }

    private void reset() {
        // reset the progress label text on the event dispatch thread
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressLabel.setText("");
            }
        });


        if (!gstInited) {
            return;
        }

        synchronized (playbinLock) {
            if (playbin2 != null) {
                if (playbin2.isPlaying()) {
                    playbin2.stop();
                }
                playbin2.setState(State.NULL);
                if (playbin2.getState().equals(State.NULL)) {
                    playbin2.dispose();
                }
                playbin2 = null;
            }
            videoComponent = null;
        }
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        if (file == null) {
            return false;
        }

        //try displaying deleted files if we can read them
        //if (file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
        //  return false;
        //}

        if (file.getSize() == 0) {
            return false;
        }

        String name = file.getName().toLowerCase();

        boolean deleted = file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC);

        if (containsExt(name, IMAGES)) {
            return true;
        } //for gstreamer formats, check if initialized first, then
        //support audio formats, and video formats if undeleted file
        else if (gstInited
                && (containsExt(name, AUDIOS)
                || (!deleted && containsExt(name, VIDEOS)))) {
            return true;
        }

        return false;
    }

    @Override
    public int isPreferred(Node node, boolean isSupported) {
        if (isSupported) {
            return 7;
        } else {
            return 0;
        }
    }

    private static boolean containsExt(String name, String[] exts) {
        int extStart = name.lastIndexOf(".");
        String ext = "";
        if (extStart != -1) {
            ext = name.substring(extStart, name.length()).toLowerCase();
        }
        return Arrays.asList(exts).contains(ext);
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

        if (!gstInited) {
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
                    play();
                }
            }
        }

        private void play() {
            if (jFile == null || !jFile.exists()) {
                progressLabel.setText("Error buffering file");
                return;
            }
            ClockTime dur = null;
            synchronized (playbinLock) {
                playbin2.play(); // must play, then pause and get state to get duration.
                playbin2.pause();
                playbin2.getState();
                dur = playbin2.queryDuration();
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
                        playbin2.play();
                    }
                    pauseButton.setText("||");
                    videoProgressWorker = new VideoProgressWorker();
                    videoProgressWorker.execute();
                }
            });
        }
    }

    private class VideoProgressWorker extends SwingWorker<Object, Object> {

        private String durationFormat = "%02d:%02d:%02d/%02d:%02d:%02d  ";
        private long millisElapsed = 0;
        private final long INTER_FRAME_PERIOD_MS = 20;
        private final long END_TIME_MARGIN_MS = 50;

        private boolean isPlayBinReady() {
            synchronized (playbinLock) {
                return playbin2 != null && !playbin2.getState().equals(State.NULL);
            }
        }

        public void resetVideo() {
            synchronized (playbinLock) {
                if (playbin2 != null) {
                    playbin2.stop();
                    playbin2.setState(State.READY); // ready to be played again
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
                    pos = playbin2.queryPosition();
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
    }
}
