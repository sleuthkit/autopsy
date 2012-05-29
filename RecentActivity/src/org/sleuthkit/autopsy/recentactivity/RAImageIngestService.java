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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceImage;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.FileSystem;

/**
 * Recent activity image ingest service
 *
 */
public final class RAImageIngestService implements IngestServiceImage {

    private static final Logger logger = Logger.getLogger(RAImageIngestService.class.getName());
    private static RAImageIngestService defaultInstance = null;
    private IngestManagerProxy managerProxy;
    private static int messageId = 0;
    private ArrayList<String> errors = null;
    private StringBuilder subCompleted = new StringBuilder();

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
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Started " + image.getName()));
        Case currentCase = Case.getCurrentCase(); // get the most updated case
        SleuthkitCase sCurrentCase = currentCase.getSleuthkitCase();
        //long imageId = image.getId();
        Collection<FileSystem> imageFS = sCurrentCase.getFileSystems(image);
        List<String> fsIds = new LinkedList<String>();
        for (FileSystem img : imageFS) {
            Long tempID = img.getId();
            fsIds.add(tempID.toString());
        }

        try {
            controller.switchToDeterminate(4);
            controller.progress(0);

            if (controller.isCancelled() == false) {
                ExtractRegistry eree = new ExtractRegistry();
                eree.getregistryfiles(fsIds, controller);
                controller.progress(1);
                subCompleted.append("Registry extraction complete. <br>");
            }
            if (controller.isCancelled() == false) {
                Firefox ffre = new Firefox();
                ffre.process(fsIds, controller);
                controller.progress(2);
                subCompleted.append("Firefox extraction complete. <br>");
                if(ffre.errorMessages != null){
                errors.addAll(ffre.errorMessages);
                }
            }
            if (controller.isCancelled() == false) {
                Chrome chre = new Chrome();
                chre.process(fsIds, controller);
                controller.progress(3);
                subCompleted.append("Chrome extraction complete. <br>");
                if(chre.errorMessages != null){
                errors.addAll(chre.errorMessages);
                }
            }
            if (controller.isCancelled() == false) {
                ExtractIE eere = new ExtractIE();
                eere.process(fsIds, controller);
                eere.parsePascoResults();
                if(eere.errorMessages != null){
                errors.addAll(eere.errorMessages);
                }
                subCompleted.append( "Internet Explorer extraction complete. <br>");
                controller.progress(4);
            }


        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting recent activity", e);
            managerProxy.postMessage(IngestMessage.createErrorMessage(++messageId, this, "Error extracting recent activity data", null));
        }

    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete() " + this.toString());
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append(subCompleted).append("Recent activity ingest completed!");
        if (errors != null) {
            errorMessage.append("<br>There were some errors extracting the data: <br>");
            for (String msg : errors) {
                final IngestMessage error = IngestMessage.createMessage(++messageId, MessageType.INFO, this, msg + "<br>");
                managerProxy.postMessage(error);
            }
        }else
        {
            errorMessage.append("<br> No errors encountered.");
        }
        final IngestMessage msg = IngestMessage.createMessage(++messageId, MessageType.INFO, this, errorMessage.toString());
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

        //service specific initialization here

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //service specific cleanup due to interruption here
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
