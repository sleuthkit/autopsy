/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import com.google.common.io.Files;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.imgscalr.Scalr;
import org.netbeans.api.progress.ProgressHandle;
import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import static org.sleuthkit.autopsy.coreutils.VideoUtils.getVideoFileInTempDir;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.discovery.search.ResultFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility class for the various user interface elements used by Discovery.
 */
final class DiscoveryUiUtils {

    private final static Logger logger = Logger.getLogger(DiscoveryUiUtils.class.getName());
    private static final int BYTE_UNIT_CONVERSION = 1000;
    private static final int ICON_SIZE = 16;
    private static final String RED_CIRCLE_ICON_PATH = "org/sleuthkit/autopsy/images/red-circle-exclamation.png";
    private static final String YELLOW_CIRCLE_ICON_PATH = "org/sleuthkit/autopsy/images/yellow-circle-yield.png";
    private static final String DELETE_ICON_PATH = "org/sleuthkit/autopsy/images/file-icon-deleted.png";
    private static final String UNSUPPORTED_DOC_PATH = "org/sleuthkit/autopsy/images/image-extraction-not-supported.png";
    private static final ImageIcon INTERESTING_SCORE_ICON = new ImageIcon(ImageUtilities.loadImage(YELLOW_CIRCLE_ICON_PATH, false));
    private static final ImageIcon NOTABLE_SCORE_ICON = new ImageIcon(ImageUtilities.loadImage(RED_CIRCLE_ICON_PATH, false));
    private static final ImageIcon DELETED_ICON = new ImageIcon(ImageUtilities.loadImage(DELETE_ICON_PATH, false));
    private static final ImageIcon UNSUPPORTED_DOCUMENT_THUMBNAIL = new ImageIcon(ImageUtilities.loadImage(UNSUPPORTED_DOC_PATH, false));
    private static final String THUMBNAIL_FORMAT = "png"; //NON-NLS
    private static final String VIDEO_THUMBNAIL_DIR = "video-thumbnails"; //NON-NLS
    private static final BufferedImage VIDEO_DEFAULT_IMAGE = getDefaultVideoThumbnail();

    @NbBundle.Messages({"# {0} - fileSize",
        "# {1} - units",
        "DiscoveryUiUtility.sizeLabel.text=Size: {0} {1}",
        "DiscoveryUiUtility.bytes.text=bytes",
        "DiscoveryUiUtility.kiloBytes.text=KB",
        "DiscoveryUiUtility.megaBytes.text=MB",
        "DiscoveryUiUtility.gigaBytes.text=GB",
        "DiscoveryUiUtility.terraBytes.text=TB"})
    /**
     * Convert a size in bytes to a string with representing the size in the
     * largest units which represent the value as being greater than or equal to
     * one. Result will be rounded down to the nearest whole number of those
     * units.
     *
     * @param bytes Size in bytes.
     */
    static String getFileSizeString(long bytes) {
        long size = bytes;
        int unitsSwitchValue = 0;
        while (size > BYTE_UNIT_CONVERSION && unitsSwitchValue < 4) {
            size /= BYTE_UNIT_CONVERSION;
            unitsSwitchValue++;
        }
        String units;
        switch (unitsSwitchValue) {
            case 1:
                units = Bundle.DiscoveryUiUtility_kiloBytes_text();
                break;
            case 2:
                units = Bundle.DiscoveryUiUtility_megaBytes_text();
                break;
            case 3:
                units = Bundle.DiscoveryUiUtility_gigaBytes_text();
                break;
            case 4:
                units = Bundle.DiscoveryUiUtility_terraBytes_text();
                break;
            default:
                units = Bundle.DiscoveryUiUtility_bytes_text();
                break;
        }
        return Bundle.DiscoveryUiUtility_sizeLabel_text(size, units);
    }

    /**
     * Get the image to use when the document type does not support image
     * extraction.
     *
     * @return An image that indicates we don't know if there are images.
     */
    static ImageIcon getUnsupportedImageThumbnail() {
        return UNSUPPORTED_DOCUMENT_THUMBNAIL;
    }

