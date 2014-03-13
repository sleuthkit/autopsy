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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.opencv.highgui.VideoCapture;
import org.opencv.core.Mat;
import org.opencv.core.Range;
import org.opencv.core.Core;
/**
 * Utilities for creating and manipulating thumbnail and icon images.
 * @author jwallace
 */
public class ImageUtils {
    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;
    private static final Logger logger = Logger.getLogger(ImageUtils.class.getName());
    private static final Image DEFAULT_ICON = new ImageIcon("/org/sleuthkit/autopsy/images/home_tree.png").getImage();
    private static List<String> SUPP_EXTENSIONS = new ArrayList<>(Arrays.asList(ImageIO.getReaderFileSuffixes())); //final
    private static List<String> SUPP_MIME_TYPES = new ArrayList<>(Arrays.asList(ImageIO.getReaderMIMETypes())); // final
    private static List<String> VIDEO_SUPP_EXTENSIONS = new ArrayList<>(); //final
    private static List<String> VIDEO_SUPP_MIME_TYPES = new ArrayList<>(); // final
    
    private static boolean temp_add =false;
    /**
     * Get the default Icon, which is the icon for a file.
     * @return 
     */
    public static Image getDefaultIcon() {
        return DEFAULT_ICON;
    }
    
    /**
     * Can a thumbnail be generated for the content?
     * 
     * @param content
     * @return 
     */
    public static boolean thumbnailSupported(Content content) {
         if (temp_add == false) {
        SUPP_EXTENSIONS.add("avi");
        SUPP_EXTENSIONS.add("mp4");
        SUPP_EXTENSIONS.add("wmv");
        SUPP_MIME_TYPES.add("video/avi"); 
        SUPP_MIME_TYPES.add("video/msvideo"); 
        SUPP_MIME_TYPES.add("video/x-msvideo"); 
        SUPP_MIME_TYPES.add("video/mp4");
        SUPP_MIME_TYPES.add("video/x-ms-wmv");
        SUPP_MIME_TYPES.add("video/x-msvideo");
        SUPP_MIME_TYPES.add("video/x-msvideo");
        
        VIDEO_SUPP_EXTENSIONS.add("avi");
        VIDEO_SUPP_EXTENSIONS.add("mp4");
        VIDEO_SUPP_EXTENSIONS.add("wmv");
        VIDEO_SUPP_MIME_TYPES.add("video/avi"); 
        VIDEO_SUPP_MIME_TYPES.add("video/msvideo"); 
        VIDEO_SUPP_MIME_TYPES.add("video/x-msvideo"); 
        VIDEO_SUPP_MIME_TYPES.add("video/mp4");
        VIDEO_SUPP_MIME_TYPES.add("video/x-ms-wmv");
        VIDEO_SUPP_MIME_TYPES.add("video/x-msvideo");
        VIDEO_SUPP_MIME_TYPES.add("video/x-msvideo");
        temp_add=true;
         }
        if (content instanceof AbstractFile == false) {
            return false;
        }
        
        AbstractFile f = (AbstractFile) content;
        if (f.getSize() == 0) {
            return false;
        }

        // check the blackboard for a file type attribute
        try {
            ArrayList <BlackboardAttribute> attributes = f.getGenInfoAttributes(ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) { 
                if (SUPP_MIME_TYPES.contains(attribute.getValueString())) {
                    return true;
                }
            }
        } 
        catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error while getting file signature from blackboard.", ex);
        }
        
        final String extension = f.getNameExtension();
        
        // if we have an extension, check it
        if (extension.equals("") == false) {
            // Note: thumbnail generator only supports JPG, GIF, and PNG for now
            if (SUPP_EXTENSIONS.contains(extension)) {
                return true;
            }
        }
        
