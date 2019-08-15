/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import com.google.common.io.Files;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.netbeans.api.progress.ProgressHandle;
import org.opencv.core.Mat;
import org.opencv.highgui.VideoCapture;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import static org.sleuthkit.autopsy.coreutils.VideoUtils.getVideoFileInTempDir;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.HashUtility;

/**
 * Container for files that holds all necessary data for grouping and sorting
 */
class ResultFile {

    private final AbstractFile abstractFile;
    private FileSearchData.Frequency frequency;
    private final List<String> keywordListNames;
    private final List<String> hashSetNames;
    private final List<String> tagNames;
    private final List<String> interestingSetNames;
    private final List<String> objectDetectedNames;
    private final List<Image> thumbnails;
    private final List<AbstractFile> duplicates;
    private FileType fileType;

    /**
     * Create a ResultFile from an AbstractFile
     *
     * @param abstractFile
     */
    ResultFile(AbstractFile abstractFile) {
        this.abstractFile = abstractFile;
        this.frequency = FileSearchData.Frequency.UNKNOWN;
        keywordListNames = new ArrayList<>();
        hashSetNames = new ArrayList<>();
        tagNames = new ArrayList<>();
        interestingSetNames = new ArrayList<>();
        objectDetectedNames = new ArrayList<>();
        thumbnails = new ArrayList<>();
        duplicates = new ArrayList<>();
        fileType = FileType.OTHER;
    }

    /**
     * Get the frequency of this file in the central repository
     *
     * @return The Frequency enum
     */
    FileSearchData.Frequency getFrequency() {
        return frequency;
    }

    /**
     * Set the frequency of this file from the central repository
     *
     * @param frequency The frequency of the file as an enum
     */
    void setFrequency(FileSearchData.Frequency frequency) {
        this.frequency = frequency;
    }

    /**
     * Add an AbstractFile to the list of files which are duplicates of this
     * file.
     *
     * @param duplicate The abstract file to add as a duplicate.
     */
    void addDuplicate(AbstractFile duplicate) {
        duplicates.add(duplicate);
    }

    /**
     * Get the list of AbstractFiles which have been identified as duplicates of
     * this file.
     *
     * @return The list of AbstractFiles which have been identified as
     *         duplicates of this file.
     */
    List<AbstractFile> getDuplicates() {
        return Collections.unmodifiableList(duplicates);
    }

    /**
     * Get the file type.
     *
     * @return The FileType enum.
     */
    FileType getFileType() {
        return fileType;
    }

    /**
     * Set the file type
     *
     * @param fileType the type
     */
    void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Add a keyword list name that matched this file.
     *
     * @param keywordListName
     */
    void addKeywordListName(String keywordListName) {
        if (!keywordListNames.contains(keywordListName)) {
            keywordListNames.add(keywordListName);
        }

        // Sort the list so the getKeywordListNames() will be consistent regardless of the order added
        Collections.sort(keywordListNames);
    }

    /**
     * Get the keyword list names for this file
     *
     * @return the keyword list names that matched this file.
     */
    List<String> getKeywordListNames() {
        return Collections.unmodifiableList(keywordListNames);
    }

    /**
     * Add a hash set name that matched this file.
     *
     * @param hashSetName
     */
    void addHashSetName(String hashSetName) {
        if (!hashSetNames.contains(hashSetName)) {
            hashSetNames.add(hashSetName);
        }

        // Sort the list so the getHashHitNames() will be consistent regardless of the order added
        Collections.sort(hashSetNames);
    }

    /**
     * Get the hash set names for this file
     *
     * @return The hash set names that matched this file.
     */
    List<String> getHashSetNames() {
        return Collections.unmodifiableList(hashSetNames);
    }

    /**
     * Add a tag name that matched this file.
     *
     * @param tagName
     */
    void addTagName(String tagName) {
        if (!tagNames.contains(tagName)) {
            tagNames.add(tagName);
        }

        // Sort the list so the getTagNames() will be consistent regardless of the order added
        Collections.sort(tagNames);
    }

    /**
     * Get the tag names for this file
     *
     * @return the tag names that matched this file.
     */
    List<String> getTagNames() {
        return Collections.unmodifiableList(tagNames);
    }

