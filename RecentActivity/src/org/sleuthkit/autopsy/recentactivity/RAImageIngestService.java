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
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.datamodel.Image;

/**
 * Recent activity image ingest service
 *
 */
public final class RAImageIngestService implements IngestServiceImage {

    private static final Logger logger = Logger.getLogger(RAImageIngestService.class.getName());
    private static RAImageIngestService defaultInstance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;
    private ArrayList<String> errors = new ArrayList<String>();
    private StringBuilder subCompleted = new StringBuilder();
    private ExtractRegistry eree = null;
    private Firefox ffre = null;
    private Chrome chre = null;
    private ExtractIE eere = null;
    private SearchEngineURLQueryAnalyzer usq = null;

    //public constructor is required
    //as multiple instances are created for processing multiple images simultenously
    public RAImageIngestService() {
    }

    //default instance used for service registration
    public static synchronized RAImageIngestService getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new RAImageIngestService();
        }
        return defaultInstance;
    }

    @Override
    public void process(Image image, IngestImageWorkerController controller) {
        //logger.log(Level.INFO, "process() " + this.toString());
        List<Extract> modules = new ArrayList<Extract>();
        modules.add(eree);
        modules.add(ffre);
        modules.add(chre);
        modules.add(eere);
        modules.add(usq);
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Started " + image.getName()));
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
                managerProxy.postMessage(error);
            }
            errorsFound = i + " errors found!";
        }else
        {
            errorMessage.append("<br> No errors encountered.");
            errorsFound = "No errors reported";
        }
        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Completed - " + errorsFound, errorMessage.toString());
        managerProxy.postMessage(msg);

        //service specific cleanup due to completion here
    }

    @Override
    public String getName() {
        return "Recent Activity";
    }

    @Override
    public String getDescription() {
        return "Extracts recent user activity, such as Internet browsing, recently used documents and installed programs.";
    }

    @Override
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init() " + this.toString());
        this.managerProxy = managerProxy;
        this.eere = new ExtractIE();
        this.chre = new Chrome();
        this.eree = new ExtractRegistry();
        this.ffre = new Firefox();
        this.usq = new SearchEngineURLQueryAnalyzer();

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "RAImageIngetService::stop()");
        //Order Matters
        //ExtractRegistry stop        
        this.eree.stop();
        //ExtractIE stop
        this.eere.stop();
        logger.log(Level.INFO, "Recent Activity processes properly shutdown.");
    }

    @Override
    public ServiceType getType() {
        return ServiceType.Image;
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
