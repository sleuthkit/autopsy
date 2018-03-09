/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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

import com.google.common.io.Files;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import static org.sleuthkit.autopsy.coreutils.ImageUtils.isMediaThumbnailSupported;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
public class VideoUtils {

    private static final List<String> SUPPORTED_VIDEO_EXTENSIONS =
            Arrays.asList("mov", "m4v", "flv", "mp4", "3gp", "avi", "mpg", //NON-NLS
                    "mpeg", "asf", "divx", "rm", "moov", "wmv", "vob", "dat", //NON-NLS
                    "m1v", "m2v", "m4v", "mkv", "mpe", "yop", "vqa", "xmv", //NON-NLS
                    "mve", "wtv", "webm", "vivo", "vc1", "seq", "thp", "san", //NON-NLS
                    "mjpg", "smk", "vmd", "sol", "cpk", "sdp", "sbg", "rtsp", //NON-NLS
                    "rpl", "rl2", "r3d", "mlp", "mjpeg", "hevc", "h265", "265", //NON-NLS
                    "h264", "h263", "h261", "drc", "avs", "pva", "pmp", "ogg", //NON-NLS
                    "nut", "nuv", "nsv", "mxf", "mtv", "mvi", "mxg", "lxf", //NON-NLS
                    "lvf", "ivf", "mve", "cin", "hnm", "gxf", "fli", "flc", //NON-NLS
                    "flx", "ffm", "wve", "uv2", "dxa", "dv", "cdxl", "cdg", //NON-NLS
                    "bfi", "jv", "bik", "vid", "vb", "son", "avs", "paf", "mm", //NON-NLS
                    "flm", "tmv", "4xm");  //NON-NLS

    private static final SortedSet<String> SUPPORTED_VIDEO_MIME_TYPES = new TreeSet<>(
            Arrays.asList("application/x-shockwave-flash",
                    "video/x-m4v",
                    "video/x-flv",
                    "video/quicktime",
                    "video/avi",
                    "video/msvideo",
                    "video/x-msvideo", //NON-NLS
                    "video/mp4",
                    "video/x-ms-wmv",
                    "video/mpeg",
                    "video/asf")); //NON-NLS

    public static List<String> getSupportedVideoExtensions() {
        return SUPPORTED_VIDEO_EXTENSIONS;
    }

    public static SortedSet<String> getSupportedVideoMimeTypes() {
        return Collections.unmodifiableSortedSet(SUPPORTED_VIDEO_MIME_TYPES);
    }

    private static final int THUMB_COLUMNS = 3;
    private static final int THUMB_ROWS = 3;
    private static final int CV_CAP_PROP_POS_MSEC = 0;
    private static final int CV_CAP_PROP_FRAME_COUNT = 7;
    private static final int CV_CAP_PROP_FPS = 5;

    static final Logger LOGGER = Logger.getLogger(VideoUtils.class.getName());

    private VideoUtils() {
    }

    public static File getTempVideoFile(AbstractFile file) throws NoCurrentCaseException {
        return Paths.get(Case.getOpenCase().getTempDirectory(), "videos", file.getId() + "." + file.getNameExtension()).toFile(); //NON-NLS
    }

    public static boolean isVideoThumbnailSupported(AbstractFile file) {
        return isMediaThumbnailSupported(file, "video/", SUPPORTED_VIDEO_MIME_TYPES, SUPPORTED_VIDEO_EXTENSIONS);
    }

    @NbBundle.Messages({"# {0} - file name",
        "VideoUtils.genVideoThumb.progress.text=extracting temporary file {0}"})
    static BufferedImage generateVideoThumbnail(AbstractFile file, int iconSize) {
        java.io.File tempFile;
        try {
            tempFile = getTempVideoFile(file);
        } catch (NoCurrentCaseException ex) {
            LOGGER.log(Level.WARNING, "Exception while getting open case.", ex); //NON-NLS
            return null;
        }
        if (tempFile.exists() == false || tempFile.length() < file.getSize()) {
            ProgressHandle progress = ProgressHandle.createHandle(Bundle.VideoUtils_genVideoThumb_progress_text(file.getName()));
            progress.start(100);
            try {
                Files.createParentDirs(tempFile);
                if (Thread.interrupted()) {
                    return null;
                }
                ContentUtils.writeToFile(file, tempFile, progress, null, true);
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Error extracting temporary file for " + ImageUtils.getContentPathSafe(file), ex); //NON-NLS
            } finally {
                progress.finish();
            }
        }
        VideoCapture videoFile = new VideoCapture(); // will contain the video
        BufferedImage bufferedImage = null;

        try {

            if (!videoFile.open(tempFile.toString())) {
                LOGGER.log(Level.WARNING, "Error opening {0} for preview generation.", ImageUtils.getContentPathSafe(file)); //NON-NLS
                return null;
            }
            double fps = videoFile.get(CV_CAP_PROP_FPS); // gets frame per second
            double totalFrames = videoFile.get(CV_CAP_PROP_FRAME_COUNT); // gets total frames
            if (fps <= 0 || totalFrames <= 0) {
                LOGGER.log(Level.WARNING, "Error getting fps or total frames for {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                return null;
            }
            double milliseconds = 1000 * (totalFrames / fps); //total milliseconds

            double timestamp = Math.min(milliseconds, 500); //default time to check for is 500ms, unless the files is extremely small

            int framkeskip = Double.valueOf(Math.floor((milliseconds - timestamp) / (THUMB_COLUMNS * THUMB_ROWS))).intValue();

            Mat imageMatrix = new Mat();

            for (int x = 0; x < THUMB_COLUMNS; x++) {
                for (int y = 0; y < THUMB_ROWS; y++) {
                    if (Thread.interrupted()) {
                        return null;
                    }
                    if (!videoFile.set(CV_CAP_PROP_POS_MSEC, timestamp + x * framkeskip + y * framkeskip * THUMB_COLUMNS)) {
                        LOGGER.log(Level.WARNING, "Error seeking to " + timestamp + "ms in {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                        break; // if we can't set the time, return black for that frame
                    }
                    //read the frame into the image/matrix
                    if (!videoFile.read(imageMatrix)) {
                        LOGGER.log(Level.WARNING, "Error reading frames at " + timestamp + "ms from {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                        break; //if the image for some reason is bad, return black for that frame
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
        } finally {
            videoFile.release(); // close the file}
        }
        if (Thread.interrupted()) {
            return null;
        }
        return bufferedImage == null ? null : ScalrWrapper.resizeFast(bufferedImage, iconSize);
    }
}
