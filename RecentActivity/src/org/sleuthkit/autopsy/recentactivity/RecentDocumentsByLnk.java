 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.Collection;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.JLNK;
import org.sleuthkit.autopsy.coreutils.JLnkParser;
import org.sleuthkit.autopsy.coreutils.JLnkParserException;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.*;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_ASSOCIATED_OBJECT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;

/**
 * Recent documents class that will extract recent documents in the form of .lnk
 * files
 */
class RecentDocumentsByLnk extends Extract {

    private static final Logger logger = Logger.getLogger(RecentDocumentsByLnk.class.getName());
    private Content dataSource;
    private IngestJobContext context;
    
    @Messages({
        "Progress_Message_Extract_Resent_Docs=Recent Documents",
    })

    /**
     * Find the documents that Windows stores about recent documents and make
     * artifacts.
     *
     * @param dataSource
     * @param controller
     */
    private void getRecentDocuments() {

        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> recentFiles;
        try {
            recentFiles = fileManager.findFiles(dataSource, "%.lnk", "Recent"); //NON-NLS
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error searching for .lnk files."); //NON-NLS
            this.addErrorMessage(
                    NbBundle.getMessage(this.getClass(), "RecentDocumentsByLnk.getRecDoc.errMsg.errGetLnkFiles",
                            this.getName()));
            return;
        }

        if (recentFiles.isEmpty()) {
            logger.log(Level.INFO, "Didn't find any recent files."); //NON-NLS
            return;
        }

        dataFound = true;
        List<BlackboardArtifact> bbartifacts = new ArrayList<>();
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

            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
            String path = lnk.getBestPath();
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH,
                    NbBundle.getMessage(this.getClass(),
                            "RecentDocumentsByLnk.parentModuleName.noSpace"),
                    path));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PATH_ID,
                    NbBundle.getMessage(this.getClass(),
                            "RecentDocumentsByLnk.parentModuleName.noSpace"),
                    Util.findID(dataSource, path)));
            bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME,
                    NbBundle.getMessage(this.getClass(),
                            "RecentDocumentsByLnk.parentModuleName.noSpace"),
                    recentFile.getCrtime()));
            BlackboardArtifact bba = createArtifactWithAttributes(ARTIFACT_TYPE.TSK_RECENT_OBJECT, recentFile, bbattributes);
            if(bba != null) {
                bbartifacts.add(bba);
                bba = createAssociatedArtifact(path, bba);
                if (bba != null) {
                    bbartifacts.add(bba);
                }
            }
        }
         
        postArtifacts(bbartifacts);
    }

    /**
     * Create associated artifacts using file name and path and the artifact it associates with
     * 
     * @param filePathName file and path of object being associated with
     * 
     * @param bba blackboard artifact to associate with
     * 
     * @returnv BlackboardArtifact or a null value 
     */  
    private BlackboardArtifact createAssociatedArtifact(String filePathName, BlackboardArtifact bba) {
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = currentCase.getServices().getFileManager();
        String normalizePathName = FilenameUtils.normalize(filePathName, true);
        String fileName = FilenameUtils.getName(normalizePathName);
        String filePath = FilenameUtils.getPath(normalizePathName);
        List<AbstractFile> sourceFiles;
        try {
            sourceFiles = fileManager.findFiles(dataSource, fileName, filePath); //NON-NLS
            for (AbstractFile sourceFile : sourceFiles) {
                if (sourceFile.getParentPath().endsWith(filePath)) {
                    Collection<BlackboardAttribute> bbattributes2 = new ArrayList<>();
                    bbattributes2.addAll(Arrays.asList(
                         new BlackboardAttribute(TSK_ASSOCIATED_ARTIFACT, this.getName(),
                         bba.getArtifactID())));

                    BlackboardArtifact associatedObjectBba = createArtifactWithAttributes(TSK_ASSOCIATED_OBJECT, sourceFile, bbattributes2);
                    if (associatedObjectBba != null) {
                        return associatedObjectBba;
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Error finding actual file %s. file may not exist", filePathName)); //NON-NLS
        }
       
        return null;
    }
    
    @Override
    public void process(Content dataSource, IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        this.context = context;
        dataFound = false;
        
        progressBar.progress(Bundle.Progress_Message_Extract_Resent_Docs());
        this.getRecentDocuments();
    }
}
