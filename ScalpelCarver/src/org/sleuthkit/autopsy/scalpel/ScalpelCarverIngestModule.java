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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.scalpel.ScalpelOutputParser.CarvedFileMeta;
import org.sleuthkit.autopsy.scalpel.jni.ScalpelCarver;
import org.sleuthkit.autopsy.scalpel.jni.ScalpelException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskFileRange;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Scalpel carving ingest module
 */
public class ScalpelCarverIngestModule implements IngestModuleAbstractFile {
    
    private static final Logger logger = Logger.getLogger(ScalpelCarverIngestModule.class.getName());
    
    private static ScalpelCarverIngestModule instance;
    private final String MODULE_NAME = "Scalpel Carver";
    private final String MODULE_DESCRIPTION = "Carves files from unallocated space at ingest time.\nCarved files are reanalyzed and displayed in the directory tree.";
    private final String MODULE_VERSION = "1.0";
    private String configFileName = "scalpel.cfg";
    private String configFilePath;
    private boolean initialized = false;

    private ScalpelCarverIngestModule() { }
    
    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {
        
        if (!initialized) {
            return ProcessResult.OK;
        }
        
        // only process files whose type is TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
        TSK_DB_FILES_TYPE_ENUM type = abstractFile.getType();
        if (type != TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
            return ProcessResult.OK;
        }
        
        // get the TskFileRange object for this AbstractFile
        List<TskFileRange> fileData = Collections.EMPTY_LIST;
        try {
            fileData = abstractFile.getRanges();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "There was a problem while getting the TskFileRanges for this AbstractFile: " + abstractFile.getName(), ex);
        }
        if (fileData.size() > 1) {
            logger.log(Level.WARNING, "The AbstractFile to be parsed (" +
                    abstractFile.getName() + ") has more than one TskFileRange object; carving supports only processing one TskFileRange object at this time.");
        } else if (fileData.size() == 0) {
            logger.log(Level.SEVERE, "The AbstractFile to be parsed (" +
                    abstractFile.getName() + ") has only one TskFileRange object; aborting.");
            return ProcessResult.OK;
        }
        TskFileRange inputFileRange = fileData.get(0);
        
        // create a name for the scalpel output file
        String scalpelOutput = Case.getCurrentCase().getModulesOutputDirAbsPath()
                + File.pathSeparator + "scalpel-output-" + abstractFile.getId() + ".txt";
        
        // find the ID of the parent FileSystem or Volume
        long systemID = -1;
        Content parent = null;
        try {
            parent = abstractFile.getParent();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Exception while trying to get parent of AbstractFile.", ex);
        }
        while (parent != null) {
            if (parent instanceof FileSystem ||
                    parent instanceof VolumeSystem) {
                systemID = parent.getId();
                break;
            }
            try {
                parent = parent.getParent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Exception while trying to get parent of Content object.", ex);
            }
        }
        
        // make sure we have a valid systemID
        if (systemID == -1) {
            logger.log(Level.SEVERE, "Could not get an ID for a FileSystem or Volume for the given AbstractFile.");
            return ProcessResult.OK;
        }
        
        // carve the AbstractFile
        List<CarvedFileMeta> output = null;
        try {
            output = ScalpelCarver.getInstance().carve(abstractFile, configFilePath, scalpelOutput);
        } catch (ScalpelException ex) {
            java.util.logging.Logger.getLogger(ScalpelCarverIngestModule.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // add a carved file to the DB for each file that scalpel carved
        SleuthkitCase db = Case.getCurrentCase().getSleuthkitCase();
        List<LayoutFile> carvedFiles = new ArrayList<LayoutFile>();
        for (CarvedFileMeta carvedFileMeta : output) {
            
            // calculate the byte offset of this carved file
            long byteOffset = inputFileRange.getByteStart() + carvedFileMeta.getByteStart();
            
            // create the list of TskFileRange objects
            List<TskFileRange> data = new ArrayList<TskFileRange>();
            data.add(new TskFileRange(byteOffset, carvedFileMeta.getByteLength(), 0));
            
            // get the size of this carved file
            long size = carvedFileMeta.getByteLength();
            
            // add the carved file
            try {
                carvedFiles.add(db.addCarvedFile(carvedFileMeta.getFileName(), size, systemID, data));
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
    
    private String getScalpelConfigPath() throws IOException {
        
        // create path to scalpel config file in user's home directory
        String scalpelConfig = PlatformUtil.getUserConfigDirectory()
                + File.pathSeparator + configFileName;
        
        // copy the default config file to the user's home directory if one
        // is not already there
        PlatformUtil.extractResourceToUserConfigDir(this.getClass(), configFileName);
        
        return scalpelConfig;
    }

    @Override
    public void init(IngestModuleInit initContext) {
        
        // make sure this is Windows
        String os = System.getProperty("os.name");
        if (!os.startsWith("Windows")) {
            logger.log(Level.WARNING, "Scalpel carving module is not compatible with non-Windows OS's at this time.");
            return;
        }

        // get the path to the config file
        try {
            configFilePath = getScalpelConfigPath();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Could not obtain the path to the Scalpel configuration file.", ex);
            return;
        }
        
        initialized = true;
    }

    @Override
    public void complete() { }

    @Override
    public void stop() { }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
    }

    @Override
    public String getArguments() {
        return "";
    }

    @Override
    public void setArguments(String args) { }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public void saveSimpleConfiguration() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void saveAdvancedConfiguration() { }

    @Override
    public JPanel getSimpleConfiguration() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public JPanel getAdvancedConfiguration() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
