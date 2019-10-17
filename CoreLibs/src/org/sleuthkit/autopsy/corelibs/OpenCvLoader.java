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

/**
 * A utility class that loads the core OpenCV library and allows clients to
 * verify that the library was loaded.
 */
public final class OpenCvLoader {

    // Uses java logger since the Autopsy class logger (Autopsy-core) is not part of this module
    private static final Logger logger = Logger.getLogger(OpenCvLoader.class.getName());
    private static boolean openCvLoaded;
    private static UnsatisfiedLinkError exception = null; // Deprecated

    static {
        openCvLoaded = false;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            openCvLoaded = true;
        } catch (UnsatisfiedLinkError ex) {
            logger.log(Level.WARNING, "Failed to load core OpenCV library", ex);
            /*
             * Save exception to rethrow later (deprecated).
             */
            exception = ex;
        } catch (Exception ex) {
            /*
             * Exception firewall to ensure that runtime exceptions do not cause
             * the loading of this class by the Java class loader to fail.
             */
            logger.log(Level.WARNING, "Failed to load core OpenCV library", ex);
        }

    }

    /**
     * Indicates whether or not the core OpenCV library has been loaded.
     *
     * @return True or false.
     */
    public static boolean openCvIsLoaded() {
        return openCvLoaded;
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private OpenCvLoader() {
    }

    /**
     * Indicates whether or not the core OpenCV library has been loaded.
     *
     * @return True or false.
     *
     * @throws UnsatisfiedLinkError if this error was thrown during the loading
     *                              of the core OpenCV library during static
     *                              initialization of this class.
     *
     * @deprecated Use openCvIsLoaded instead.
     */
    @Deprecated
    public static boolean isOpenCvLoaded() throws UnsatisfiedLinkError {
        if (!openCvLoaded) {
            if (exception != null) {
                throw exception;
            } else {
                throw new UnsatisfiedLinkError("OpenCV native library failed to load");
            }
        }
        return openCvLoaded;
    }

}
