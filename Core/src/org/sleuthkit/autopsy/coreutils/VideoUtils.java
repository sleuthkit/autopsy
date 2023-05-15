/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
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

    private static final String FFMPEG = "ffmpeg";
    private static final String FFMPEG_EXE = "ffmpeg.exe";

    private static final List<String> SUPPORTED_VIDEO_EXTENSIONS
            = Arrays.asList("mov", "m4v", "flv", "mp4", "3gp", "avi", "mpg", //NON-NLS
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

    private static final int CV_CAP_PROP_POS_MSEC = 0;
    private static final int CV_CAP_PROP_FRAME_COUNT = 7;
    private static final int CV_CAP_PROP_FPS = 5;

    private static final double[] FRAME_GRAB_POS_RATIO = {0.50, 0.25, 0.75, 0.01};

    static final Logger LOGGER = Logger.getLogger(VideoUtils.class.getName());

    private VideoUtils() {
    }

    /**
     * Gets a File object in the temp directory of the current case for the
     * given AbstractFile object.
     *
     * @param file The AbstractFile object
     *
     * @return The File object
     *
     * @throws NoCurrentCaseException If no case is opened.
     */
    public static File getVideoFileInTempDir(AbstractFile file) throws NoCurrentCaseException {
        return Paths.get(Case.getCurrentCaseThrows().getTempDirectory(), "videos", file.getId() + "." + file.getNameExtension()).toFile(); //NON-NLS
    }

    public static boolean isVideoThumbnailSupported(AbstractFile file) {
        return isMediaThumbnailSupported(file, "video/", SUPPORTED_VIDEO_MIME_TYPES, SUPPORTED_VIDEO_EXTENSIONS);
    }

    /**
     * Generate a thumbnail for the supplied video.
     *
     * @param file     The video file.
     * @param iconSize The target icon size in pixels.
     *
     * @return The generated thumbnail. Can return null if an error occurred
     *         trying to generate the thumbnail, or if the current thread was
     *         interrupted.
     */
    @NbBundle.Messages({"# {0} - file name",
        "VideoUtils.genVideoThumb.progress.text=extracting temporary file {0}"})
    static BufferedImage generateVideoThumbnail(AbstractFile file, int iconSize) {
        java.io.File tempFile;
        try {
            tempFile = getVideoFileInTempDir(file);
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
            if (Thread.interrupted()) {
                return null;
            }

            double duration = 1000 * (totalFrames / fps); //total milliseconds

            /*
             * Four attempts are made to grab a frame from a video. The first
             * attempt at 50% will give us a nice frame in the middle that gets
             * to the heart of the content. If that fails, the next positions
             * tried will be 25% and 75%. After three failed attempts, 1% will
             * be tried in a last-ditch effort, the idea being the video may be
             * corrupt and that our best chance at retrieving a frame is early
             * on in the video.
             *
             * If no frame can be retrieved, no thumbnail will be created.
             */
            int[] framePositions = new int[]{
                (int) (duration * FRAME_GRAB_POS_RATIO[0]),
                (int) (duration * FRAME_GRAB_POS_RATIO[1]),
                (int) (duration * FRAME_GRAB_POS_RATIO[2]),
                (int) (duration * FRAME_GRAB_POS_RATIO[3]),};

            Mat imageMatrix = new Mat();

            for (int i = 0; i < framePositions.length; i++) {
                if (!videoFile.set(CV_CAP_PROP_POS_MSEC, framePositions[i])) {
                    LOGGER.log(Level.WARNING, "Error seeking to " + framePositions[i] + "ms in {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                    // If we can't set the time, continue to the next frame position and try again.
                    continue;
                }
                // Read the frame into the image/matrix.
                if (!videoFile.read(imageMatrix)) {
                    LOGGER.log(Level.WARNING, "Error reading frame at " + framePositions[i] + "ms from {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                    // If the image is bad for some reason, continue to the next frame position and try again.
                    continue;
                }

                break;
            }

            // If the image is empty, return since no buffered image can be created.
            if (imageMatrix.empty()) {
                return null;
            }

            int matrixColumns = imageMatrix.cols();
            int matrixRows = imageMatrix.rows();

            // Convert the matrix that contains the frame to a buffered image.
            if (bufferedImage == null) {
                bufferedImage = new BufferedImage(matrixColumns, matrixRows, BufferedImage.TYPE_3BYTE_BGR);
            }

            byte[] data = new byte[matrixRows * matrixColumns * (int) (imageMatrix.elemSize())];
            imageMatrix.get(0, 0, data); //copy the image to data

            //todo: this looks like we are swapping the first and third channels.  so we can use  BufferedImage.TYPE_3BYTE_BGR
            if (imageMatrix.channels() == 3) {
                for (int k = 0; k < data.length; k += 3) {
                    byte temp = data[k];
                    data[k] = data[k + 2];
                    data[k + 2] = temp;
                }
            }

            bufferedImage.getRaster().setDataElements(0, 0, matrixColumns, matrixRows, data);
        } finally {
            videoFile.release(); // close the file}
        }
        if (Thread.interrupted()) {
            return null;
        }
        return bufferedImage == null ? null : ScalrWrapper.resizeFast(bufferedImage, iconSize);
    }

    public static boolean canCompressAndScale(AbstractFile file) {

        if (PlatformUtil.getOSName().toLowerCase().startsWith("windows")) {
            return isVideoThumbnailSupported(file);
        }

        return false;
    }

    /**
     * Compress the given the files. This method takes advantage of the exiting
     * getVideoFile method to create a temp copy of the inputFile. It does not
     * delete the temp file, it leaves it in the video temp file for future use.
     *
     * When using this method there is no way to cancel the process between the
     * creation of the temp file and the launching of the process. For better
     * control use the other compressVideo method.
     *
     * @param inputFile    The AbstractFile representing the video.
     * @param outputFile   Output file.
     * @param terminator   A processTerminator for the ffmpeg executable.
     * @param logFileDirectory A file to send the output of ffmpeg stdout.
     *
     * @return The ffmpeg process exit value.
     *
     * @throws IOException
     */
    static public int compressVideo(AbstractFile inputFile, File outputFile, ExecUtil.ProcessTerminator terminator, File logFileDirectory) throws IOException {
        return compressVideo(getVideoFile(inputFile), outputFile, terminator, logFileDirectory);
    }

    /**
     * Compress the given the files.
     *
     * The output from ffmpeg seem to go to the error file not the out file.
     * Text in the err file does not mean an issue occurred.
     *
     * @param inputFile    Absolute path to input video.
     * @param outputFile   Path for scaled file.
     * @param terminator   A processTerminator for the ffmpeg executable.
     * @param logFileDirectory Location to put the output of ffmpeg
     *
     * @return The ffmpeg process exit value.
     *
     * @throws IOException
     */
    static public int compressVideo(File inputFile, File outputFile, ExecUtil.ProcessTerminator terminator, File logFileDirectory) throws IOException {
        Path executablePath = Paths.get(FFMPEG, FFMPEG_EXE);
        File exeFile = InstalledFileLocator.getDefault().locate(executablePath.toString(), VideoUtils.class.getPackage().getName(), true);
        if (exeFile == null) {
            throw new IOException("Unable to compress ffmpeg.exe was not found.");
        }

        if (!exeFile.canExecute()) {
            throw new IOException("Unable to compress ffmpeg.exe could not be execute");
        }

        if (outputFile.exists()) {
            throw new IOException(String.format("Failed to compress %s, output file already exists %s", inputFile.toString(), outputFile.toString()));
        }

        File ffmpegStderr = null;
        File ffmpegStdout = null;
        
        // In case the folder doesn't exist.
        if(logFileDirectory != null) {
            logFileDirectory.mkdirs();
            ffmpegStderr = File.createTempFile("ffmpegstderr", ".txt", logFileDirectory);
            ffmpegStdout = File.createTempFile("ffmpegstdout", ".txt", logFileDirectory);
        } else {
            throw new IOException("Invalid output file location null");
        }

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + exeFile.getAbsolutePath() + "\"",
                "-i", "\"" + inputFile.toString() + "\"",
                "-vcodec", "libx264",
                "-crf", "28",
                "\"" + outputFile.toString() + "\"");
 
        processBuilder.redirectError(ffmpegStderr);
        processBuilder.redirectOutput(ffmpegStdout);

        if (terminator == null) {
            return ExecUtil.execute(processBuilder);
        }

        return ExecUtil.execute(processBuilder, terminator);
    }

    /**
     * Returns a File object representing a temporary copy of the video file
     * representing by the AbstractFile object.
     *
     * @param file
     *
     * @return
     */
    static File getVideoFile(AbstractFile file) {
        java.io.File tempFile;

        try {
            tempFile = getVideoFileInTempDir(file);
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

        return tempFile;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force aLeapp to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        return processBuilder;
    }

    // Defaults for the screen capture methods.
    private static final long MIN_FRAME_INTERVAL_MILLIS = 500;
    private static final int DEFAULT_FRAMES_PER_SECOND = 1;
    private static final int DEFAULT_TOTAL_FRAMES = 100;
    private static final int DEFAULT_SCALE = 100;

    /**
     * Creates an output video containing a series of screen captures from the
     * input video. The output file will be an avi with a unique name based on
     * the input file.
     *
     * @param inputFile    File representing the input video.
     * @param outDirectory The directory for the output file.
     *
     * @return The newly created file.
     *
     * @throws IOException
     */
    static public File createScreenCaptureVideo(File inputFile, File outDirectory) throws IOException {
        return createScreenCaptureVideo(inputFile, outDirectory, DEFAULT_TOTAL_FRAMES, DEFAULT_SCALE, DEFAULT_FRAMES_PER_SECOND);
    }

    /**
     * Creates a video containing a series of screen captures from the input
     * video.
     *
     * @param inputFile       File representing the input video.
     * @param outDirectory    The directory for the output file.
     * @param numFrames       Total number of screen captures to included in the
     *                        video.
     * @param scale           Percentage to scale the screen captures. The value
     *                        of 0 or 100 will cause no change.
     * @param framesPerSecond The number of frames to show in the video each
     *                        second The lower this value the longer the image
     *                        will appear on the screen.
     *
     * @return The newly created file.
     *
     * @throws IOException
     */
    static public File createScreenCaptureVideo(File inputFile, File outDirectory, long numFrames, double scale, int framesPerSecond) throws IOException {

        if (!outDirectory.exists()) {
            outDirectory.mkdirs();
        } else if (!outDirectory.isDirectory()) {
            throw new IOException(String.format("The passed in outDir is not a directory", outDirectory.getName()));
        }

        if (!inputFile.exists() || !inputFile.canRead()) {
            throw new IOException(String.format("Failed to compress %s, input file cannot be read.", inputFile.getName()));
        }

        String file_name = inputFile.toString();//OpenCV API requires string for file name
        VideoCapture videoCapture = new VideoCapture(file_name); //VV will contain the videos

        if (!videoCapture.isOpened()) //checks if file is not open
        {
            // add this file to the set of known bad ones
            throw new IOException("Problem with video file; problem when attempting to open file.");
        }
        VideoWriter writer = null;
        try {
            // get the duration of the video
            double fps = framesPerSecond == 0 ? videoCapture.get(Videoio.CAP_PROP_FPS) : framesPerSecond; // gets frame per second
            double total_frames = videoCapture.get(7); // gets total frames
            double milliseconds = 1000 * (total_frames / fps); //convert to ms duration
            long myDurationMillis = (long) milliseconds;

            if (myDurationMillis <= 0) {
                throw new IOException(String.format("Failed to make snapshot video, original video has no duration. %s", inputFile.toString()));
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

            File outputFile = createEmptyOutputFile(inputFile, outDirectory, ".avi");
            writer = new VideoWriter(outputFile.toString(), VideoWriter.fourcc('M', 'J', 'P', 'G'), 1, newSize, true);

            if (!writer.isOpened()) {
                outputFile.delete();
                throw new IOException(String.format("Problem with video file; problem when attempting to open output file. %s", outputFile.toString()));
            }

            for (int frame = 0; frame < numFramesToGet; ++frame) {
                long timeStamp = frame * frameInterval;
                videoCapture.set(0, timeStamp); //set video in timeStamp ms

                if (!videoCapture.read(mat)) { // on Wav files, usually the last frame we try to read does not succeed.
                    continue;
                }

                Mat resized = new Mat();
                Imgproc.resize(mat, resized, newSize, 0, 0, Imgproc.INTER_CUBIC);
                writer.write(resized);
            }

            return outputFile;

        } finally {
            videoCapture.release();
            if (writer != null) {
                writer.release();
            }
        }
    }

    /**
     * Generates an empty at the given outputDirectory location with a unique
     * name based on the inputFile name with the given extension.
     *
     * @param inputFile       InputFile to base new file name on.
     * @param outputDirectory The directory the new file should be created in.
     * @param extension       The extension the new file should have.
     *
     * @return The file.
     *
     * @throws IOException
     */
    private static File createEmptyOutputFile(File inputFile, File outputDirectory, String extension) throws IOException {
        Path path = Paths.get(inputFile.getAbsolutePath());
        String fileName = path.getFileName().toString();
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return File.createTempFile(fileName, extension, outputDirectory);
    }

    /**
     * Gets a File object in the temp directory of the current case for the
     * given AbstractFile object.
     *
     * @param file The AbstractFile object
     *
     * @return The File object
     *
     * @deprecated Call getVideoFileInTempDir instead.
     */
    @Deprecated
    public static File getTempVideoFile(AbstractFile file) {
        try {
            return getVideoFileInTempDir(file);
        } catch (NoCurrentCaseException ex) {
            // Mimic the old behavior.
            throw new IllegalStateException(ex);
        }
    }

}
