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
package org.sleuthkit.autopsy.scalpel.jni;

import java.util.Arrays;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * JNI wrapper over libscalpel and library loader
 */
public class ScalpelCarver {

    private static final String SCALPEL_JNI_LIB = "libscalpel_jni";
    private static volatile ScalpelCarver instance;
    private boolean initialized = false;
    private static final Logger logger = Logger.getLogger(ScalpelCarver.class.getName());

    private static native void carveNat(ReadContentInputStream input, String configFilePath, String outputFolderPath) throws ScalpelException;

    private ScalpelCarver() {
        init();
    }

    private void init() {
        initialized = true;
        for (String library : Arrays.asList("libtre-4", "pthreadGC2", SCALPEL_JNI_LIB)) {
            if (!loadLib(library)) {
                initialized = false;
                logger.log(Level.SEVERE, "Failed initializing " + ScalpelCarver.class.getName() + " due to failure loading library: " + library);
                break;
            }
        }
        
        if (initialized) {
            logger.log(Level.INFO, ScalpelCarver.class.getName() + " JNI initialized successfully. ");
        }
    }

    /**
     * initialize, load dynamic libraries
     */
    private boolean loadLib(String id) {
        boolean success = false;
        try {
            //rely on netbeans / jna to locate the lib variation for architecture/OS
            System.loadLibrary(id);
            success = true;
        } catch (UnsatisfiedLinkError ex) {
            String msg = "Could not load library " + id + " for your environment ";
            System.out.println(msg + ex.toString());
            logger.log(Level.SEVERE, msg, ex);
        } catch (Exception ex) {
            String msg = "Could not load library " + id + " for your environment ";
            System.out.println(msg + ex.toString());
            logger.log(Level.SEVERE, msg, ex);
        }

        return success;
    }

    /**
     * Singleton getter for ScalpelCarver. Initializes and returns a
     * ready-instance of scalpel wrapper.
     *
     * @return ScalpelCarver instance
     */
    public static ScalpelCarver getInstance() {
        if (instance == null) {
            synchronized (ScalpelCarver.class) {
                if (instance == null) {
                    instance = new ScalpelCarver();
                }
            }
        }

        return instance;
    }

    /**
     * Check if initialized
     *
     * @return true if library has been initialized properly, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Carve the file passed in as an argument and save the results.
     *
     * TODO consider returning a data structure representing carved files.
     *
     * @param file File to carve #param configFilePath file path to scalpel
     * configuration file with signatures, such as scalpel.conf
     * @param outputFolder Location to save the reults to (should be in the case
     * folder)
     * @throws ScalpelException on errors
     */
    public void carve(AbstractFile file, String configFilePath, String outputFolderPath) throws ScalpelException {
        if (!initialized) {
            throw new ScalpelException("Scalpel library is not fully initialized. ");
        }

        //basic check of arguments before going to jni land
        if (file == null || configFilePath == null || configFilePath.isEmpty()
                || outputFolderPath == null || outputFolderPath.isEmpty()) {
            throw new ScalpelException("Invalid arguments for scalpel carving. ");
        }

        final ReadContentInputStream carverInput = new ReadContentInputStream(file);

        carveNat(carverInput, configFilePath, outputFolderPath);
    }
}
