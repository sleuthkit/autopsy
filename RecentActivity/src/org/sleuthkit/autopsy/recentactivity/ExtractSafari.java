/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extract the bookmarks, cookies, downloads and history from Safari
 *
 */
final class ExtractSafari extends Extract {

    private final IngestServices services = IngestServices.getInstance();

    // visit_time uses an epoch of Jan 1, 2001 thus the addition of 978307200
    private static final String SAFARI_HISTORY_QUERY = "SELECT url, title, visit_time + 978307200 as time FROM 'history_items' JOIN history_visits ON history_item = history_items.id;";

    private static final String SAFARI_HISTORY_FILE_NAME = "History.db";
    private static final String SAFARI_DATABASE_EXT = ".db";

    private static final String SAFARI_HEAD_URL = "url";
    private static final String SAFARI_HEAD_TITLE = "title";
    private static final String SAFARI_HEAD_TIME = "time";

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    @Messages({
        "ExtractSafari_Module_Name=Safari",
        "ExtractSafari_Error_Getting_History=An error occurred while processing Safari history files."
    })

    ExtractSafari() {

    }

    @Override
    protected String getName() {
        return Bundle.ExtractSafari_Module_Name();
    }

    @Override
    void process(Content dataSource, IngestJobContext context) {
        setFoundData(false);

        try {
            processHistoryDB(dataSource, context);
        } catch (IOException | TskCoreException ex) {
            this.addErrorMessage(Bundle.ExtractSafari_Error_Getting_History());
            logger.log(Level.SEVERE, "Exception thrown while processing history file: " + ex); //NON-NLS
        }
    }

    /**
     * Finds the all of the history.db files in the case looping through them to
     * find all of the history artifacts
     *
     * @throws TskCoreException
     * @throws IOException
     */
    private void processHistoryDB(Content dataSource, IngestJobContext context) throws TskCoreException, IOException {
        FileManager fileManager = getCurrentCase().getServices().getFileManager();

        List<AbstractFile> historyFiles = fileManager.findFiles(dataSource, SAFARI_HISTORY_FILE_NAME);

        if (historyFiles == null || historyFiles.isEmpty()) {
            return;
        }

        this.setFoundData(true);

        for (AbstractFile historyFile : historyFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            getHistory(context, historyFile);
        }
    }

    /**
     * Creates a temporary copy of historyFile and creates a list of
     * BlackboardArtifacts for the history information in the file.
     *
     * @param historyFile AbstractFile version of the history file from the case
     * @throws TskCoreException
     * @throws IOException
     */
    private void getHistory(IngestJobContext context, AbstractFile historyFile) throws TskCoreException, IOException {
        if (historyFile.getSize() == 0) {
            return;
        }

        Path tempHistoryPath = Paths.get(RAImageIngestModule.getRATempPath(
                getCurrentCase(), getName()), historyFile.getName() + historyFile.getId() + SAFARI_DATABASE_EXT);
        File tempHistoryFile = tempHistoryPath.toFile();

        try {
            ContentUtils.writeToFile(historyFile, tempHistoryFile, context::dataSourceIngestIsCancelled);
        } catch (IOException ex) {
            throw new IOException("Error writingToFile: " + historyFile, ex); //NON-NLS
        }

        try {
            Collection<BlackboardArtifact> bbartifacts = getHistoryArtifacts(historyFile, tempHistoryPath);
            if (!bbartifacts.isEmpty()) {
                services.fireModuleDataEvent(new ModuleDataEvent(
                        RecentActivityExtracterModuleFactory.getModuleName(),
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY, bbartifacts));
            }
        } finally {
            tempHistoryFile.delete();
        }
    }

    /**
     * Queries the history db for the history information creating a list of
     * BlackBoardArtifact for each row returned from the db.
     *
     * @param origFile AbstractFile of the history file from the case
     * @param tempFilePath Path to temporary copy of the history db
     * @return Blackboard Artifacts for the history db
     * @throws TskCoreException
     */
    private Collection<BlackboardArtifact> getHistoryArtifacts(AbstractFile origFile, Path tempFilePath) throws TskCoreException {
        List<HashMap<String, Object>> historyList = this.dbConnect(tempFilePath.toString(), SAFARI_HISTORY_QUERY);

        if (historyList == null || historyList.isEmpty()) {
            return null;
        }

        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        for (HashMap<String, Object> row : historyList) {
            String url = row.get(SAFARI_HEAD_URL).toString();
            String title = row.get(SAFARI_HEAD_TITLE).toString();
            Long time = (Double.valueOf(row.get(SAFARI_HEAD_TIME).toString())).longValue();

            BlackboardArtifact bbart = origFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY);
            bbart.addAttributes(createHistoryAttribute(url, time, null, title,
                    this.getName(), NetworkUtils.extractDomain(url), null));
            bbartifacts.add(bbart);
        }

        return bbartifacts;
    }
}
