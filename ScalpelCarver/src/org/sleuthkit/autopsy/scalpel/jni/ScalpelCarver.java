/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.scalpel.jni.ScalpelOutputParser.CarvedFileMeta;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * JNI wrapper over libscalpel and library loader
 */
public class ScalpelCarver {

    private static final String SCALPEL_JNI_LIB = "libscalpel_jni"; //NON-NLS
    private static final String SCALPEL_OUTPUT_FILE_NAME = "audit.txt"; //NON-NLS
    private static volatile boolean initialized = false;
    private static final Logger logger = Logger.getLogger(ScalpelCarver.class.getName());

    private static native void carveNat(String carverInputId, ReadContentInputStream input, String configFilePath, String outputFolderPath) throws ScalpelException;

    public  ScalpelCarver() {
        
    }

    public static synchronized boolean init() {
        if (initialized) {
            return true;
        }
        initialized = true;
        for (String library : Arrays.asList("libtre-4", "pthreadGC2", SCALPEL_JNI_LIB)) { //NON-NLS
            if (!loadLib(library)) {
                initialized = false;
                logger.log(Level.SEVERE, "Failed initializing " + ScalpelCarver.class.getName() + " due to failure loading library: " + library); //NON-NLS
                break;
            }
        }
        
        if (initialized) {
            logger.log(Level.INFO, ScalpelCarver.class.getName() + " JNI initialized successfully. "); //NON-NLS
        }
        
        return initialized;
    }

    /**
     * initialize, load dynamic libraries
     */
    private static boolean loadLib(String id) {
        boolean success = false;
        try {
            //rely on netbeans / jna to locate the lib variation for architecture/OS
            System.loadLibrary(id);
            success = true;
        } catch (UnsatisfiedLinkError ex) {
            String msg = NbBundle.getMessage(ScalpelCarver.class, "ScalpelCarver.loadLib.errMsg.cannotLoadLib", id);
            logger.log(Level.SEVERE, msg, ex);
        } catch (Exception ex) {
            String msg = NbBundle.getMessage(ScalpelCarver.class, "ScalpelCarver.loadLib.errMsg.cannotLoadLib2", id);
            logger.log(Level.SEVERE, msg, ex);
        }

        return success;
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
     * Requires prior call to ScalpelCarver.init()
     *
     *
     * @param file File to carve 
     * @param configFilePath file path to scalpel
     * configuration file with signatures, such as scalpel.conf
     * @param outputFolderPath Location to save the reults to (should be in the case
     * folder)
     * @return list of carved files info
     * @throws ScalpelException on errors
     */
    public List<CarvedFileMeta> carve(AbstractFile file, String configFilePath, String outputFolderPath) throws ScalpelException {
        if (!initialized) {
            throw new ScalpelException(NbBundle.getMessage(this.getClass(), "ScalpelCarver.carve.exception.libNotInit"));
        }

        //basic check of arguments before going to jni land
        if (file == null || configFilePath == null || configFilePath.isEmpty()
                || outputFolderPath == null || outputFolderPath.isEmpty()) {
            throw new ScalpelException(NbBundle.getMessage(this.getClass(), "ScalpelCarver.carve.exception.invalidArgs"));
        }
        
        //validate the paths passed in
        File config = new File(configFilePath);
        if (! config.exists() || ! config.canRead()) {
            throw new ScalpelException(
                    NbBundle.getMessage(this.getClass(), "ScalpelCarver.carve.exception.cannotReadConfig",
                                        configFilePath));
        }
        
        File outDir = new File(outputFolderPath);
        if (! outDir.exists() || ! outDir.canWrite()) {
            throw new ScalpelException(
                    NbBundle.getMessage(this.getClass(), "ScalpelCarver.carve.exception.cannotWriteConfig",
                                        outputFolderPath));
        }

        final String carverInputId = file.getId() + ": " + file.getName();
        final ReadContentInputStream carverInput = new ReadContentInputStream(file);
        

        try {
            carveNat(carverInputId, carverInput, configFilePath, outputFolderPath);
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Error while caving file " + file, e); //NON-NLS
            throw new ScalpelException(e);
        }
        finally {
            try {
                carverInput.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error closing input stream after carving, file: " + file, ex); //NON-NLS
            }
        }
        
        // create a file object for the output
        File outputFile = new File(outputFolderPath, SCALPEL_OUTPUT_FILE_NAME);
        
        // parse the output
        List<CarvedFileMeta> output = Collections.<CarvedFileMeta>emptyList();
        try {
            output = ScalpelOutputParser.parse(outputFile);
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find scalpel output file.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "IOException while processing scalpel output file.", ex); //NON-NLS
        }
        
        return output;
    }
}
