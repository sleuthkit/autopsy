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

import java.io.File;
import java.util.List;

/**
 * Interface used to capture frames from a video file.
 */
 interface FrameCapture {
    
    /**
     * @param file the video file to use
     * @param numFrames the number of frames to capture. Note that the actual
     * number of frames returned may be less than this number. Specifically, this
     * may happen if the video is very short.
     * @return a list of VideoFrames representing the captured frames
     */
    List<VideoFrame> captureFrames(File file, int numFrames) throws Exception;

}