    /**
     * Get the names of the sets which exist in the case database for the
     * specified artifact and attribute types.
     *
     * @param artifactType     The artifact type to get the list of sets for.
     * @param setNameAttribute The attribute type which contains the set names.
     *
     * @return A list of set names which exist in the case for the specified
     *         artifact and attribute types.
     *
     * @throws TskCoreException
     */
    static List<String> getSetNames(BlackboardArtifact.ARTIFACT_TYPE artifactType, BlackboardAttribute.ATTRIBUTE_TYPE setNameAttribute) throws TskCoreException {
        List<BlackboardArtifact> arts = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(artifactType);
        List<String> setNames = new ArrayList<>();
        for (BlackboardArtifact art : arts) {
            for (BlackboardAttribute attr : art.getAttributes()) {
                if (attr.getAttributeType().getTypeID() == setNameAttribute.getTypeID()) {
                    String setName = attr.getValueString();
                    if (!setNames.contains(setName)) {
                        setNames.add(setName);
                    }
                }
            }
        }
        Collections.sort(setNames);
        return setNames;
    }

    /**
     * Helper method to see if point is on the icon.
     *
     * @param comp  The component to check if the cursor is over the icon of
     * @param point The point the cursor is at.
     *
     * @return True if the point is over the icon, false otherwise.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    static boolean isPointOnIcon(Component comp, Point point) {
        return comp instanceof JComponent && point.x >= comp.getX() && point.x <= comp.getX() + ICON_SIZE && point.y >= comp.getY() && point.y <= comp.getY() + ICON_SIZE;
    }

    /**
     * Method to set the icon and tool tip text for a label to show deleted
     * status.
     *
     * @param isDeleted      True if the label should reflect deleted status,
     *                       false otherwise.
     * @param isDeletedLabel The label to set the icon and tooltip for.
     */
    @NbBundle.Messages({"DiscoveryUiUtils.isDeleted.text=All instances of file are deleted."})
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    static void setDeletedIcon(boolean isDeleted, javax.swing.JLabel isDeletedLabel) {
        if (isDeleted) {
            isDeletedLabel.setIcon(DELETED_ICON);
            isDeletedLabel.setToolTipText(Bundle.DiscoveryUiUtils_isDeleted_text());
        } else {
            isDeletedLabel.setIcon(null);
            isDeletedLabel.setToolTipText(null);
        }
    }

    /**
     * Method to set the icon and tool tip text for a label to show the score.
     *
     * @param resultFile The result file which the label should reflect the
     *                   score of.
     * @param scoreLabel The label to set the icon and tooltip for.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    static void setScoreIcon(ResultFile resultFile, javax.swing.JLabel scoreLabel) {
        ImageIcon icon = null;
        
        Score score = resultFile.getScore();
        if (score != null && score.getSignificance() != null) {
            switch (score.getSignificance()) {
                case NOTABLE:
                    icon = NOTABLE_SCORE_ICON;
                    break;
                case LIKELY_NOTABLE: 
                    icon = INTERESTING_SCORE_ICON;
                    break;
                case LIKELY_NONE:
                case NONE:
                case UNKNOWN:
                default:
                    icon = null;
                    break;
            }    
        }
        
        scoreLabel.setIcon(icon);
        scoreLabel.setToolTipText(resultFile.getScoreDescription());
    }
    
    
    /**
     * Get the size of the icons used by the UI.
     *
     * @return
     */
    static int getIconSize() {
        return ICON_SIZE;
    }

