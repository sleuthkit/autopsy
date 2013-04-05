package org.sleuthkit.autopsy.corecomponents;

import java.io.File;
import java.util.List;

/**
 * Interface used to capture frames from a video file.
 */
public interface FrameCapture {
    
    /**
     * @param file the video file to use
     * @param numFrames the number of frames to capture. Note that the actual
     * number of frames returned may be less than this number. Specifically, this
     * may happen if the video is very short.
     * @return a list of VideoFrames representing the captured frames
     */
    List<VideoFrame> captureFrames(File file, int numFrames) throws Exception;

}
