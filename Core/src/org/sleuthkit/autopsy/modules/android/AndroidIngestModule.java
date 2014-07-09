/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.android;

import java.util.ArrayList;
import java.util.HashMap;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;

class AndroidIngestModule implements DataSourceIngestModule {

    private static final HashMap<Long, Long> fileCountsForIngestJobs = new HashMap<>();
    private IngestJobContext context = null;
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final Logger logger = Logger.getLogger(AndroidIngestModule.class.getName());
    private IngestServices services = IngestServices.getInstance();

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        services.postMessage(IngestMessage.createMessage(IngestMessage.MessageType.INFO, AndroidModuleFactory.getModuleName(), "Started Analysis"));

        ArrayList<String> errors = new ArrayList<>();
        progressBar.switchToDeterminate(9);

        try {
            ContactAnalyzer.findContacts();
            progressBar.progress(1);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Contacts");
        }

        try {
            CallLogAnalyzer.findCallLogs();
            progressBar.progress(2);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Call Logs");
        }

        try {
            TextMessageAnalyzer.findTexts();
            progressBar.progress(3);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Text Messages");
        }

        try {
            TangoMessageAnalyzer.findTangoMessages();
            progressBar.progress(4);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Tango Messages");
        }

        try {
            WWFMessageAnalyzer.findWWFMessages();
            progressBar.progress(5);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Words with Friends Messages");
        }

        try {
            GoogleMapLocationAnalyzer.findGeoLocations();
            progressBar.progress(6);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Google Map Locations");
        }

        try {
            BrowserLocationAnalyzer.findGeoLocations();
            progressBar.progress(7);
            if (context.isJobCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Browser Locations");
        }

        try {
            CacheLocationAnalyzer.findGeoLocations();
            progressBar.progress(8);
        } catch (Exception e) {
            errors.add("Error getting Cache Locations");
        }

        // create the final message for inbox
        StringBuilder errorMessage = new StringBuilder();
        String errorMsgSubject;
        IngestMessage.MessageType msgLevel = IngestMessage.MessageType.INFO;
        if (errors.isEmpty() == false) {
            msgLevel = IngestMessage.MessageType.ERROR;
            errorMessage.append("Errors were encountered");
            for (String msg : errors) {
                errorMessage.append("<li>").append(msg).append("</li>\n"); //NON-NLS
            }
            errorMessage.append("</ul>\n"); //NON-NLS

            if (errors.size() == 1) {
                errorMsgSubject = "One error was found";
            } else {
                errorMsgSubject = "errors found: " + errors.size();
            }
        } else {
            errorMessage.append("No errors");
            errorMsgSubject = "No errors";
        }

        services.postMessage(IngestMessage.createMessage(msgLevel, AndroidModuleFactory.getModuleName(), "Finished Analysis: " + errorMsgSubject, errorMessage.toString()));

        return IngestModule.ProcessResult.OK;
    }
}
