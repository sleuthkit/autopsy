/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
public class VideoUtils {

    private static final int THUMB_COLUMNS = 3;
    private static final int THUMB_ROWS = 3;
    private static final int CV_CAP_PROP_POS_MSEC = 0;
    private static final int CV_CAP_PROP_FRAME_COUNT = 7;
    private static final int CV_CAP_PROP_FPS = 5;

    static final Logger LOGGER = Logger.getLogger(VideoUtils.class.getName());

    private VideoUtils() {
    }

    public static File getTempVideoFile(AbstractFile file) {
        return Paths.get(Case.getCurrentCase().getTempDirectory(), "videos", file.getId() + "." + file.getNameExtension()).toFile();
    }

    static BufferedImage generateVideoThumbnail(AbstractFile file, int iconSize) {
        java.io.File tempFile = getTempVideoFile(file);

        try {
            if (tempFile.exists() == false || tempFile.length() < file.getSize()) {
                com.google.common.io.Files.createParentDirs(tempFile);
                ProgressHandle progress = ProgressHandleFactory.createHandle("extracting temporary file " + file.getName());
                progress.start(100);
                try {
                    ContentUtils.writeToFile(file, tempFile, progress, null, true);
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Error buffering file", ex); //NON-NLS
                }
                progress.finish();
            }
        } catch (IOException ex) {
            return null;
        }

        VideoCapture videoFile = new VideoCapture(); // will contain the video

        if (!videoFile.open(tempFile.toString())) {
            return null;
        }
        double fps = videoFile.get(CV_CAP_PROP_FPS); // gets frame per second
        double totalFrames = videoFile.get(CV_CAP_PROP_FRAME_COUNT); // gets total frames
        if (fps <= 0 || totalFrames <= 0) {
            return null;
        }
        double milliseconds = 1000 * (totalFrames / fps); //total milliseconds

        double timestamp = Math.min(milliseconds, 500); //default time to check for is 500ms, unless the files is extremely small

        int framkeskip = Double.valueOf(Math.floor((milliseconds - timestamp) / (THUMB_COLUMNS * THUMB_ROWS))).intValue();

        Mat imageMatrix = new Mat();
        BufferedImage bufferedImage = null;

        for (int x = 0; x < THUMB_COLUMNS; x++) {
            for (int y = 0; y < THUMB_ROWS; y++) {
                if (!videoFile.set(CV_CAP_PROP_POS_MSEC, timestamp + x * framkeskip + y * framkeskip * THUMB_COLUMNS)) {
                    break;
                }
                //read the frame into the image/matrix
                if (!videoFile.read(imageMatrix)) {
                    break; //if the image for some reason is bad, return default icon
                }

                if (bufferedImage == null) {
                    bufferedImage = new BufferedImage(imageMatrix.cols() * THUMB_COLUMNS, imageMatrix.rows() * THUMB_ROWS, BufferedImage.TYPE_3BYTE_BGR);
                }

                byte[] data = new byte[imageMatrix.rows() * imageMatrix.cols() * (int) (imageMatrix.elemSize())];
                imageMatrix.get(0, 0, data); //copy the image to data

                //todo: this looks like we are swapping the first and third channels.  so we can use  BufferedImage.TYPE_3BYTE_BGR
                if (imageMatrix.channels() == 3) {
                    for (int k = 0; k < data.length; k += 3) {
                        byte temp = data[k];
                        data[k] = data[k + 2];
                        data[k + 2] = temp;
                    }
                }

                bufferedImage.getRaster().setDataElements(imageMatrix.cols() * x, imageMatrix.rows() * y, imageMatrix.cols(), imageMatrix.rows(), data);
            }
        }

        videoFile.release(); // close the file

        return ScalrWrapper.resizeFast(bufferedImage, iconSize);
    }
}
