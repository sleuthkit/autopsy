 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.Content;

/**
 * Recent activity image ingest module
 *
 */
public final class RAImageIngestModule extends IngestModuleDataSource {

    private static final Logger logger = Logger.getLogger(RAImageIngestModule.class.getName());
    private static RAImageIngestModule defaultInstance = null;
    private IngestServices services;
    private static int messageId = 0;
    private StringBuilder subCompleted = new StringBuilder();
    private ArrayList<Extract> modules;
    private List<Extract> browserModules;
    final private static String MODULE_VERSION = Version.getVersion();

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public RAImageIngestModule() {
    }


    @Override
    public void process(PipelineContext<IngestModuleDataSource>pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Started " + dataSource.getName()));
        
        controller.switchToDeterminate(modules.size());
        controller.progress(0);
        ArrayList<String> errors = new ArrayList<>();
        
        for (int i = 0; i < modules.size(); i++) {
            Extract module = modules.get(i);
            if (controller.isCancelled()) {
                logger.log(Level.INFO, "Recent Activity has been canceled, quitting before {0}", module.getName());
                break;
            }
            
            try {
                module.process(pipelineContext, dataSource, controller);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception occurred in " + module.getName(), ex);
                subCompleted.append(module.getName()).append(" failed - see log for details <br>");
                errors.add(module.getName() + " had errors -- see log");
            }
            controller.progress(i + 1);
            errors.addAll(module.getErrorMessages());
        }
        
        // create the final message for inbox
        StringBuilder errorMessage = new StringBuilder();
        String errorMsgSubject;
        MessageType msgLevel = MessageType.INFO;
        if (errors.isEmpty() == false) {
            msgLevel = MessageType.ERROR;
            errorMessage.append("<p>Errors encountered during analysis: <ul>\n");
            for (String msg : errors) {
                errorMessage.append("<li>").append(msg).append("</li>\n");
            }
            errorMessage.append("</ul>\n");

            if (errors.size() == 1) {
                errorMsgSubject = "1 error found";
            } else {
                errorMsgSubject = errors.size() + " errors found";
            }
        } else {
            errorMessage.append("<p>No errors encountered.</p>");
            errorMsgSubject = "No errors reported";
        }
        final IngestMessage msg = IngestMessage.createMessage(++messageId, msgLevel, this, "Finished " + dataSource.getName()+ " - " + errorMsgSubject, errorMessage.toString());
        services.postMessage(msg);
        
        StringBuilder historyMsg = new StringBuilder();
        historyMsg.append("<p>Browser Data on ").append(dataSource.getName()).append(":<ul>\n");
        for (Extract module : browserModules) {
            historyMsg.append("<li>").append(module.getName());
            historyMsg.append(": ").append((module.foundData()) ? " Found." : " Not Found.");
            historyMsg.append("</li>");
        }
        historyMsg.append("</ul>");
        final IngestMessage inboxMsg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, dataSource.getName() + " - Browser Results", historyMsg.toString());
        services.postMessage(inboxMsg);
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());
        
        // close modules 
        for (int i = 0; i < modules.size(); i++) {
            Extract module = modules.get(i);
            try {
                module.complete();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception occurred when completing " + module.getName(), ex);
                subCompleted.append(module.getName()).append(" failed to complete - see log for details <br>");
            }
        }

        //module specific cleanup due to completion here
    }

    @Override
    public String getName() {
        return "Recent Activity";
    }

    @Override
    public String getDescription() {
        return "Extracts recent user activity, such as Web browsing, recently used documents and installed programs.";
    }

    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        modules = new ArrayList<>();
        browserModules = new ArrayList();
        logger.log(Level.INFO, "init() {0}", this.toString());
        services = IngestServices.getDefault();

        final Extract registry = new ExtractRegistry();
        final Extract iexplore = new ExtractIE();
        final Extract recentDocuments= new RecentDocumentsByLnk(); 
        final Extract chrome = new Chrome();
        final Extract firefox = new Firefox();
        final Extract SEUQA = new SearchEngineURLQueryAnalyzer();

        modules.add(chrome);
        modules.add(firefox);
        modules.add(iexplore);
        modules.add(recentDocuments);
        // this needs to run after the web browser modules
        modules.add(SEUQA);
        
        // this runs last because it is slowest
        modules.add(registry);
        
        browserModules.add(chrome);
        browserModules.add(firefox);
        browserModules.add(iexplore);

        for (Extract module : modules) {
            try {
                module.init(initContext);
            } catch (IngestModuleException ex) {
                logger.log(Level.SEVERE, "Exception during init() of " + module.getName(), ex);
            }
        }
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "RAImageIngetModule::stop()");
        for (Extract module : modules) {
            try {
                module.stop();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception during stop() of " + module.getName(), ex);
            }
        }
        logger.log(Level.INFO, "Recent Activity processes has been shutdown.");
    }


    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    /**
     * Get the temp path for a specific sub-module in recent activity.  Will create the dir if it doesn't exist.
     * @param a_case Case that directory is for
     * @param mod Module name that will be used for a sub folder in the temp folder to prevent  name collisions
     * @return Path to directory
     */
    protected static String getRATempPath(Case a_case, String mod) {
        String tmpDir = a_case.getTempDirectory() + File.separator + "RecentActivity" + File.separator + mod;
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }
    
    /**
     * Get the output path for a specific sub-module in recent activity.  Will create the dir if it doesn't exist.
     * @param a_case Case that directory is for
     * @param mod Module name that will be used for a sub folder in the temp folder to prevent  name collisions
     * @return Path to directory
     */
    protected static String getRAOutputPath(Case a_case, String mod) {
        String tmpDir = a_case.getModulesOutputDirAbsPath() + File.separator + "RecentActivity" + File.separator + mod;
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpDir;
    }
}
