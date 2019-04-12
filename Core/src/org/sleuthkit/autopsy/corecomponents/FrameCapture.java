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
package org.sleuthkit.autopsy.corecomponents;

import java.io.File;
import java.util.List;

/**
 * Interface used to capture frames from a video file.
 *
 * @deprecated This "extension point" is not currently supported.
 */
@Deprecated
public interface FrameCapture {

    /**
     * Captures the specified number of frames from a video file.
     *
     * @param file      The video file to use
     * @param numFrames The number of frames to capture. Note that the actual
     *                  number of frames returned may be less than this number.
     *                  Specifically, this may happen if the video is very
     *                  short.
     *
     * @throws Exception If anything goes wrong.
     * @return A list of VideoFrames representing the captured frames
     */
    List<VideoFrame> captureFrames(File file, int numFrames) throws Exception;

}
