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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.corecomponents.FrameCapture;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Video viewer part of the Media View layered pane. Uses different engines
 * depending on platform.
 */
public abstract class MediaViewVideoPanel extends JPanel implements FrameCapture, MediaFileViewer.MediaViewPanel {

    private static final Set<String> AUDIO_EXTENSIONS = new TreeSet<>(Arrays.asList(".mp3", ".wav", ".wma")); //NON-NLS

    private static final Logger logger = Logger.getLogger(MediaViewVideoPanel.class.getName());

    // 64 bit architectures
    private static final String[] ARCH64 = new String[]{"amd64", "x86_64"}; //NON-NLS NON-NLS

    // 32 bit architectures
    private static final String[] ARCH32 = new String[]{"x86"}; //NON-NLS

    /**
     * Factory Method to create a MediaViewVideoPanel.
     *
     * Implementation is dependent on the architecture of the JVM.
     *
     * @return a MediaViewVideoPanel instance.
     */
    public static MediaViewVideoPanel createVideoPanel() {
        if (is64BitJVM()) {
            logger.log(Level.INFO, "64 bit JVM detected. Creating JavaFX Video Player."); //NON-NLS
            return getFXImpl();
        } else {
            logger.log(Level.INFO, "32 bit JVM detected. Creating GStreamer Video Player."); //NON-NLS
            return getGstImpl();
        }
    }

    /**
     * Is the JVM architecture 64 bit?
     *
     * @return
     */
    private static boolean is64BitJVM() {
        String arch = System.getProperty("os.arch");
        return Arrays.asList(ARCH64).contains(arch);
    }

    /**
     * Get a GStreamer video player implementation.
     *
     * @return a GstVideoPanel
     */
    private static MediaViewVideoPanel getGstImpl() {
        return new GstVideoPanel();
    }

    /**
     * Get a JavaFX video player implementation.
     *
     * @return a FXVideoPanel
     */
    private static MediaViewVideoPanel getFXImpl() {
        return new FXVideoPanel();
    }

    /**
     * Has this MediaViewVideoPanel been initialized correctly?
     *
     * @return
     */
    public abstract boolean isInited();

    /**
     * Prepare this MediaViewVideoPanel to accept a different media file.
     */
    abstract void reset();

    /**
     * Initialize all the necessary vars to play a video/audio file.
     *
     * @param file video file to play
     * @param dims dimension of the parent window
     */
    abstract void setupVideo(final AbstractFile file, final Dimension dims);

    /**
     * Return the extensions supported by this video panel.
     *
     * @return
     */
    abstract public String[] getExtensions();

    /**
     * Return the MimeTypes supported by this video panel.
     */
    @Override
    abstract public List<String> getMimeTypes();

    @Override
    public List<String> getExtensionsList() {
        return Arrays.asList(getExtensions());
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        String extension = file.getNameExtension();
        /**
         * Although it seems too restrictive, requiring both a supported
         * extension and a supported MIME type prevents two undesirable
         * behaviors:
         *
         * 1) Until AUT-1766 and AUT-1801 are fixed, we incorrectly identify all
         * iff files as audio/aiff. This means that if this panel went with the
         * looser 'mime type OR extension' criteria we use for images, then this
         * panel would attempt (and fail) to display all iff files, even non
         * audio ones.
         *
         * 2) The looser criteria means we are less confident about the files we
         * are potentialy sending to GStreamer on 32bit jvms. We are less
         * comfortable with the error handling for GStreamer, and don't want to
         * send it files which might cause it trouble.
         */
        if (AUDIO_EXTENSIONS.contains("." + extension) || getExtensionsList().contains("." + extension)) {
            SortedSet<String> mimeTypes = new TreeSet<>(getMimeTypes());
            try {
                String mimeType = new FileTypeDetector().getMIMEType(file);
                return mimeTypes.contains(mimeType);
            } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                logger.log(Level.WARNING, "Failed to look up mimetype for " + file.getName() + " using FileTypeDetector.  Fallingback on AbstractFile.isMimeType", ex);
                if (!mimeTypes.isEmpty() && file.isMimeType(mimeTypes) == AbstractFile.MimeMatchEnum.TRUE) {
                    return true;
                }
            }

            return getExtensionsList().contains("." + extension);
        }
        return false;
    }
}
