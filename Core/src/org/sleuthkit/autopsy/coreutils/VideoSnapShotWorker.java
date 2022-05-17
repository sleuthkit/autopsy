/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.SwingWorker;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import static org.sleuthkit.autopsy.coreutils.VideoUtils.isVideoThumbnailSupported;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A SwingWorker that will create a video of screen captures.
 * 
 * To have the the gui update with process add a PropertyChangeListener for
 * the property 'progress'.
 * 
 * To update the gui on completion subclass this class and implement 
 * the SwingWorker done method.
 * 
 */
public class VideoSnapShotWorker extends SwingWorker<Void, Void>{
    private static final long MIN_FRAME_INTERVAL_MILLIS = 500;
    
    private static final int DEFAULT_FRAMES_PER_SECOND = 1;
    private static final int DEFAULT_TOTAL_FRAMES = 100;
    private static final int DEFAULT_SCALE = 100;
    
    private final Path inputPath;
    private final Path outputPath;
    private final long numFrames;
    private final double scale;
    private final int framesPerSecond;
    
    /**
     * Creates a new instance of the SwingWorker using the default parameters.
     * 
     * @param inputPath The path to the existing video. 
     * @param outputPath The output path of the snapshot video.
     */
    public VideoSnapShotWorker(Path inputPath, Path outputPath) {
        this(inputPath, outputPath, DEFAULT_TOTAL_FRAMES, DEFAULT_SCALE, DEFAULT_FRAMES_PER_SECOND);
    }
    
    /**
     * Creates a new instance of the SwingWorker.
     * 
     * @param inputPath The path to the existing video. 
     * @param outputPath The output path of the snapshot video.
     * @param numFrames The number of screen captures to include in the video.
     * @param scale % to scale from the original. Passing 0 or 100 will result in no change.
     * @param framesPerSecond Effects how long each frame will appear in the video.
     */
    public VideoSnapShotWorker(Path inputPath, Path outputPath, long numFrames, double scale, int framesPerSecond) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.scale = scale;
        this.numFrames = numFrames;
        this.framesPerSecond = framesPerSecond;
    }
    
    static boolean isSupported(AbstractFile file) {
        return isVideoThumbnailSupported(file);
    }

    @Override
    protected Void doInBackground() throws Exception {
        File input = inputPath.toFile();
        if (!input.exists() || !input.isFile() || !input.canRead()) {
            throw new IOException(String.format("Unable to read input file %s", input.toString()));
        }

        File outputFile = outputPath.toFile();
        outputFile.mkdirs();

        String file_name = inputPath.toString();//OpenCV API requires string for file name
        VideoCapture videoCapture = new VideoCapture(file_name); //VV will contain the videos

        if (!videoCapture.isOpened()) //checks if file is not open
        {
            // add this file to the set of known bad ones
            throw new Exception("Problem with video file; problem when attempting to open file.");
        }
        VideoWriter writer = null;
        try {
            // get the duration of the video
            double fps = framesPerSecond == 0 ? videoCapture.get(Videoio.CAP_PROP_FPS) : framesPerSecond; // gets frame per second
            double total_frames = videoCapture.get(7); // gets total frames
            double milliseconds = 1000 * (total_frames / fps); //convert to ms duration
            long myDurationMillis = (long) milliseconds;

            if (myDurationMillis <= 0) {
                throw new Exception(String.format("Failed to make snapshot video, original video has no duration. %s", inputPath.toAbsolutePath()));
            }

            // calculate the number of frames to capture
            int numFramesToGet = (int) numFrames;
            long frameInterval = myDurationMillis / numFrames;

            if (frameInterval < MIN_FRAME_INTERVAL_MILLIS) {
                numFramesToGet = 1;
            }

            // for each timeStamp, grab a frame
            Mat mat = new Mat(); // holds image received from video in mat format (opencv format)

            Size s = new Size((int) videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH), (int) videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT));
            Size newSize = (scale == 0 || scale == 100) ? s : new Size((int) (videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH) * scale), (int) (videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT) * scale));

            writer = new VideoWriter(outputFile.toString(), VideoWriter.fourcc('M', 'J', 'P', 'G'), 1, newSize, true);

            if (!writer.isOpened()) {
                throw new Exception(String.format("Problem with video file; problem when attempting to open output file. %s", outputFile.toString()));
            }

            for (int frame = 0; frame < numFramesToGet; ++frame) {
                
                if(isCancelled()) {
                    break;
                }
                
                long timeStamp = frame * frameInterval;
                videoCapture.set(0, timeStamp); //set video in timeStamp ms

                if (!videoCapture.read(mat)) { // on Wav files, usually the last frame we try to read does not succeed.
                    continue;
                }

                Mat resized = new Mat();
                Imgproc.resize(mat, resized, newSize, 0, 0, Imgproc.INTER_CUBIC);
                writer.write(resized);
                
                setProgress(numFramesToGet/frame);
            }

        } finally {
            videoCapture.release();
            if (writer != null) {
                writer.release();
            }
        }
        
        if(isCancelled()) {
            if(outputFile.exists()) {
                outputFile.delete();
            }
        }
        
        return null;
    }
    
}
