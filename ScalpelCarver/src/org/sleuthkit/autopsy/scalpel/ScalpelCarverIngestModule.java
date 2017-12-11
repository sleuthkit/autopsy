/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.scalpel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.scalpel.jni.ScalpelOutputParser.CarvedFileMeta;
import org.sleuthkit.autopsy.scalpel.jni.ScalpelCarver;
import org.sleuthkit.autopsy.scalpel.jni.ScalpelException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskFileRange;
import org.sleuthkit.datamodel.Volume;

/**
 * Scalpel carving ingest module
 */
class ScalpelCarverIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(ScalpelCarverIngestModule.class.getName());
    private final String MODULE_OUTPUT_DIR_NAME = "ScalpelCarver"; //NON-NLS
    private String moduleOutputDirPath;
    private final String configFileName = "scalpel.conf"; //NON-NLS
    private String configFilePath;
    private boolean initialized = false;
    private ScalpelCarver carver;
    private IngestJobContext context;

    ScalpelCarverIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        // make sure this is Windows
        String os = System.getProperty("os.name"); //NON-NLS
        if (!os.startsWith("Windows")) { //NON-NLS
            String message = NbBundle.getMessage(this.getClass(), "ScalpelCarverIngestModule.startUp.exception.msg1");
            logger.log(Level.SEVERE, message);
            throw new IngestModuleException(message);
        }

        carver = new ScalpelCarver();
        if (!carver.isInitialized()) {
            String message = NbBundle.getMessage(this.getClass(), "ScalpelCarverIngestModule.startUp.exception.msg2");
            logger.log(Level.SEVERE, message);
            throw new IngestModuleException(message);
        }

        // make sure module output directory exists; create it if it doesn't
        moduleOutputDirPath = Case.getCurrentCase().getModuleDirectory()
                + File.separator + MODULE_OUTPUT_DIR_NAME;
        File moduleOutputDir = new File(moduleOutputDirPath);
        if (!moduleOutputDir.exists()) {
            if (!moduleOutputDir.mkdir()) {
                String message = NbBundle
                        .getMessage(this.getClass(), "ScalpelCarverIngestModule.startUp.exception.msg3");
                logger.log(Level.SEVERE, message);
                throw new IngestModuleException(message);
            }
        }

        // create path to scalpel config file in user's home directory
        configFilePath = PlatformUtil.getUserConfigDirectory()
                + File.separator + configFileName;

        // copy the default config file to the user's home directory if one
        // is not already there
        try {
            PlatformUtil.extractResourceToUserConfigDir(this.getClass(), configFileName, false);
        } catch (IOException ex) {
            String message = NbBundle.getMessage(this.getClass(), "ScalpelCarverIngestModule.startUp.exception.msg4");
            logger.log(Level.SEVERE, message, ex);
            throw new IngestModuleException(message, ex);
        }

        initialized = true;
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        ScalpelCarver.init();

        if (!initialized) {
            return ProcessResult.OK;
        }

        // only process files whose type is TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
        TSK_DB_FILES_TYPE_ENUM type = abstractFile.getType();
        if (type != TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
            return ProcessResult.OK;
        }

        // create the output directory for this run
        String scalpelOutputDirPath = moduleOutputDirPath + File.separator + abstractFile.getId();
        File scalpelOutputDir = new File(scalpelOutputDirPath);
        if (!scalpelOutputDir.exists()) {
            if (!scalpelOutputDir.mkdir()) {
                logger.log(Level.SEVERE, "Could not create Scalpel output directory: {0}", scalpelOutputDirPath); //NON-NLS
                return ProcessResult.OK;
            }
        }

        // find the ID of the parent FileSystem, Volume or Image
        long id = -1;
        Content parent = null;
        try {
            parent = abstractFile.getParent();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while trying to get parent of AbstractFile.", ex); //NON-NLS
        }
        while (parent != null) {
            if (parent instanceof FileSystem
                    || parent instanceof Volume
                    || parent instanceof Image) {
                id = parent.getId();
                break;
            }
            try {
                parent = parent.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Exception while trying to get parent of Content object.", ex); //NON-NLS
            }
        }

        // make sure we have a valid systemID
        if (id == -1) {
            logger.log(Level.SEVERE, "Could not get an ID for a FileSystem, Volume or Image for the given AbstractFile."); //NON-NLS
            return ProcessResult.OK;
        }

        // carve the AbstractFile
        List<CarvedFileMeta> output = null;
        try {
            output = carver.carve(abstractFile, configFilePath, scalpelOutputDirPath);
        } catch (ScalpelException ex) {
            logger.log(Level.SEVERE, "Error when attempting to carved data from AbstractFile with ID {0}", abstractFile.getId()); //NON-NLS
            return ProcessResult.OK;
        }

        // add a carved file to the DB for each file that scalpel carved
        SleuthkitCase db = Case.getCurrentCase().getSleuthkitCase();
        List<LayoutFile> carvedFiles = new ArrayList<LayoutFile>(output.size());
        for (CarvedFileMeta carvedFileMeta : output) {

            // calculate the byte offset of this carved file
            long byteOffset;
            try {
                byteOffset = abstractFile.convertToImgOffset(carvedFileMeta.getByteStart());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not calculate the image byte offset of AbstractFile ({0})", abstractFile.getName()); //NON-NLS
                break;
            }

            // get the size of the carved file
            long size = carvedFileMeta.getByteLength();

            // create the list of TskFileRange objects
            List<TskFileRange> data = new ArrayList<TskFileRange>();
            data.add(new TskFileRange(byteOffset, size, 0));

            // add the carved file
            try {
                carvedFiles.add(db.addCarvedFile(carvedFileMeta.getFileName(), size, id, data));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "There was a problem while trying to add a carved file to the database.", ex); //NON-NLS
            }
        }

        // get the IngestServices object
        IngestServices is = IngestServices.getInstance();

        // get the parent directory of the carved files
        Content carvedFileDir = null;
        if (!carvedFiles.isEmpty()) {
            try {
                carvedFileDir = carvedFiles.get(0).getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "There was a problem while trying to obtain the carved files directory.", ex); //NON-NLS
            }
        }

        // send a notification about the carved files directory
        if (carvedFileDir != null) {
            is.fireModuleContentEvent(new ModuleContentEvent(carvedFileDir));
        } else {
            logger.log(Level.SEVERE, "Could not obtain the carved files directory."); //NON-NLS
        }

        // reschedule carved files
        context.addFilesToJob(new ArrayList<AbstractFile>(carvedFiles));

        return ProcessResult.OK;
    }
    
    @Override
    public void shutDown() {        
    }
}
