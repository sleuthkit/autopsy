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
package org.sleuthkit.autopsy.contentviewers.imagetagging;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Utility for drawing rectangles on image files.
 */
public final class ImageTagsUtil {
    
    //String constant for writing PNG in ImageIO
    private final static String AWT_PNG = "png";
    
    //String constant for encoding PNG in OpenCV
    private final static String OPENCV_PNG = ".png";

    /**
     * Creates an image with tags applied.
     *
     * @param file Source image.
     * @param tagRegions Tags to apply.
     * @return Tagged image.
     *
     * @throws IOException
     * @throws InterruptedException Calling thread was interrupted
     * @throws ExecutionException Error while reading image from AbstractFile
     */
    public static BufferedImage getImageWithTags(AbstractFile file,
            Collection<ImageTagRegion> tagRegions) throws IOException, InterruptedException, ExecutionException {

        //The raw image in OpenCV terms
        Mat sourceImage = getImageMatFromFile(file);
        //Image with tags in OpenCV terms
        MatOfByte taggedMatrix = getTaggedImageMatrix(sourceImage, tagRegions);

        try (ByteArrayInputStream taggedStream = new ByteArrayInputStream(taggedMatrix.toArray())) {
            return ImageIO.read(taggedStream);
        } finally {
            sourceImage.release();
            taggedMatrix.release();
        }
    }
    
    /**
     * Get the image from file.
     * 
     * @param file
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException 
     */
    private static BufferedImage getImageFromFile(AbstractFile file) throws IOException, InterruptedException, ExecutionException {
        if (ImageUtils.isGIF(file)) {
            //Grab the first frame.
            try (BufferedInputStream bufferedReadContentStream = 
                    new BufferedInputStream(new ReadContentInputStream(file))) {
                return ImageIO.read(bufferedReadContentStream);
            }
        } else {
            //Otherwise, read the full image.
            Task<Image> readImageTask = ImageUtils.newReadImageTask(file);
            readImageTask.run();
            Image fxResult = readImageTask.get();
            return SwingFXUtils.fromFXImage(fxResult, null);
        }
    }

    /**
     * Reads the image and converts it into an OpenCV equivalent.
     *
     * @param file Image to read
     * @return raw image bytes
     *
     * @throws IOException
     * @throws InterruptedException Calling thread was interrupted.
     * @throws ExecutionException Error while reading image from AbstractFile
     */
    private static Mat getImageMatFromFile(AbstractFile file) throws InterruptedException, ExecutionException, IOException {
        //Get image from file
        BufferedImage buffImage = getImageFromFile(file);

        //Convert it to OpenCV Mat.
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            ImageIO.write(buffImage, AWT_PNG, outStream);

            byte[] imageBytes = outStream.toByteArray();
            MatOfByte rawSourceBytes = new MatOfByte(imageBytes);
            Mat sourceImage = Highgui.imdecode(rawSourceBytes, Highgui.IMREAD_COLOR);
            rawSourceBytes.release();

            return sourceImage;
        }
    }

    /**
     * Adds tags to an image matrix.
     *
     * @param sourceImage
     * @param tagRegions
     * @return
     */
    private static MatOfByte getTaggedImageMatrix(Mat sourceImage, Collection<ImageTagRegion> tagRegions) {

        //Apply all tags to source image
        for (ImageTagRegion region : tagRegions) {
            Point topLeft = new Point(region.getX(), region.getY());
            Point bottomRight = new Point(topLeft.x + region.getWidth(),
                    topLeft.y + region.getHeight());
            //Red
            Scalar rectangleBorderColor = new Scalar(0, 0, 255);

            int rectangleBorderWidth = (int) Math.rint(region.getStrokeThickness());

            Core.rectangle(sourceImage, topLeft, bottomRight,
                    rectangleBorderColor, rectangleBorderWidth);
        }

        MatOfByte taggedMatrix = new MatOfByte();
        Highgui.imencode(OPENCV_PNG, sourceImage, taggedMatrix);

        return taggedMatrix;
    }

    /**
     * Creates a thumbnail with tags applied.
     *
     * @param file Input file to apply tags & produce thumbnail from
     * @param tagRegions Tags to apply
     * @param iconSize Size of the output thumbnail
     * @return BufferedImage Thumbnail image
     *
     * @throws InterruptedException Calling thread was interrupted.
     * @throws ExecutionException Error while reading image from file.
     */
    public static BufferedImage getThumbnailWithTags(AbstractFile file, Collection<ImageTagRegion> tagRegions,
            IconSize iconSize) throws IOException, InterruptedException, ExecutionException {

        //Raw image
        Mat sourceImage = getImageMatFromFile(file);
        //Full size image with tags
        MatOfByte taggedMatrix = getTaggedImageMatrix(sourceImage, tagRegions);
        //Resized to produce thumbnail
        MatOfByte thumbnailMatrix = getResizedMatrix(taggedMatrix, iconSize);

        try (ByteArrayInputStream thumbnailStream = new ByteArrayInputStream(thumbnailMatrix.toArray())) {
            return ImageIO.read(thumbnailStream);
        } finally {
            sourceImage.release();
            taggedMatrix.release();
            thumbnailMatrix.release();
        }
    }

    /**
     * Resizes the image matrix.
     *
     * @param taggedMatrix Image to resize.
     * @param size Size of thumbnail.
     *
     * @return A new resized image matrix.
     */
    private static MatOfByte getResizedMatrix(MatOfByte taggedMatrix, IconSize size) {
        Size resizeDimensions = new Size(size.getSize(), size.getSize());
        Mat taggedImage = Highgui.imdecode(taggedMatrix, Highgui.IMREAD_COLOR);

        Mat thumbnailImage = new Mat();
        Imgproc.resize(taggedImage, thumbnailImage, resizeDimensions);

        MatOfByte thumbnailMatrix = new MatOfByte();
        Highgui.imencode(OPENCV_PNG, thumbnailImage, thumbnailMatrix);

        thumbnailImage.release();
        taggedImage.release();

        return thumbnailMatrix;
    }

    private ImageTagsUtil() {
    }

    /**
     * Sizes for thumbnails
     */
    public enum IconSize {
        SMALL(50),
        MEDIUM(100),
        LARGE(200);

        private final int SIZE;

        IconSize(int size) {
            this.SIZE = size;
        }

        public int getSize() {
            return SIZE;
        }
    }
}
