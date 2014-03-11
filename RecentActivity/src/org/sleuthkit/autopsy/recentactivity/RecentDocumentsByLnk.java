 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2013 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.recentactivity;

// imports
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.Collection;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.coreutils.JLnkParserException;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.*;

/**
 * Recent documents class that will extract recent documents in the form of 
 *.lnk files
 */
class RecentDocumentsByLnk extends Extract  {
    private static final Logger logger = Logger.getLogger(RecentDocumentsByLnk.class.getName());
    private IngestServices services;    
    final private static String MODULE_VERSION = "1.0";

    /**
     * Find the documents that Windows stores about recent documents and make artifacts.
     * @param dataSource
     * @param controller 
     */
    private void getRecentDocuments(Content dataSource, IngestDataSourceWorkerController controller) {
        
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> recentFiles = null;
        try {
            recentFiles = fileManager.findFiles(dataSource, "%.lnk", "Recent");
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error searching for .lnk files.");
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.getRecDoc.errMsg.errGetLnkFiles",
                                        this.getName()));
            return;
        }

        if (recentFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any recent files.");
            return;
        }
        
        dataFound = true;
        for (AbstractFile recentFile : recentFiles) {
            if (controller.isCancelled()) {
                break;
            }
            
            if (recentFile.getSize() == 0) {
                continue;
            }
            JLNK lnk = null;
            JLnkParser lnkParser = new JLnkParser(new ReadContentInputStream(recentFile), (int) recentFile.getSize());
            try {
                lnk = lnkParser.parse();
            } catch (JLnkParserException e) {
                //TODO should throw a specific checked exception
                boolean unalloc = recentFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC) 
                        || recentFile.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
                if (unalloc == false) {
                    logger.log(Level.SEVERE, "Error lnk parsing the file to get recent files" + recentFile, e);
                    this.addErrorMessage(
                            NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.getRecDoc.errParsingFile",
                                                this.getName(), recentFile.getName()));
                }
                continue;
            }
           
            Collection<BlackboardAttribute> bbattributes = new ArrayList<BlackboardAttribute>();
            String path = lnk.getBestPath();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH.getTypeID(),
                                                     NbBundle.getMessage(this.getClass(),
                                                                         "RecentDocumentsByLnk.parentModuleName.noSpace"),
                                                     path));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID(),
                                                     NbBundle.getMessage(this.getClass(),
                                                                         "RecentDocumentsByLnk.parentModuleName.noSpace"),
                                                     Util.findID(dataSource, path)));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID(),
                                                     NbBundle.getMessage(this.getClass(),
                                                                         "RecentDocumentsByLnk.parentModuleName.noSpace"),
                                                     recentFile.getCrtime()));
            this.addArtifact(ARTIFACT_TYPE.TSK_RECENT_OBJECT, recentFile, bbattributes);
        }
        services.fireModuleDataEvent(new ModuleDataEvent(
                NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.parentModuleName"),
                BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT));
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }
    
    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        dataFound = false;
        this.getRecentDocuments(dataSource, controller);
    }
   
    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        services = IngestServices.getDefault();
    }

    @Override
    public void complete() {
    }

    @Override
    public void stop() {
        //call regular cleanup from complete() method
        complete();
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.getDesc.text");
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
