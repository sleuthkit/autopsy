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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

class AndroidIngestModule implements DataSourceIngestModule {

    private IngestJobContext context = null;
    private static final Logger logger = Logger.getLogger(AndroidIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        this.context = context;
    }

    @Override
    public IngestModule.ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        services.postMessage(IngestMessage.createMessage(IngestMessage.MessageType.INFO, AndroidModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "AndroidIngestModule.processing.startedAnalysis")));

        ArrayList<String> errors = new ArrayList<>();
        progressBar.switchToDeterminate(9);
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        List<AndroidAnalyzer> listOfAndroidAnalyzer = new ArrayList<>();

        listOfAndroidAnalyzer.add(new BrowserLocationAnalyzer());
        listOfAndroidAnalyzer.add(new CacheLocationAnalyzer());
        listOfAndroidAnalyzer.add(new CallLogAnalyzer());
        listOfAndroidAnalyzer.add(new ContactAnalyzer());
        listOfAndroidAnalyzer.add(new GoogleMapLocationAnalyzer());
        listOfAndroidAnalyzer.add(new TangoMessageAnalyzer());
        listOfAndroidAnalyzer.add(new TextMessageAnalyzer());
        listOfAndroidAnalyzer.add(new WWFMessageAnalyzer());

        progressBar.switchToDeterminate(listOfAndroidAnalyzer.size());
        int i = 0;

        for (AndroidAnalyzer androidAnalyzer : listOfAndroidAnalyzer) {
            try {
                List<AbstractFile> listOfDBAbstractFiles = new ArrayList<>();
                for (String databaseName : androidAnalyzer.getDatabaseNames()) {
                    listOfDBAbstractFiles.addAll(fileManager.findFiles(dataSource, databaseName));
                }
                for (AbstractFile dBAbstractFile : listOfDBAbstractFiles) {
                    if (dBAbstractFile.getSize() > 0) {
                        File jFile = new File(Case.getCurrentCase().getTempDirectory(), dBAbstractFile.getName());
                        ContentUtils.writeToFile(dBAbstractFile, jFile);
                        try {
                            if (androidAnalyzer.parsesDB()) {
                                String databasePath = jFile.toString();
                                //androidAnalyzer.findInDB(jFile.toString(), dBAbstractFile);
                                try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
                                    androidAnalyzer.findInDB(connection, dBAbstractFile);
                                }
                            } else {
                                androidAnalyzer.findInFile(jFile, dBAbstractFile);
                            }
                        } catch (Exception ex) {
                            errors.add("Error while executing " + androidAnalyzer.getClass().getName());
                        }
                    }
                }
                progressBar.progress(++i);
                if (context.dataSourceIngestIsCancelled()) {
                    return IngestModule.ProcessResult.OK;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to find database file ", ex); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error writing file to the disk ", ex); //NON-NLS
            }
        }

        postFinalMessageToInbox(errors);

        return IngestModule.ProcessResult.OK;
    }

    private void postFinalMessageToInbox(List<String> errors) {

        StringBuilder errorMessage = new StringBuilder();
        String errorMsgSubject;
        IngestMessage.MessageType msgLevel = IngestMessage.MessageType.INFO;
        if (errors.isEmpty() == false) {
            msgLevel = IngestMessage.MessageType.ERROR;
            errorMessage.append("Errors were encountered"); //NON-NLS
            for (String msg : errors) {
                errorMessage.append("<li>").append(msg).append("</li>\n"); //NON-NLS
            }
            errorMessage.append("</ul>\n"); //NON-NLS

            if (errors.size() == 1) {
                errorMsgSubject = "One error was found"; //NON-NLS
            } else {
                errorMsgSubject = "errors found: " + errors.size(); //NON-NLS
            }
        } else {
            errorMessage.append("No errors"); //NON-NLS
            errorMsgSubject = "No errors"; //NON-NLS
        }

        services.postMessage(IngestMessage.createMessage(msgLevel, AndroidModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "AndroidIngestModule.processing.finishedAnalysis",
                        errorMsgSubject), errorMessage.toString()));
    }
}