    /**
     * Add an interesting file set name that matched this file.
     *
     * @param interestingSetName
     */
    void addInterestingSetName(String interestingSetName) {
        if (!interestingSetNames.contains(interestingSetName)) {
            interestingSetNames.add(interestingSetName);
        }

        // Sort the list so the getInterestingSetNames() will be consistent regardless of the order added
        Collections.sort(interestingSetNames);
    }

    private void createThumbnails(FileSearchData.FileType resultType) {
        if (resultType == FileType.IMAGE) {
            thumbnails.add(ImageUtils.getThumbnail(abstractFile, ImageUtils.ICON_SIZE_MEDIUM));
        } else if (resultType == FileType.VIDEO) {
            thumbnails.add(ImageUtils.getThumbnail(abstractFile, ImageUtils.ICON_SIZE_LARGE));
            thumbnails.add(ImageUtils.getThumbnail(abstractFile, ImageUtils.ICON_SIZE_LARGE));
            thumbnails.add(ImageUtils.getThumbnail(abstractFile, ImageUtils.ICON_SIZE_LARGE));
            thumbnails.add(ImageUtils.getThumbnail(abstractFile, ImageUtils.ICON_SIZE_LARGE));
        }

    }

    @NbBundle.Messages({"# {0} - file name",
        "ResultFile.genVideoThumb.progress.text=extracting temporary file {0}"})
    static ThumbnailsWrapper createVideoThumbnails(AbstractFile file) {
        java.io.File tempFile;
        try {
            tempFile = getVideoFileInTempDir(file);
        } catch (NoCurrentCaseException ex) {
//            LOGGER.log(Level.WARNING, "Exception while getting open case.", ex); //NON-NLS
            int[] framePositions = new int[]{
                0,
                0,
                0,
                0};
            List<Image> videoThumbnails = new ArrayList<>();
            videoThumbnails.add(ImageUtils.getDefaultThumbnail());
            videoThumbnails.add(ImageUtils.getDefaultThumbnail());
            videoThumbnails.add(ImageUtils.getDefaultThumbnail());
            videoThumbnails.add(ImageUtils.getDefaultThumbnail());
            return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
        }
        if (tempFile.exists() == false || tempFile.length() < file.getSize()) {
            ProgressHandle progress = ProgressHandle.createHandle(Bundle.ResultFile_genVideoThumb_progress_text(file.getName()));
            progress.start(100);
            try {
                Files.createParentDirs(tempFile);
                if (Thread.interrupted()) {
                    int[] framePositions = new int[]{
                        0,
                        0,
                        0,
                        0};
                    List<Image> videoThumbnails = new ArrayList<>();
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
                }
                ContentUtils.writeToFile(file, tempFile, progress, null, true);
            } catch (IOException ex) {
//                LOGGER.log(Level.WARNING, "Error extracting temporary file for " + ImageUtils.getContentPathSafe(file), ex); //NON-NLS
            } finally {
                progress.finish();
            }
        }
        VideoCapture videoFile = new VideoCapture(); // will contain the video
        BufferedImage bufferedImage = null;

        try {
            if (!videoFile.open(tempFile.toString())) {
//                LOGGER.log(Level.WARNING, "Error opening {0} for preview generation.", ImageUtils.getContentPathSafe(file)); //NON-NLS
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                List<Image> videoThumbnails = new ArrayList<>();
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
            }
            double fps = videoFile.get(5); // gets frame per second
            double totalFrames = videoFile.get(7); // gets total frames
            if (fps <= 0 || totalFrames <= 0) {
//                LOGGER.log(Level.WARNING, "Error getting fps or total frames for {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                List<Image> videoThumbnails = new ArrayList<>();
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
            }
            if (Thread.interrupted()) {
                int[] framePositions = new int[]{
                    0,
                    0,
                    0,
                    0};
                List<Image> videoThumbnails = new ArrayList<>();
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
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
                (int) (duration * .01),
                (int) (duration * .25),
                (int) (duration * .5),
                (int) (duration * .75),};

            Mat imageMatrix = new Mat();
            List<Image> videoThumbnails = new ArrayList<>();
            for (int i = 0; i < framePositions.length; i++) {
                if (!videoFile.set(0, framePositions[i])) {
//                    LOGGER.log(Level.WARNING, "Error seeking to " + framePositions[i] + "ms in {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                    // If we can't set the time, continue to the next frame position and try again.

                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    continue;
                }
                // Read the frame into the image/matrix.
                if (!videoFile.read(imageMatrix)) {
//                    LOGGER.log(Level.WARNING, "Error reading frame at " + framePositions[i] + "ms from {0}", ImageUtils.getContentPathSafe(file)); //NON-NLS
                    // If the image is bad for some reason, continue to the next frame position and try again.
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
                    continue;
                }
                // If the image is empty, return since no buffered image can be created.
                if (imageMatrix.empty()) {
                    videoThumbnails.add(ImageUtils.getDefaultThumbnail());
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

                //todo: this looks like we are swapping the first and third channels.  so we can use  BufferedImage.TYPE_3BYTE_BGR
                if (imageMatrix.channels() == 3) {
                    for (int k = 0; k < data.length; k += 3) {
                        byte temp = data[k];
                        data[k] = data[k + 2];
                        data[k + 2] = temp;
                    }
                }

                bufferedImage.getRaster().setDataElements(0, 0, matrixColumns, matrixRows, data);
                if (Thread.interrupted()) {
                    return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
                }
                videoThumbnails.add(bufferedImage == null ? ImageUtils.getDefaultThumbnail() : ScalrWrapper.resizeFast(bufferedImage, ImageUtils.ICON_SIZE_MEDIUM));
            }
            return new ThumbnailsWrapper(videoThumbnails, framePositions, file);
        } finally {
            videoFile.release(); // close the file}
        }
    }

    List<Image> getThumbnails(FileSearchData.FileType resultType) {
        if (thumbnails.isEmpty()) {
            createThumbnails(resultType);
        }
        return Collections.unmodifiableList(thumbnails);
    }

    /**
     * Get the interesting item set names for this file
     *
     * @return the interesting item set names that matched this file.
     */
    List<String> getInterestingSetNames() {
        return Collections.unmodifiableList(interestingSetNames);
    }

    /**
     * Add an object detected in this file.
     *
     * @param objectDetectedName
     */
    void addObjectDetectedName(String objectDetectedName) {
        if (!objectDetectedNames.contains(objectDetectedName)) {
            objectDetectedNames.add(objectDetectedName);
        }

        // Sort the list so the getObjectDetectedNames() will be consistent regardless of the order added
        Collections.sort(objectDetectedNames);
    }

    /**
     * Get the objects detected for this file
     *
     * @return the objects detected in this file.
     */
    List<String> getObjectDetectedNames() {
        return Collections.unmodifiableList(objectDetectedNames);
    }

    /**
     * Get the AbstractFile
     *
     * @return the AbstractFile object
     */
    AbstractFile getAbstractFile() {
        return abstractFile;
    }

    @Override
    public String toString() {
        return abstractFile.getName() + "(" + abstractFile.getId() + ") - "
                + abstractFile.getSize() + ", " + abstractFile.getParentPath() + ", "
                + abstractFile.getDataSourceObjectId() + ", " + frequency.toString() + ", "
                + String.join(",", keywordListNames) + ", " + abstractFile.getMIMEType();
    }

    @Override
    public int hashCode() {
        if (this.getAbstractFile().getMd5Hash() == null
                || HashUtility.isNoDataMd5(this.getAbstractFile().getMd5Hash())
                || !HashUtility.isValidMd5Hash(this.getAbstractFile().getMd5Hash())) {
            return super.hashCode();
        } else {
            //if the file has a valid MD5 use the hashcode of the MD5 for deduping files with the same MD5
            return this.getAbstractFile().getMd5Hash().hashCode();
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ResultFile)
                || this.getAbstractFile().getMd5Hash() == null
                || HashUtility.isNoDataMd5(this.getAbstractFile().getMd5Hash())
                || !HashUtility.isValidMd5Hash(this.getAbstractFile().getMd5Hash())) {
            return super.equals(obj);
        } else {
            //if the file has a valid MD5 compare use the MD5 for equality check
            return this.getAbstractFile().getMd5Hash().equals(((ResultFile) obj).getAbstractFile().getMd5Hash());
        }
    }
}
