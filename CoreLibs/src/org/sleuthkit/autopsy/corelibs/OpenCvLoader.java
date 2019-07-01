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

    private static final Logger LOGGER = Logger.getLogger(OpenCvLoader.class.getName());
    private static boolean openCvLoaded;
    private static UnsatisfiedLinkError exception = null;

    static {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            openCvLoaded = true;
        } catch (UnsatisfiedLinkError ex) {
            LOGGER.log(Level.WARNING, "Unable to load OpenCV", ex);
            exception = ex;  //save relevant error for throwing at appropriate time
            openCvLoaded = false;
        } catch (SecurityException ex) {
            LOGGER.log(Level.WARNING, "Unable to load OpenCV", ex);
            openCvLoaded = false;
        }
        
    }

    /**
     * Return whether or not the OpenCV library has been loaded.
     *
     * @return - true if the opencv library is loaded or false if it is not
     * @throws UnsatisfiedLinkError - A COPY of the exception that prevented
     * OpenCV from loading. Note that the stack trace in the exception can be
     * confusing because it refers to a past invocation.
     *
     * @deprecated Use hasOpenCvLoaded instead.
     */
    @Deprecated
    public static boolean isOpenCvLoaded() throws UnsatisfiedLinkError {
        if (!openCvLoaded) {
            //exception should never be null if the open cv isn't loaded but just in case
            if (exception != null) {
                throw exception;
            } else {
                throw new UnsatisfiedLinkError("OpenCV native library failed to load");
            }

        }
        return openCvLoaded;
    }

    /**
     * Return whether OpenCV library has been loaded.
     *
     * @return true if OpenCV library was loaded, false if not.
     */
    public static boolean hasOpenCvLoaded() {
        return openCvLoaded;
    }
}
