 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for creating and manipulating thumbnail and icon images.
 *
 */
public class ImageUtils {

    private static final Logger LOGGER = Logger.getLogger(ImageUtils.class.getName());

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;

    private static final Logger logger = LOGGER;
    private static final BufferedImage DEFAULT_ICON;
    private static final List<String> SUPP_IMAGE_EXTENSIONS = new ArrayList<>(Arrays.asList(ImageIO.getReaderFileSuffixes())); //final
    private static final List<String> SUPP_IMAGE_MIME_TYPES = new ArrayList<>(Arrays.asList(ImageIO.getReaderMIMETypes())); // final
    private static final List<String> SUPP_VIDEO_EXTENSIONS
            = Arrays.asList("mov", "m4v", "flv", "mp4", "3gp", "avi", "mpg",
                    "mpeg", "asf", "divx", "rm", "moov", "wmv", "vob", "dat",
                    "m1v", "m2v", "m4v", "mkv", "mpe", "yop", "vqa", "xmv",
                    "mve", "wtv", "webm", "vivo", "vc1", "seq", "thp", "san",
                    "mjpg", "smk", "vmd", "sol", "cpk", "sdp", "sbg", "rtsp",
                    "rpl", "rl2", "r3d", "mlp", "mjpeg", "hevc", "h265", "265",
                    "h264", "h263", "h261", "drc", "avs", "pva", "pmp", "ogg",
                    "nut", "nuv", "nsv", "mxf", "mtv", "mvi", "mxg", "lxf",
                    "lvf", "ivf", "mve", "cin", "hnm", "gxf", "fli", "flc",
                    "flx", "ffm", "wve", "uv2", "dxa", "dv", "cdxl", "cdg",
                    "bfi", "jv", "bik", "vid", "vb", "son", "avs", "paf", "mm",
                    "flm", "tmv", "4xm");  //NON-NLS
    private static final List<String> SUPP_VIDEO_MIME_TYPES
            = Arrays.asList("video/x-m4v", "video/quicktime", "video/avi", "video/msvideo", "video/x-msvideo",
                    "video/mp4", "video/x-ms-wmv", "video/mpeg", "video/asf"); //NON-NLS
    private static final boolean openCVLoaded;

    static {
        BufferedImage tempImage;
        try {
            tempImage = ImageIO.read(ImageUtils.class.getResourceAsStream("/org/sleuthkit/autopsy/images/file-icon.png"));//NON-NLS
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load default icon.", ex);
            tempImage = null;
        }
        DEFAULT_ICON = tempImage;

        SUPP_IMAGE_MIME_TYPES.add("image/x-ms-bmp");

        //load opencv libraries
        boolean openCVLoadedTemp;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            if (System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64")) {
                System.loadLibrary("opencv_ffmpeg248_64");
            } else {
                System.loadLibrary("opencv_ffmpeg248");
            }

            openCVLoadedTemp = true;
        } catch (UnsatisfiedLinkError e) {
            openCVLoadedTemp = false;
            LOGGER.log(Level.SEVERE, "OpenCV Native code library failed to load", e);
            //TODO: show warning bubble

        }