        // if no extension or one that is not for an image, then read the content
        return isJpegFileHeader(f);    
    }

   
    /**
     * Get an icon of a specified size.
     * 
     * @param content
     * @param iconSize
     * @return 
     */
    public static Image getIcon(Content content, int iconSize) {
        Image icon=null;
       
        // If a thumbnail file is already saved locally
        File file = getFile(content.getId());
        if (file.exists()) {
            try {
                BufferedImage bicon = ImageIO.read(file);
                if (bicon == null) {
                    icon = DEFAULT_ICON;
                } else if (bicon.getWidth() != iconSize) {
                    icon = generateAndSaveIcon(content, iconSize);    
                } else {
                    icon = bicon;    
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error while reading image.", ex);
                icon = DEFAULT_ICON;
            }
        } else { // Make a new icon
            AbstractFile f = (AbstractFile) content;
            final String extension = f.getNameExtension();
        
               // if we have an extension, check it
                if (extension.equals("") == false) 
                {
                    // Note: thumbnail generator only supports JPG, GIF, and PNG for now
                    if (VIDEO_SUPP_EXTENSIONS.contains(extension)) 
                    {
                        icon = generateVideoIcon(content, iconSize);
                    }
                }
                else 
                {
                    icon = generateAndSaveIcon(content, iconSize);
                }
        }
        if (icon==null) return DEFAULT_ICON;
        return icon;
    }
    
    /**
     * Get the cached file of the icon. Generates the icon and its file if it 
     * doesn't already exist, so this method guarantees to return a file that
     * exists.
     * @param content
     * @param iconSize
     * @return 
     */
    public static File getIconFile(Content content, int iconSize) {
        if (getIcon(content, iconSize) != null) {
            return getFile(content.getId());
        }
        return null;
    }
    
    /**
     * Get the cached file of the content object with the given id. 
     * 
     * The returned file may not exist.
     * 
     * @param id
     * @return 
     */
    public static File getFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".png");
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
        if (((int) (fileHeaderBuffer[0] & 0xff) == 0xff) && ((int) (fileHeaderBuffer[1] & 0xff) == 0xd8)) {
            return true;
        }
        return false;
    }
     private static Image generateVideoIcon(Content content, int iconSize) { 
        Image icon = null;
        
       System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
       try {
          if (System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64")){
              System.loadLibrary("opencv_ffmpeg248_64");
          }else{
             System.loadLibrary("opencv_ffmpeg248");
             System.loadLibrary("opencv_java248");
             
          }
       }catch (UnsatisfiedLinkError e) {
           Logger.getLogger(AddContentTagAction.class.getName()).log(Level.SEVERE, "OpenCV Native code library failed to load", e);       
           return DEFAULT_ICON;
       }
        File file = getFile(content.getId());
        String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
        String fileName = content.getId() + (ext.isEmpty() ? "" : "." + ext);
        java.io.File jFile = new java.io.File(Case.getCurrentCase().getTempDirectory(), fileName);
        Image image; //final image to be returned
       
         try {
         //createFile(); // create entire file in TEMP
         copyFileUsingStream(content,jFile); //create small file in TEMP
        }catch(Exception ex) {
            return DEFAULT_ICON;
        }  
               fileName = jFile.toString(); //store filepath as String
        VideoCapture videoFile= new VideoCapture(); // will contain the video     
        
        if(!videoFile.open(fileName))return DEFAULT_ICON;
        double fps = videoFile.get(5); // gets frame per second
        double totalFrames = videoFile.get(7); // gets total frames
        if (fps==0||totalFrames==0 ) return DEFAULT_ICON;
        double milliseconds= 1000*(totalFrames/fps); //total milliseconds
        if (milliseconds <= 0) return DEFAULT_ICON;
        
        
        Mat mat = new Mat();
        double timestamp = (milliseconds<500)? milliseconds:500; //default time to check for is 500ms, unless the files is extremely small
        
        if(!videoFile.set(0,timestamp))return DEFAULT_ICON;
        if(! videoFile.read(mat)) return DEFAULT_ICON; //if the image for some reason is bad, return default icon
        
                byte[] data = new byte[mat.rows()*mat.cols()*(int)(mat.elemSize())];
        mat.get(0, 0, data);
        
        if (mat.channels() == 3) 
        {
            for (int k = 0; k < data.length; k += 3) 
            {
             byte temp = data[k];
             data[k] = data[k + 2];
             data[k + 2] = temp;
            }
        }
        BufferedImage B_image = new BufferedImage(mat.cols(), mat.rows(), BufferedImage.TYPE_3BYTE_BGR);
        B_image.getRaster().setDataElements(0, 0, mat.cols(), mat.rows(), data);
        
       // image = SwingFXUtils.toFXImage(B_image, null); //convert bufferedImage to Image
        videoFile.release(); // close the file
      //  if (image==null) return DEFAULT_ICON;
        if (B_image==null) return DEFAULT_ICON;
        else return B_image;
         
    }
    
    private static Image generateAndSaveIcon(Content content, int iconSize) { 
        Image icon = null;
        try {
            icon = generateIcon(content, iconSize);
            if (icon == null) {
                return DEFAULT_ICON;
            } else {
                File f = getFile(content.getId());
                if (f.exists()) {
                    f.delete();
                }
                ImageIO.write((BufferedImage) icon, "png", getFile(content.getId()));
            }         
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex);
        }  
        if (icon==null) return DEFAULT_ICON;
        return icon;        
    }
    
    /*
     * Generate a scaled image
     */
    private static BufferedImage generateIcon(Content content, int iconSize) {

        InputStream inputStream = null;
        try {
            inputStream = new ReadContentInputStream(content);
            BufferedImage bi = ImageIO.read(inputStream);
            if (bi == null) {
                logger.log(Level.WARNING, "No image reader for file: " + content.getName());
                return null;
            }
            BufferedImage biScaled = ScalrWrapper.resizeFast(bi, iconSize);
            if (biScaled==null) return (BufferedImage)DEFAULT_ICON;
            return biScaled;
        } catch (OutOfMemoryError e) {
            logger.log(Level.WARNING, "Could not scale image (too large): " + content.getName(), e);
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not scale image: " + content.getName(), e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close input stream after resizing thumbnail: " + content.getName(), ex);
                }
            }

        }
             
    }
     public static void copyFileUsingStream(Content file,java.io.File jFile) throws IOException {
       InputStream is = new ReadContentInputStream(file);
       // copy the file data to the temporary file

       OutputStream os = new FileOutputStream(jFile);
       byte[] buffer = new byte[8192];
       int length;
       int counter =0;
        try {
                while ((length = is.read(buffer)) != -1) 
                {
                    os.write(buffer, 0, length);
                    counter++;
                    if (counter== 63) break; //after saving 500 KB (63*8192)
                }
            
        } finally {
            is.close();
            os.close();
        }

    }
}
