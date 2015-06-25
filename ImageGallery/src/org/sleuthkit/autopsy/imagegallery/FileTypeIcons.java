/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.net.URL;
import java.util.Objects;
import java.util.logging.Level;
import javafx.scene.image.Image;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.ImageFile;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class FileTypeIcons {

    private static final Image VIDEO_ICON = new Image("org/sleuthkit/autopsy/imagegallery/images/Clapperboard.png");
    private static final Image IMAGE_ICON = new Image("org/sleuthkit/autopsy/imagegallery/images/Clapperboard.png");

    public static Image getGenericVideoThumbnail() {
        return VIDEO_ICON;
    }

    private Image getGenericImageIcon() {
        return IMAGE_ICON;
    }
    static private FileTypeIcons instance;

    static public synchronized FileTypeIcons FileTypeIconsgetInstance() {
        if (Objects.isNull(instance)) {
            instance = new FileTypeIcons();
        }

        return instance;
    }

    private final LoadingCache<String, Image> iconCache = CacheBuilder.newBuilder().build(CacheLoader.from(this::getImageForMimeType));

    Image getImageForMimeType(String mimeType) {
        try {
            final MimeType forName = MimeTypes.getDefaultMimeTypes().forName(mimeType);
            for (String extension : forName.getExtensions()) {
                final URL iconURL = ImageFile.class.getResource("/org/sleuthkit/autopsy/imagegallery/images/mimeTypes/"
                        + StringUtils.strip(extension, ".") + "-icon-16x16.png");
                if (Objects.isNull(iconURL)) {
                    return new Image(iconURL.toExternalForm(), true);
                }
            }
        } catch (MimeTypeException ex) {
            Logger.getLogger(FileTypeIcons.class.getName()).log(Level.WARNING, "Failed to get MimeType for " + mimeType);
        }
        return null;
    }

    Image getFileTypeIcon(DrawableFile<?> file) {
        if (Objects.nonNull(fileTypeDetector)) {
            try {
                String fileType1 = fileTypeDetector.getFileType(file.getAbstractFile());
                return iconCache.getUnchecked(fileType1);
            } catch (TskCoreException ex) {
                Logger.getLogger(FileTypeIcons.class.getName()).log(Level.WARNING, "Failed to get file type for " + file.getName());
            }
        }

        return file.isVideo() ? getGenericVideoThumbnail() : getGenericImageIcon();
    }
    private final FileTypeDetector fileTypeDetector;

    private FileTypeIcons() {
        FileTypeDetector ftd = null;
        try {
            ftd = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            Exceptions.printStackTrace(ex);
        }
        fileTypeDetector = ftd;
    }

}
