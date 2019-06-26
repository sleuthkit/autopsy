/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corelibs;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.opencv.core.Core;

public final class OpenCvLoader {

    private static Logger logger = Logger.getLogger(OpenCvLoader.getClass().getName());
    private static final boolean OPEN_CV_LOADED;
    
    static {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            OPEN_CV_LOADED = true;
        } catch (UnsatisfiedLinkError | SecurityException ex) {
            OPEN_CV_LOADED = false;
            logger.log(Level.WARNING, "Unable to load OpenCV", ex);
        }
    }

    /**
     * Return whether or not the OpenCV library has been loaded.
     *
     * @return - true if the opencv library is loaded or false if it is not
     */
    public static boolean isOpenCvLoaded() {
        return OPEN_CV_LOADED;
    }
}
