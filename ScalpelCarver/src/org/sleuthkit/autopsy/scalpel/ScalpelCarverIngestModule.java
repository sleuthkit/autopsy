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
package org.sleuthkit.autopsy.scalpel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.PipelineContext;
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
public class ScalpelCarverIngestModule { // extends IngestModuleAbstractFile { // disable autodiscovery for now  {
    
    private static final Logger logger = Logger.getLogger(ScalpelCarverIngestModule.class.getName());
    
    private static ScalpelCarverIngestModule instance;
    private final String MODULE_NAME = "Scalpel Carver";
    private final String MODULE_DESCRIPTION = "Carves files from unallocated space at ingest time.\nCarved files are reanalyzed and displayed in the directory tree.";
    private final String MODULE_VERSION = "1.0";
    private final String MODULE_OUTPUT_DIR_NAME = "ScalpelCarver";
    private String moduleOutputDirPath;
    private String configFileName = "scalpel.conf";
    private String configFilePath;
    private boolean initialized = false;
    private ScalpelCarver carver;

    private ScalpelCarverIngestModule() {
        ScalpelCarver.init();
    }
    
   // @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {
        
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
                logger.log(Level.SEVERE, "Could not create Scalpel output directory: " + scalpelOutputDirPath);
                return ProcessResult.OK;
            }
        }
        
        // find the ID of the parent FileSystem, Volume or Image
        long id = -1;
        Content parent = null;
        try {
            parent = abstractFile.getParent();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while trying to get parent of AbstractFile.", ex);
        }
        while (parent != null) {
            if (parent instanceof FileSystem ||
                    parent instanceof Volume ||
                    parent instanceof Image) {
                id = parent.getId();
                break;
            }
            try {
                parent = parent.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Exception while trying to get parent of Content object.", ex);
            }
        }
        
        // make sure we have a valid systemID
        if (id == -1) {
            logger.log(Level.SEVERE, "Could not get an ID for a FileSystem, Volume or Image for the given AbstractFile.");
            return ProcessResult.OK;
        }
        
        // carve the AbstractFile
        List<CarvedFileMeta> output = null;
        try {
            output = carver.carve(abstractFile, configFilePath, scalpelOutputDirPath);
        } catch (ScalpelException ex) {
            logger.log(Level.SEVERE, "Error when attempting to carved data from AbstractFile with ID " + abstractFile.getId());
            return ProcessResult.OK;
        }
        

        // get the image's size
        long imageSize = Long.MAX_VALUE;
        try {
            
            imageSize = abstractFile.getImage().getSize();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Could not obtain the image's size.");
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
                logger.log(Level.SEVERE, "Could not calculate the image byte offset of AbstractFile (" + abstractFile.getName() + ")");
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
                logger.log(Level.SEVERE, "There was a problem while trying to add a carved file to the database.", ex);
            }
        }
        
        // get the IngestServices object
        IngestServices is = IngestServices.getDefault();

        // get the parent directory of the carved files
        Content carvedFileDir = null;
        if (!carvedFiles.isEmpty()) {
            try {
                carvedFileDir = carvedFiles.get(0).getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "There was a problem while trying to obtain the carved files directory.", ex);
            }
        }

        // send a notification about the carved files directory
        if (carvedFileDir != null) {
            is.fireModuleContentEvent(new ModuleContentEvent(carvedFileDir));
        } else {
            logger.log(Level.SEVERE, "Could not obtain the carved files directory.");
        }
        
        // reschedule carved files
        for (LayoutFile carvedFile : carvedFiles) {
            is.scheduleFile(carvedFile, pipelineContext);
        }
        
        return ProcessResult.OK;
    }
    

    public static ScalpelCarverIngestModule getDefault() {
        if (instance == null) {
            synchronized (ScalpelCarverIngestModule.class) {
                if (instance == null) {
                    instance = new ScalpelCarverIngestModule();
                }
            }
        }
        return instance;
    }
    
  //  @Override
    public void init(IngestModuleInit initContext) {
        
        // make sure this is Windows
        String os = System.getProperty("os.name");
        if (!os.startsWith("Windows")) {
            logger.log(Level.WARNING, "Scalpel carving module is not compatible with non-Windows OS's at this time.");
            return;
        }
        

        carver = new ScalpelCarver();
        if (! carver.isInitialized()) {
            logger.log(Level.SEVERE, "Error initializing scalpel carver. ");
            return;
        }
        
        // make sure module output directory exists; create it if it doesn't
        moduleOutputDirPath = Case.getCurrentCase().getModulesOutputDirAbsPath() +
                File.separator + MODULE_OUTPUT_DIR_NAME;
        File moduleOutputDir = new File(moduleOutputDirPath);
        if (!moduleOutputDir.exists()) {
            if (!moduleOutputDir.mkdir()) {
                logger.log(Level.SEVERE, "Could not create the output directory for the Scalpel module.");
                return;
            }
        }
        
        // create path to scalpel config file in user's home directory
        configFilePath = PlatformUtil.getUserConfigDirectory()
                + File.separator + configFileName;
        
        // copy the default config file to the user's home directory if one
        // is not already there
        try {
            PlatformUtil.extractResourceToUserConfigDir(this.getClass(), configFileName);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not obtain the path to the Scalpel configuration file.", ex);
            return;
        }
        
        initialized = true;
    }

   // @Override
    public void complete() { }

   // @Override
    public void stop() { }

   // @Override
    public String getName() {
        return MODULE_NAME;
    }

  //  @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

   // @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

   // @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    



    
}
