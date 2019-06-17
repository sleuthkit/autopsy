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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility class for handling content viewer tags on images.
 */
public final class ImageTagsUtility {

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

    /**
     * Embeds the tag regions into an image.
     *
     * @param file Base Image
     * @param tagRegions Tag regions to be saved into the image
     * @param outputEncoding Format of image (jpg, png, etc). See OpenCV for
     * supported formats. Do not include a "."
     * @return Output image as a BufferedImage
     *
     * @throws TskCoreException Cannot read from abstract file
     * @throws IOException Could not create buffered image from OpenCV result
     */
    public static BufferedImage writeTags(AbstractFile file, Collection<ImageTagRegion> tagRegions,
            String outputEncoding) throws TskCoreException, IOException {
        byte[] imageInMemory = new byte[(int) file.getSize()];
        file.read(imageInMemory, 0, file.getSize());
        Mat originalImage = Highgui.imdecode(new MatOfByte(imageInMemory), Highgui.IMREAD_UNCHANGED);

        tagRegions.forEach((region) -> {
            Core.rectangle(
                    originalImage, //Matrix obj of the image
                    new Point(region.getX(), region.getY()), //p1
                    new Point(region.getX() + region.getWidth(), region.getY() + region.getHeight()), //p2
                    new Scalar(0, 0, 255), //Scalar object for color
                    (int) Math.rint(region.getStrokeThickness())
            );
        });

        MatOfByte matOfByte = new MatOfByte();
        MatOfInt params = new MatOfInt(Highgui.IMWRITE_JPEG_QUALITY, 100);
        Highgui.imencode("." + outputEncoding, originalImage, matOfByte, params);

        try (ByteArrayInputStream imageStream = new ByteArrayInputStream(matOfByte.toArray())) {
            BufferedImage result = ImageIO.read(imageStream);
            originalImage.release();
            matOfByte.release();
            return result;
        }
    }

    /**
     * Creates a thumbnail version of the image with tags applied.
     *
     * @param file Input file to apply tags & produce thumbnail from
     * @param tagRegions Tags to apply
     * @param iconSize Size of the output thumbnail
     * @param outputEncoding Format of thumbnail (jpg, png, etc). See OpenCV for
     * supported formats. Do not include a "."
     * @return BufferedImage representing the thumbnail
     *
     * @throws TskCoreException Could not read from file
     * @throws IOException Could not create buffered image from OpenCV result
     */
    public static BufferedImage makeThumbnail(AbstractFile file, Collection<ImageTagRegion> tagRegions,
            IconSize iconSize, String outputEncoding) throws TskCoreException, IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage result = writeTags(file, tagRegions, outputEncoding);
            ImageIO.write(result, outputEncoding, baos);
            Mat markedUpImage = Highgui.imdecode(new MatOfByte(baos.toByteArray()), Highgui.IMREAD_UNCHANGED);
            Mat thumbnail = new Mat();
            Size resize = new Size(iconSize.getSize(), iconSize.getSize());

            Imgproc.resize(markedUpImage, thumbnail, resize);
            MatOfByte matOfByte = new MatOfByte();
            Highgui.imencode("." + outputEncoding, thumbnail, matOfByte);

            try (ByteArrayInputStream thumbnailStream = new ByteArrayInputStream(matOfByte.toArray())) {
                BufferedImage thumbnailImage = ImageIO.read(thumbnailStream);
                thumbnail.release();
                matOfByte.release();
                markedUpImage.release();
                return thumbnailImage;
            }
        }
    }

    private ImageTagsUtility() {
    }
}