    /**
     * Helper method to display an error message when the results of the
     * Discovery Top component may be incomplete.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @NbBundle.Messages({"DiscoveryUiUtils.resultsIncomplete.text=Discovery results may be incomplete"})
    static void displayErrorMessage(DiscoveryDialog dialog) {
        //check if modules run and assemble message
        try {
            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
            Map<Long, DataSourceModulesWrapper> dataSourceIngestModules = new HashMap<>();
            for (DataSource dataSource : skCase.getDataSources()) {
                dataSourceIngestModules.put(dataSource.getId(), new DataSourceModulesWrapper(dataSource.getName()));
            }

            for (IngestJobInfo jobInfo : skCase.getIngestJobs()) {
                dataSourceIngestModules.get(jobInfo.getObjectId()).updateModulesRun(jobInfo);
            }
            String message = "";
            for (DataSourceModulesWrapper dsmodulesWrapper : dataSourceIngestModules.values()) {
                message += dsmodulesWrapper.getMessage();
            }
            if (!message.isEmpty()) {
                JScrollPane messageScrollPane = new JScrollPane();
                JTextPane messageTextPane = new JTextPane();
                messageTextPane.setText(message);
                messageTextPane.setVisible(true);
                messageTextPane.setEditable(false);
                messageTextPane.setCaretPosition(0);
                messageScrollPane.setMaximumSize(new Dimension(600, 100));
                messageScrollPane.setPreferredSize(new Dimension(600, 100));
                messageScrollPane.setViewportView(messageTextPane);
                JOptionPane.showMessageDialog(dialog, messageScrollPane, Bundle.DiscoveryUiUtils_resultsIncomplete_text(), JOptionPane.PLAIN_MESSAGE);
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Exception while determining which modules have been run for Discovery", ex);
        }
        dialog.validateDialog();
    }

    /**
     * Get the video thumbnails for a file which exists in a
     * VideoThumbnailsWrapper and update the VideoThumbnailsWrapper to include
     * them.
     *
     * @param thumbnailWrapper the object which contains the file to generate
     *                         thumbnails for.
     *
     */
    @NbBundle.Messages({"# {0} - file name",
        "DiscoveryUiUtils.genVideoThumb.progress.text=extracting temporary file {0}"})
    static void getVideoThumbnails(VideoThumbnailsWrapper thumbnailWrapper) {
        AbstractFile file = thumbnailWrapper.getResultFile().getFirstInstance();
        String cacheDirectory;
        try {
            cacheDirectory = Case.getCurrentCaseThrows().getCacheDirectory();
        } catch (NoCurrentCaseException ex) {
            cacheDirectory = null;
            logger.log(Level.WARNING, "Unable to get cache directory, video thumbnails will not be saved", ex);
        }
        if (cacheDirectory == null || file.getMd5Hash() == null || !Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash()).toFile().exists()) {
            java.io.File tempFile;
            try {
                tempFile = getVideoFileInTempDir(file);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Exception while getting open case.", ex); //NON-NLS
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                thumbnailWrapper.setThumbnails(createDefaultThumbnailList(VIDEO_DEFAULT_IMAGE), framePositions);
                return;
            }
            if (tempFile.exists() == false || tempFile.length() < file.getSize()) {
                ProgressHandle progress = ProgressHandle.createHandle(Bundle.DiscoveryUiUtils_genVideoThumb_progress_text(file.getName()));
                progress.start(100);
                try {
                    Files.createParentDirs(tempFile);
                    if (Thread.interrupted()) {
                        int[] framePositions = new int[]{
                            0,
                            0,
                            0,
                            0};
                        thumbnailWrapper.setThumbnails(createDefaultThumbnailList(VIDEO_DEFAULT_IMAGE), framePositions);
                        return;
                    }
                    ContentUtils.writeToFile(file, tempFile, progress, null, true);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error extracting temporary file for " + file.getParentPath() + "/" + file.getName(), ex); //NON-NLS
                } finally {
                    progress.finish();
                }
            }
            VideoCapture videoFile = new VideoCapture(); // will contain the video
            BufferedImage bufferedImage = null;

            try {
                if (!videoFile.open(tempFile.toString())) {
                    logger.log(Level.WARNING, "Error opening {0} for preview generation.", file.getParentPath() + "/" + file.getName()); //NON-NLS
                    int[] framePositions = new int[]{
                        0,
                        0,
                        0,
                        0};
                    thumbnailWrapper.setThumbnails(createDefaultThumbnailList(VIDEO_DEFAULT_IMAGE), framePositions);
                    return;
                }
                double fps = videoFile.get(5); // gets frame per second
                double totalFrames = videoFile.get(7); // gets total frames
                if (fps <= 0 || totalFrames <= 0) {
                    logger.log(Level.WARNING, "Error getting fps or total frames for {0}", file.getParentPath() + "/" + file.getName()); //NON-NLS
                    int[] framePositions = new int[]{
                        0,
                        0,
                        0,
                        0};
                    thumbnailWrapper.setThumbnails(createDefaultThumbnailList(VIDEO_DEFAULT_IMAGE), framePositions);
                    return;
                }
                if (Thread.interrupted()) {
                    int[] framePositions = new int[]{
                        0,
                        0,
                        0,
                        0};
                    thumbnailWrapper.setThumbnails(createDefaultThumbnailList(VIDEO_DEFAULT_IMAGE), framePositions);
                    return;
                }

                double duration = 1000 * (totalFrames / fps); //total milliseconds

                int[] framePositions = new int[]{
                    (int) (duration * .01),
                    (int) (duration * .25),
                    (int) (duration * .5),
                    (int) (duration * .75),};

                Mat imageMatrix = new Mat();
                List<Image> videoThumbnails = new ArrayList<>();
                if (cacheDirectory == null || file.getMd5Hash() == null) {
                    cacheDirectory = null;
                } else {
                    try {
                        FileUtils.forceMkdir(Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash()).toFile());
                    } catch (IOException ex) {
                        cacheDirectory = null;
                        logger.log(Level.WARNING, "Unable to make video thumbnails directory, thumbnails will not be saved", ex);
                    }
                }
                for (int i = 0; i < framePositions.length; i++) {
                    if (!videoFile.set(0, framePositions[i])) {
                        logger.log(Level.WARNING, "Error seeking to " + framePositions[i] + "ms in {0}", file.getParentPath() + "/" + file.getName()); //NON-NLS
                        // If we can't set the time, continue to the next frame position and try again.

                        videoThumbnails.add(VIDEO_DEFAULT_IMAGE);
                        if (cacheDirectory != null) {
                            try {
                                ImageIO.write(VIDEO_DEFAULT_IMAGE, THUMBNAIL_FORMAT,
                                        Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash(), i + "-" + framePositions[i] + "." + THUMBNAIL_FORMAT).toFile()); //NON-NLS)
                            } catch (IOException ex) {
                                logger.log(Level.WARNING, "Unable to save default video thumbnail for " + file.getMd5Hash() + " at frame position " + framePositions[i], ex);
                            }
                        }
                        continue;
                    }
                    // Read the frame into the image/matrix.
                    if (!videoFile.read(imageMatrix)) {
                        logger.log(Level.WARNING, "Error reading frame at " + framePositions[i] + "ms from {0}", file.getParentPath() + "/" + file.getName()); //NON-NLS
                        // If the image is bad for some reason, continue to the next frame position and try again.
                        videoThumbnails.add(VIDEO_DEFAULT_IMAGE);
                        if (cacheDirectory != null) {
                            try {
                                ImageIO.write(VIDEO_DEFAULT_IMAGE, THUMBNAIL_FORMAT,
                                        Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash(), i + "-" + framePositions[i] + "." + THUMBNAIL_FORMAT).toFile()); //NON-NLS)
                            } catch (IOException ex) {
                                logger.log(Level.WARNING, "Unable to save default video thumbnail for " + file.getMd5Hash() + " at frame position " + framePositions[i], ex);
                            }
                        }

                        continue;
                    }
                    // If the image is empty, return since no buffered image can be created.
                    if (imageMatrix.empty()) {
                        videoThumbnails.add(VIDEO_DEFAULT_IMAGE);
                        if (cacheDirectory != null) {
                            try {
                                ImageIO.write(VIDEO_DEFAULT_IMAGE, THUMBNAIL_FORMAT,
                                        Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash(), i + "-" + framePositions[i] + "." + THUMBNAIL_FORMAT).toFile()); //NON-NLS)
                            } catch (IOException ex) {
                                logger.log(Level.WARNING, "Unable to save default video thumbnail for " + file.getMd5Hash() + " at frame position " + framePositions[i], ex);
                            }
                        }
                        continue;
                    }

                    int matrixColumns = imageMatrix.cols();
                    int matrixRows = imageMatrix.rows();

                    // Convert the matrix that contains the frame to a buffered image.
                    if (bufferedImage == null) {
                        bufferedImage = new BufferedImage(matrixColumns, matrixRows, BufferedImage.TYPE_3BYTE_BGR);
                    }

                    byte[] data = new byte[matrixRows * matrixColumns * (int) (imageMatrix.elemSize())];
                    imageMatrix.get(0, 0, data); //copy the image to data

                    if (imageMatrix.channels() == 3) {
                        for (int k = 0; k < data.length; k += 3) {
                            byte temp = data[k];
                            data[k] = data[k + 2];
                            data[k + 2] = temp;
                        }
                    }

                    bufferedImage.getRaster().setDataElements(0, 0, matrixColumns, matrixRows, data);
                    if (Thread.interrupted()) {
                        thumbnailWrapper.setThumbnails(videoThumbnails, framePositions);
                        try {
                            FileUtils.forceDelete(Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash()).toFile());
                        } catch (IOException ex) {
                            logger.log(Level.WARNING, "Unable to delete directory for cancelled video thumbnail process", ex);
                        }
                        return;
                    }
                    BufferedImage thumbnail = ScalrWrapper.resize(bufferedImage, Scalr.Method.SPEED, Scalr.Mode.FIT_TO_HEIGHT, ImageUtils.ICON_SIZE_LARGE, ImageUtils.ICON_SIZE_MEDIUM, Scalr.OP_ANTIALIAS);
                    //We are height limited here so it can be wider than it can be tall.Scalr maintains the aspect ratio.
                    videoThumbnails.add(thumbnail);
                    if (cacheDirectory != null) {
                        try {
                            ImageIO.write(thumbnail, THUMBNAIL_FORMAT,
                                    Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, file.getMd5Hash(), i + "-" + framePositions[i] + "." + THUMBNAIL_FORMAT).toFile()); //NON-NLS)
                        } catch (IOException ex) {
                            logger.log(Level.WARNING, "Unable to save video thumbnail for " + file.getMd5Hash() + " at frame position " + framePositions[i], ex);
                        }
                    }
                }
                thumbnailWrapper.setThumbnails(videoThumbnails, framePositions);
            } finally {
                videoFile.release(); // close the file}
            }
        } else {
            loadSavedThumbnails(cacheDirectory, thumbnailWrapper, VIDEO_DEFAULT_IMAGE);
        }
    }

    /**
     * Get the default image to display when a thumbnail is not available.
     *
     * @return The default video thumbnail.
     */
    private static BufferedImage getDefaultVideoThumbnail() {
        try {
            return ImageIO.read(ImageUtils.class
                    .getResourceAsStream("/org/sleuthkit/autopsy/images/failedToCreateVideoThumb.png"));//NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to load 'failed to create video' placeholder.", ex); //NON-NLS
        }
        return null;
    }

    /**
     * Load the thumbnails that exist in the cache directory for the specified
     * video file.
     *
     * @param cacheDirectory   The directory which exists for the video
     *                         thumbnails.
     * @param thumbnailWrapper The VideoThumbnailWrapper object which contains
     *                         information about the file and the thumbnails
     *                         associated with it.
     */
    private static void loadSavedThumbnails(String cacheDirectory, VideoThumbnailsWrapper thumbnailWrapper, BufferedImage failedVideoThumbImage) {
        int[] framePositions = new int[4];
        List<Image> videoThumbnails = new ArrayList<>();
        int thumbnailNumber = 0;
        String md5 = thumbnailWrapper.getResultFile().getFirstInstance().getMd5Hash();
        for (String fileName : Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, md5).toFile().list()) {
            try {
                videoThumbnails.add(ImageIO.read(Paths.get(cacheDirectory, VIDEO_THUMBNAIL_DIR, md5, fileName).toFile()));
            } catch (IOException ex) {
                videoThumbnails.add(failedVideoThumbImage);
                logger.log(Level.WARNING, "Unable to read saved video thumbnail " + fileName + " for " + md5, ex);
            }
            int framePos = Integer.valueOf(FilenameUtils.getBaseName(fileName).substring(2));
            framePositions[thumbnailNumber] = framePos;
            thumbnailNumber++;
        }
        thumbnailWrapper.setThumbnails(videoThumbnails, framePositions);
    }

    /**
     * Private helper method for creating video thumbnails, for use when no
     * thumbnails are created.
     *
     * @return List containing the default thumbnail.
     */
    private static List<Image> createDefaultThumbnailList(BufferedImage failedVideoThumbImage) {
        List<Image> videoThumbnails = new ArrayList<>();
        videoThumbnails.add(failedVideoThumbImage);
        videoThumbnails.add(failedVideoThumbImage);
        videoThumbnails.add(failedVideoThumbImage);
        videoThumbnails.add(failedVideoThumbImage);
        return videoThumbnails;
    }

    /**
     * Private constructor for DiscoveryUiUtils utility class.
     */
    private DiscoveryUiUtils() {
        //private constructor in a utility class intentionally left blank
    }
}
