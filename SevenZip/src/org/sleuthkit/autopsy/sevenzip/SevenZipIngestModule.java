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
package org.sleuthkit.autopsy.sevenzip;

import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 7Zip ingest module Extracts supported archives, adds extracted DerivedFiles,
 * reschedules extracted DerivedFiles for ingest.
 *
 * Updates datamodel / directory tree with new files.
 */
public final class SevenZipIngestModule implements IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(SevenZipIngestModule.class.getName());
    public static final String MODULE_NAME = "7Zip";
    public static final String MODULE_DESCRIPTION = "Extracts archive files, add new files, reschedules them to current ingest and populates directory tree with new files.";
    final public static String MODULE_VERSION = "1.0";
    private String args;
    private IngestServices services;
    private volatile int messageID = 0;
    private boolean processedFiles;
    private SleuthkitCase caseHandle = null;
    private boolean initialized = false;
    private static SevenZipIngestModule instance = null;
    //TODO use content type detection instead of extensions
    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar",};

    //private constructor to ensure singleton instance 
    private SevenZipIngestModule() {
    }

    /**
     * Returns singleton instance of the module, creates one if needed
     *
     * @return instance of the module
     */
    public static synchronized SevenZipIngestModule getDefault() {
        if (instance == null) {
            instance = new SevenZipIngestModule();
        }
        return instance;
    }

    @Override
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init()");
        services = IngestServices.getDefault();
        initialized = false;


        try {
            SevenZip.initSevenZipFromPlatformJAR();
            String platform = SevenZip.getUsedPlatform();
            logger.log(Level.INFO, "7-Zip-JBinding library was initialized on supported platform: " + platform);
        } catch (SevenZipNativeInitializationException e) {
            logger.log(Level.SEVERE, "Error initializing 7-Zip-JBinding library", e);
            MessageNotifyUtil.Notify.error("Error initializing " + MODULE_NAME, "Could not initialize 7-ZIP library");
            return;
        }


        initialized = true;
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {

        if (initialized == false) //error initializing indexing/Solr
        {
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: " + abstractFile.getName());
            return ProcessResult.OK;
        }

        if (abstractFile.isFile() == false || !isSupported(abstractFile)) {
            //do not process dirs and files that are not supported
            return ProcessResult.OK;
        }

        //check if already has derived files, skip
        try {
            if (abstractFile.hasChildren()) {
                logger.log(Level.INFO, "File already has been processed as it has children, skipping: " + abstractFile.getName());
                return ProcessResult.OK;
            }
        } catch (TskCoreException e) {
            logger.log(Level.INFO, "Error checking if file already has been processed, skipping: " + abstractFile.getName());
            return ProcessResult.OK;
        }


        logger.log(Level.INFO, "Processing with 7ZIP: " + abstractFile.getName());

        //process archive recursively, extract to case output folder 

        //add derived files, send event

        //process, return error if occurred

        return ProcessResult.OK;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        if (initialized == false) {
            return;
        }
        
       

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");


    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
    }

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
    }

    @Override
    public void saveAdvancedConfiguration() {
    }

    @Override
    public JPanel getSimpleConfiguration() {
        return null;
    }

    @Override
    public JPanel getAdvancedConfiguration() {
        return null;
    }

    public boolean isSupported(AbstractFile file) {
        String fileNameLower = file.getName().toLowerCase();
        int dotI = fileNameLower.lastIndexOf(".");
        if (dotI == -1 || dotI == fileNameLower.length() - 1) {
            return false; //no extension
        }
        final String extension = fileNameLower.substring(dotI + 1);
        for (int i = 0; i < SUPPORTED_EXTENSIONS.length; ++i) {
            if (extension.equals(SUPPORTED_EXTENSIONS[i])) {
                return true;
            }
        }
        return false;
    }
}
