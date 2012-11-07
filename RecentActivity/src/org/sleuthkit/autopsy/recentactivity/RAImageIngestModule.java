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
    private ArrayList<Extract> modules;
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
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Started " + image.getName()));
        controller.switchToDeterminate(modules.size());
        controller.progress(0);
        for (int i = 0; i < modules.size(); i++) {
            Extract module = modules.get(i);
            if (controller.isCancelled()) {
                logger.log(Level.INFO, "Recent Activity has been canceled, quitting before " + module.getName());
                break;
            }
            try {
                module.process(image, controller);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception occurred in " + module.getName(), ex);
                subCompleted.append(module.getName()).append(" failed - see log for details <br>");
            }
            controller.progress(i + 1);
            errors.addAll(module.getErrorMessages());
        }
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());
        StringBuilder errorMessage = new StringBuilder();
        String errorsFound = "";

        for (int i = 0; i < modules.size(); i++) {
            Extract module = modules.get(i);
            try {
                module.complete();
                subCompleted.append(module.getName()).append(" complete <br>");
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception occurred when completing " + module.getName(), ex);
            }
        }

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
            } else {
                errorsFound = i + " errors found";
            }
        } else {
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
        modules = new ArrayList<Extract>();
        logger.log(Level.INFO, "init() " + this.toString());
        services = IngestServices.getDefault();

        final Extract registry = new ExtractRegistry();
        final Extract iexplore = new ExtractIE();
        final Extract chrome = new Chrome();
        final Extract firefox = new Firefox();
        final Extract SEUQA = new SearchEngineURLQueryAnalyzer();

        modules.add(registry);
        modules.add(iexplore);
        modules.add(chrome);
        modules.add(firefox);
        modules.add(SEUQA);

        for (Extract module : modules) {
            try {
                module.init(initContext);
            } catch (Exception ex) {
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
