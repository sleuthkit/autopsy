/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel;

import java.lang.ref.SoftReference;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.imagegallery.ThumbnailCache;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * ImageGallery data model object that represents an image file. It is a
 * wrapper(/decorator?/adapter?) around {@link AbstractFile} and provides
 * methods to get an icon sized and a full sized {@link  Image}.
 *
 *
 */
public class ImageFile<T extends AbstractFile> extends DrawableFile<T> {

    private SoftReference<Image> imageRef;

    ImageFile(T f, Boolean analyzed) {
        super(f, analyzed);

    }

    @Override
    public Image getThumbnail() {
        return ThumbnailCache.getDefault().get(this);
    }

    public Image getFullSizeImage() {
        Image image = null;
//        if (imageRef != null) {
//            image = imageRef.get();
//        }
//        if (image == null) {
//
//            try (ReadContentInputStream readContentInputStream = new ReadContentInputStream(this.getAbstractFile())) {
//                BufferedImage read = ImageIO.read(readContentInputStream);
//                image = SwingFXUtils.toFXImage(read, null);
//            } catch (IOException | NullPointerException ex) {
//                Logger.getLogger(ImageFile.class.getName()).log(Level.WARNING, "unable to read file" + getName());
//                return null;
//            }
//            imageRef = new SoftReference<>(image);
//        }
        if (image == null) {
            try {
                String fileType1 = new FileTypeDetector().getFileType(file);
                String extension = MimeTypes.getDefaultMimeTypes().forName(fileType1).getExtension();
                extension = StringUtils.strip(extension, ".");
                final String name = "/org/sleuthkit/autopsy/imagegallery/images/mimeTypes/" + extension + "-icon-128x128.png";
                System.out.println(name);
                final String toExternalForm = ImageFile.class.getResource(name).toExternalForm();
                return new Image(toExternalForm, true);

            } catch (NullPointerException | FileTypeDetector.FileTypeDetectorInitException | TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            } catch (MimeTypeException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
            return image;
    }

    @Override
    Double getWidth() {
        final Image fullSizeImage = getFullSizeImage();
        if (fullSizeImage != null) {
            return fullSizeImage.getWidth();
        }
        return -1.0;
    }

    @Override
    Double getHeight() {
        final Image fullSizeImage = getFullSizeImage();
        if (fullSizeImage != null) {
            return fullSizeImage.getHeight();
        }
        return -1.0;
    }

    @Override
    public boolean isVideo() {
        return false;
    }
}
