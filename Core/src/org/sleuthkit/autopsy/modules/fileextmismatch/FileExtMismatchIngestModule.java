/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * Flags mismatched filename extensions based on file signature.
 */
public class FileExtMismatchIngestModule extends IngestModuleAdapter implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(FileExtMismatchIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private final FileExtMismatchDetectorModuleSettings settings;
    private HashMap<String, String[]> SigTypeToExtMap = new HashMap<>();
    private long jobId;
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();    
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();

    private static class IngestJobTotals {
        long processTime = 0;
        long numFiles = 0;
    }
    
    private static synchronized void initTotals(long ingestJobId) {
        totalsForIngestJobs.put(ingestJobId, new IngestJobTotals());
    }
 
    /**
     * Update the match time total and increment num of files for this job
     * @param ingestJobId  
     * @param matchTimeInc amount of time to add
     */
    private static synchronized void addToTotals(long ingestJobId, long processTimeInc) {
        IngestJobTotals ingestJobTotals = totalsForIngestJobs.get(ingestJobId);
        ingestJobTotals.processTime += processTimeInc;
        ingestJobTotals.numFiles++;
        totalsForIngestJobs.put(ingestJobId, ingestJobTotals);
    }    
    
    FileExtMismatchIngestModule(FileExtMismatchDetectorModuleSettings settings) {
        this.settings = settings;
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        if (refCounter.incrementAndGet(jobId) == 1) {
            initTotals(jobId);
        }  
        FileExtMismatchXML xmlLoader = FileExtMismatchXML.getDefault();
        SigTypeToExtMap = xmlLoader.load();
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {
            return ProcessResult.OK;
        }

        // deleted files often have content that was not theirs and therefor causes mismatch
        if ((abstractFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC))
                || (abstractFile.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC))) {
            return ProcessResult.OK;
        }

        try {
            long startTime = System.currentTimeMillis();

            boolean mismatchDetected = compareSigTypeToExt(abstractFile);

            addToTotals(jobId, System.currentTimeMillis() - startTime);

            if (mismatchDetected) {
                // add artifact               
                BlackboardArtifact bart = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED);

                services.fireModuleDataEvent(new ModuleDataEvent(FileExtMismatchDetectorModuleFactory.getModuleName(), ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED, Collections.singletonList(bart)));
            }
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex);
            return ProcessResult.ERROR;
        }
    }

    /**
     * Compare file type for file and extension.
     *
     * @param abstractFile
     * @return false if the two match. True if there is a mismatch.
     */
    private boolean compareSigTypeToExt(AbstractFile abstractFile) {
        try {
            String currActualExt = abstractFile.getNameExtension();

            // If we are skipping names with no extension
            if (settings.skipFilesWithNoExtension() && currActualExt.isEmpty()) {
                return false;
            }

            // find file_sig value.
            // check the blackboard for a file type attribute
            ArrayList<BlackboardAttribute> attributes = abstractFile.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                String currActualSigType = attribute.getValueString();
                if (settings.skipFilesWithTextPlainMimeType()) {
                    if (!currActualExt.isEmpty() && currActualSigType.equals("text/plain")) {
                        return false;
                    }
                }

                //get known allowed values from the map for this type
                String[] allowedExtArray = SigTypeToExtMap.get(currActualSigType);
                if (allowedExtArray != null) {
                    List<String> allowedExtList = Arrays.asList(allowedExtArray);

                    // see if the filename ext is in the allowed list
                    if (allowedExtList != null) {
                        for (String e : allowedExtList) {
                            if (e.equals(currActualExt)) {
                                return false;
                            }
                        }
                        return true; //potential mismatch
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error while getting file signature from blackboard.", ex);
        }

        return false;
    }

    @Override
    public void shutDown(boolean ingestJobCancelled) {
        // We only need to post the summary msg from the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {    
            IngestJobTotals jobTotals = totalsForIngestJobs.remove(jobId);

            StringBuilder detailsSb = new StringBuilder();
            detailsSb.append("<table border='0' cellpadding='4' width='280'>");
            detailsSb.append("<tr><td>").append(FileExtMismatchDetectorModuleFactory.getModuleName()).append("</td></tr>");
            detailsSb.append("<tr><td>").append(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchIngestModule.complete.totalProcTime"))
                    .append("</td><td>").append(jobTotals.processTime).append("</td></tr>\n");
            detailsSb.append("<tr><td>").append(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchIngestModule.complete.totalFiles"))
                    .append("</td><td>").append(jobTotals.numFiles).append("</td></tr>\n");
            detailsSb.append("</table>");
            services.postMessage(IngestMessage.createMessage(IngestMessage.MessageType.INFO, FileExtMismatchDetectorModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                    "FileExtMismatchIngestModule.complete.svcMsg.text"),
                    detailsSb.toString()));
        }
    }
}
