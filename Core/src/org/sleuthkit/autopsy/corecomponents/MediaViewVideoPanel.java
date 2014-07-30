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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JPanel;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Video viewer part of the Media View layered pane.
 * Uses different engines depending on platform.
 */
public abstract class MediaViewVideoPanel extends JPanel implements FrameCapture {
    
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
     */
    abstract public String[] getExtensions();
    /**
     * Return the MimeTypes supported by this video panel.
     */
    abstract public List<String> getMimeTypes();
}