        openCVLoaded = openCVLoadedTemp;
    }

    /**
     * Get the default Icon, which is the icon for a file.
     *
     * @return
     */
    public static Image getDefaultIcon() {
        return DEFAULT_ICON;
    }

    /**
     * Can a thumbnail be generated for the content?
     *
     * @param content
     *
     * @return
     */
    public static boolean thumbnailSupported(Content content) {
        if (content instanceof AbstractFile == false) {
            return false;
        }
        AbstractFile f = (AbstractFile) content;
        if (f.getSize() == 0) {
            return false;
        }

        // check the blackboard for a file type attribute
        try {
            //TODO: Use FileTypeDetector here?
            ArrayList<BlackboardAttribute> attributes = f.getGenInfoAttributes(ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                if (SUPP_IMAGE_MIME_TYPES.contains(attribute.getValueString()) || SUPP_VIDEO_MIME_TYPES.contains(attribute.getValueString())) {
                    return true;
                }
            }

            // if the file type is known and we don't support it, bail
            if (attributes.size() > 0) {
                return false;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error while getting file signature from blackboard.", ex); //NON-NLS
        }

        // if we have an extension, check it
        final String extension = f.getNameExtension();
        if (extension.isEmpty() == false) {
            // Note: thumbnail generator only supports JPG, GIF, and PNG for now
            if (SUPP_IMAGE_EXTENSIONS.contains(extension) || SUPP_VIDEO_EXTENSIONS.contains(extension)) {
                return true;
            }
        }

        // if no extension or one that is not for an image, then read the content
        return isJpegFileHeader(f) || isPngFileHeader(f);
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is
     * not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return
     */
    public static Image getIcon(Content content, int iconSize) {
        //TODO: why do we allow Content here if we only handle AbstractFiles?

        Image icon = null;
        // If a thumbnail file is already saved locally
        // @@@ Bug here in that we do not refer to size in the cache. 

        File iconFile = getCachedThumbnailLocation(content.getId());
        // If a thumbnail file is already saved locally
        if (iconFile.exists()) {
            try {
                BufferedImage bicon = ImageIO.read(iconFile);
                if (bicon == null) {
                    icon = DEFAULT_ICON;
                } else if (bicon.getWidth() != iconSize) {
                    icon = generateAndSaveIcon(content, iconSize, iconFile);
                } else {
                    icon = bicon;
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Error while reading image.", ex); //NON-NLS
                icon = DEFAULT_ICON;
            }
        } else {
            final String extension = ((AbstractFile) content).getNameExtension();

            if (SUPP_VIDEO_EXTENSIONS.contains(extension) || SUPP_IMAGE_EXTENSIONS.contains(extension)) {
                icon = generateAndSaveIcon(content, iconSize, iconFile);
            }
        }
        if (icon == null) {
            return DEFAULT_ICON;
        }
        return icon;
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is
     * not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return File object for cached image. Is guaranteed to exist.
     */
    public static File getIconFile(Content content, int iconSize) {
        if (getIcon(content, iconSize) != null) {
            return getFile(content.getId());
        }
        return null;
    }

    /**
     * Get a file object for where the cached icon should exist. The returned
     * file may not exist.
     *
     * @param id
     *
     * @return
     *
     */
    public static File getFile(long id) {
        return getCachedThumbnailLocation(id);
    }

    /**
     * Get a file object for where the cached icon should exist. The returned
     * file may not exist.
     *
     * @param fileID
     *
     * @return
     *
     */
    private static File getCachedThumbnailLocation(long fileID) {
        return Paths.get(Case.getCurrentCase().getCacheDirectory(), "thumbnails", fileID + ".png").toFile();
    }

    /**
     * Check if is jpeg file based on header
     *
     * @param file
     *
     * @return true if jpeg file, false otherwise
     */
    public static boolean isJpegFileHeader(AbstractFile file) {
        if (file.getSize() < 100) {
            return false;
        }

        byte[] fileHeaderBuffer = new byte[2];
        int bytesRead;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, 2);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a JPEG
            return false;
        }
        if (bytesRead != 2) {
            return false;
        }
        /*
         * Check for the JPEG header. Since Java bytes are signed, we cast them
         * to an int first.
         */
        return (((fileHeaderBuffer[0] & 0xff) == 0xff) && ((fileHeaderBuffer[1] & 0xff) == 0xd8));
    }

    public static boolean isPngFileHeader(AbstractFile file) {
        if (file.getSize() < 10) {
            return false;
        }

        byte[] fileHeaderBuffer = new byte[8];
        int bytesRead;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, 8);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not an image
            return false;
        }
        if (bytesRead != 8) {
            return false;
        }
        /*
         * Check for the header. Since Java bytes are signed, we cast them
         * to an int first.
         */
        return (((fileHeaderBuffer[1] & 0xff) == 0x50) && ((fileHeaderBuffer[2] & 0xff) == 0x4E)
                && ((fileHeaderBuffer[3] & 0xff) == 0x47) && ((fileHeaderBuffer[4] & 0xff) == 0x0D)
                && ((fileHeaderBuffer[5] & 0xff) == 0x0A) && ((fileHeaderBuffer[6] & 0xff) == 0x1A)
                && ((fileHeaderBuffer[7] & 0xff) == 0x0A));
    }
    private final static int THUMB_COLUMNS = 3;
    private final static int THUMB_ROWS = 3;

    private static BufferedImage generateVideoThumbnail(AbstractFile file, int iconSize) {

        final String extension = file.getNameExtension();

        java.io.File tempFile = Paths.get(Case.getCurrentCase().getTempDirectory(), "videos", file.getId() + "." + extension).toFile();

        try {
            if (tempFile.exists() == false || tempFile.length() < file.getSize()) {
                copyFileUsingStream(file, tempFile);
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

    private static final int CV_CAP_PROP_POS_MSEC = 0;
    private static final int CV_CAP_PROP_FRAME_COUNT = 7;
    private static final int CV_CAP_PROP_FPS = 5;

    /**
     * Generate an icon and save it to specified location.
     *
     * @param content  File to generate icon for
     * @param iconSize
     * @param saveFile Location to save thumbnail to
     *
     * @return Generated icon or null on error
     */
    private static Image generateAndSaveIcon(Content content, int iconSize, File saveFile) {
        AbstractFile f = (AbstractFile) content;
        final String extension = f.getNameExtension();
        BufferedImage icon = null;
        try {
            if (SUPP_VIDEO_EXTENSIONS.contains(extension)) {
                if (openCVLoaded) {
                    icon = generateVideoThumbnail((AbstractFile) content, iconSize);
                } else {
                    return DEFAULT_ICON;
                }
            } else if (SUPP_IMAGE_EXTENSIONS.contains(extension)) {
                icon = generateImageThumbnail(content, iconSize);
            }

            if (icon == null) {
                return DEFAULT_ICON;

            } else {
                if (saveFile.exists()) {
                    saveFile.delete();
                }
                Files.createParentDirs(saveFile);
                ImageIO.write(icon, "png", saveFile); //NON-NLS
            }
        } catch (NullPointerException | IOException ex) {
            logger.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex); //NON-NLS
        }
        return icon;
    }

    /*
     * Generate and return a scaled image
     */
    private static BufferedImage generateImageThumbnail(Content content, int iconSize) {

        InputStream inputStream = null;
        BufferedImage bi = null;
        try {
            inputStream = new ReadContentInputStream(content);
            bi = ImageIO.read(inputStream);
            if (bi == null) {
                logger.log(Level.WARNING, "No image reader for file: " + content.getName()); //NON-NLS
                return null;
            }
            return ScalrWrapper.resizeFast(bi, iconSize);
         
        } catch (IllegalArgumentException e) {
            // if resizing does not work due to extremely small height/width ratio,
            // crop the image instead.
            BufferedImage biCropped = ScalrWrapper.cropImage(bi, Math.min(iconSize, bi.getWidth()), Math.min(iconSize, bi.getHeight()));
            return biCropped;
        } catch (OutOfMemoryError e) {
            logger.log(Level.WARNING, "Could not scale image (too large): " + content.getName(), e); //NON-NLS
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not scale image: " + content.getName(), e); //NON-NLS
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close input stream after resizing thumbnail: " + content.getName(), ex); //NON-NLS
                }
            }
        }
    }

    /**
     * copy the first 500kb to a temporary file
     *
     * @param file
     * @param tempFile
     *
     * @throws IOException
     */
    public static void copyFileUsingStream(Content file, java.io.File tempFile) throws IOException {
        com.google.common.io.Files.createParentDirs(tempFile);

        ProgressHandle progress = ProgressHandleFactory.createHandle("extracting temporary file " + file.getName());
        progress.start();
        progress.switchToDeterminate(100);
        try {
            ContentUtils.writeToFile(file, tempFile, progress, null, true);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error buffering file", ex); //NON-NLS
        }
        progress.finish();
    }
}
