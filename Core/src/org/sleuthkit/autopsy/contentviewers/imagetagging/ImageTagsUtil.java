/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 *
 * @author dsmyda
 */
public class ImageTagsUtil {

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
}
