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

import java.util.Collection;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility class for handling content viewer tags on images.
 */
public class ImageTagsUtility {

    /**
     * Embeds the tag regions into an image (represented as an AbstractFile).
     * 
     * @param file Base Image
     * @param tagRegions Tag regions to be saved into the image
     * @param outputEncoding Output file type encoding (ex. .jpg, .png)
     * @return output image in byte array
     * @throws TskCoreException 
     */
    public static byte[] exportTags(AbstractFile file, Collection<ImageTagRegion> tagRegions, String outputEncoding) throws TskCoreException {
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
        Highgui.imencode(outputEncoding, originalImage, matOfByte);
        
        originalImage.release();
        byte[] output = matOfByte.toArray();
        matOfByte.release();
        
        return output;
    }
    
    private ImageTagsUtility(){
    }
}
