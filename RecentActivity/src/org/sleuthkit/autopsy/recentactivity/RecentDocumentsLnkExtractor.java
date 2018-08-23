/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.coreutils.JLnkParserException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_RECENT_OBJECT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Recent documents class that will extract recent documents in the form of .lnk
 * files
 */
class RecentDocumentsLnkExtractor extends Extract {

    private static final Logger logger = Logger.getLogger(RecentDocumentsLnkExtractor.class.getName());

    private static final String PARENT_MODULE_NAME = NbBundle.getMessage(RecentDocumentsLnkExtractor.class,
            "RecentDocumentsByLnk.parentModuleName.noSpace");
    private Content dataSource;
    private IngestJobContext context;

    @Override
    protected String getModuleName() {
        return "lnk files";
    }

    @Override
    public void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        this.getRecentDocuments();
    }

    /**
     * Find the documents that Windows stores about recent documents and make
     * artifacts.
     *
     * @param dataSource
     * @param controller
     */
    private void getRecentDocuments() {
        List<AbstractFile> recentFiles;
        try {
            recentFiles = fileManager.findFiles(dataSource, "%.lnk", "Recent"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error searching for .lnk files."); //NON-NLS
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.getRecDoc.errMsg.errGetLnkFiles",
                            this.getModuleName()));
            return;
        }

        if (recentFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any recent files."); //NON-NLS
            return;
        }

        dataFound = true;

        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        for (AbstractFile recentFile : recentFiles) {
            if (context.dataSourceIngestIsCancelled()) {
                break;
            }

            if (recentFile.getSize() == 0) {
                continue;
            }
            JLNK lnk;
            JLnkParser lnkParser = new JLnkParser(new ReadContentInputStream(recentFile), (int) recentFile.getSize());
            try {
                lnk = lnkParser.parse();
            } catch (JLnkParserException e) {
                //TODO should throw a specific checked exception
                boolean unalloc = recentFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)
                                  || recentFile.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC);
                if (unalloc == false) {
                    logger.log(Level.WARNING, "Error lnk parsing the file to get recent files {0}", recentFile); //NON-NLS
                }
                continue;
            }
            String path = lnk.getBestPath();
            Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                    new BlackboardAttribute(
                            TSK_PATH, PARENT_MODULE_NAME,
                            path),
                    new BlackboardAttribute(
                            TSK_PATH_ID, PARENT_MODULE_NAME,
                            Util.findID(dataSource, path)),
                    new BlackboardAttribute(
                            TSK_DATETIME, PARENT_MODULE_NAME,
                            recentFile.getCrtime()));
            try {
                BlackboardArtifact bbart = recentFile.newArtifact(TSK_RECENT_OBJECT);
                bbart.addAttributes(bbattributes);
                bbartifacts.add(bbart);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error creating recent document artifact.", ex); //NON-NLS
                this.addErrorMessage(
                        NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.getRecDoc.errMsg.errCreatingArtifact",
                                this.getModuleName()));
            }
        }

        try {
            blackboard.postArtifacts(bbartifacts, PARENT_MODULE_NAME);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Error while trying to post recent document artifact.", ex); //NON-NLS
            this.addErrorMessage(Bundle.Extractor_errPostingArtifacts(getModuleName()));
        }
    }

}
