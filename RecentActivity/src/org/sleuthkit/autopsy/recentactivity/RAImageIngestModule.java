 /*
 *
 * Autopsy Forensic Browser
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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleImage;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.datamodel.Image;

/**
 * Recent activity image ingest module
 *
 */
public final class RAImageIngestModule implements IngestModuleImage {

    private static final Logger logger = Logger.getLogger(RAImageIngestModule.class.getName());
    private static RAImageIngestModule defaultInstance = null;
    private IngestServices services;
    private static int messageId = 0;
    private ArrayList<String> errors = new ArrayList<String>();
    private StringBuilder subCompleted = new StringBuilder();
    private ExtractRegistry registry = null;
    private Firefox firefox = null;
    private Chrome chrome = null;
    private ExtractIE ie = null;
    private SearchEngineURLQueryAnalyzer usq = null;
    
    final public static String MODULE_VERSION = "1.0";
    
    private String args;

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public RAImageIngestModule() {
    }

    //default instance used for module registration
    public static synchronized RAImageIngestModule getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new RAImageIngestModule();
        }
        return defaultInstance;
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        //logger.log(Level.INFO, "process() " + this.toString());
        List<Extract> modules = new ArrayList<Extract>();
        modules.add(registry);
        modules.add(firefox);
        modules.add(chrome);
        modules.add(ie);
        modules.add(usq);
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Started " + image.getName()));
        controller.switchToDeterminate(modules.size());
        controller.progress(0);
        
        for(int i = 0; i < modules.size(); i++) {
            Extract module = modules.get(i);
            try {
                module.process(image, controller);
                subCompleted.append(module.getName()).append(" complete <br>");
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Exception occurred in " + module.getName(), ex);
                subCompleted.append(module.getName()).append(" failed - see log for details <br>");
            }
            controller.progress(i+1);
            errors.addAll(module.getErrorMessages());
        }
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());
        StringBuilder errorMessage = new StringBuilder();
        String errorsFound = "";
        errorMessage.append(subCompleted);
        int i = 0;
        if (!errors.isEmpty()) {
            errorMessage.append("<br>There were some errors extracting the data: <br>");
            for (String msg : errors) {
                i++;
                final IngestMessage error = IngestMessage.createMessage(++messageId, MessageType.INFO, this, msg + "<br>");
                services.postMessage(error);
            }
            
            if (i == 1) {
                errorsFound = i + " error found";
            }
            else {
                errorsFound = i + " errors found";
            }
        }else
        {
            errorMessage.append("<br> No errors encountered.");
            errorsFound = "No errors reported";
        }
        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Completed - " + errorsFound, errorMessage.toString());
        services.postMessage(msg);

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
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init() " + this.toString());
        services = IngestServices.getDefault();
        
        ie = new ExtractIE();
        ie.init(initContext);
        
        chrome = new Chrome();
        chrome.init(initContext);
        
        registry = new ExtractRegistry();
        registry.init(initContext);
        
        firefox = new Firefox();
        firefox.init(initContext);
        
        usq = new SearchEngineURLQueryAnalyzer();
        usq.init(initContext);
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "RAImageIngetModule::stop()");
        //Order Matters
        //ExtractRegistry stop        
        this.registry.stop();
        //ExtractIE stop
        this.ie.stop();
        logger.log(Level.INFO, "Recent Activity processes properly shutdown.");
    }

    @Override
    public ModuleType getType() {
        return ModuleType.Image;
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
    public boolean hasSimpleConfiguration() {
        return false;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return null;
    }

    @Override
    public void saveAdvancedConfiguration() {
    }

    @Override
    public void saveSimpleConfiguration() {
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
}
