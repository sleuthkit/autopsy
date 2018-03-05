/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.fileextmismatch.FileExtMismatchDetectorModuleSettings.CHECK_TYPE;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;

/**
 * Flags mismatched filename extensions based on file signature.
 */
@NbBundle.Messages({
    "CannotRunFileTypeDetection=Unable to run file type detection.",
    "FileExtMismatchIngestModule.readError.message=Could not read settings."
})
public class FileExtMismatchIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(FileExtMismatchIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private final FileExtMismatchDetectorModuleSettings settings;
    private HashMap<String, Set<String>> mimeTypeToExtsMap = new HashMap<>();
    private long jobId;
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static Blackboard blackboard;
    private FileTypeDetector detector;

    private static class IngestJobTotals {

        private long processTime = 0;
        private long numFiles = 0;
    }

    /**
     * Update the match time total and increment num of files for this job
     *
     * @param ingestJobId
     * @param processTimeInc amount of time to add
     */
    private static synchronized void addToTotals(long ingestJobId, long processTimeInc) {
        IngestJobTotals ingestJobTotals = totalsForIngestJobs.get(ingestJobId);
        if (ingestJobTotals == null) {
            ingestJobTotals = new IngestJobTotals();
            totalsForIngestJobs.put(ingestJobId, ingestJobTotals);
        }

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
        refCounter.incrementAndGet(jobId);

        try {
            mimeTypeToExtsMap = FileExtMismatchSettings.readSettings().getMimeTypeToExtsMap();
            this.detector = new FileTypeDetector();
        } catch (FileExtMismatchSettings.FileExtMismatchSettingsException ex) {
            throw new IngestModuleException(Bundle.FileExtMismatchIngestModule_readError_message(), ex);
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }
    }

    @Override
    @Messages({"FileExtMismatchIngestModule.indexError.message=Failed to index file extension mismatch artifact for keyword search."})
    public ProcessResult process(AbstractFile abstractFile) {
        try {
            blackboard = Case.getOpenCase().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        if (this.settings.skipKnownFiles() && (abstractFile.getKnown() == FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                || (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)
                || (abstractFile.isFile() == false)) {
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

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(bart);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bart.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(FileExtMismatchDetectorModuleFactory.getModuleName(), Bundle.FileExtMismatchIngestModule_indexError_message());
                }

                services.fireModuleDataEvent(new ModuleDataEvent(FileExtMismatchDetectorModuleFactory.getModuleName(), ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED, Collections.singletonList(bart)));
            }
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
    }

    /**
     * Compare file type for file and extension.
     *
     * @param abstractFile
     *
     * @return false if the two match. True if there is a mismatch.
     */
    private boolean compareSigTypeToExt(AbstractFile abstractFile) {
        String currActualExt = abstractFile.getNameExtension();

        // If we are skipping names with no extension
        if (settings.skipFilesWithNoExtension() && currActualExt.isEmpty()) {
            return false;
        }
        String currActualSigType = detector.getMIMEType(abstractFile);
        if (settings.getCheckType() != CHECK_TYPE.ALL) {
            if (settings.getCheckType() == CHECK_TYPE.NO_TEXT_FILES) {
                if (!currActualExt.isEmpty() && currActualSigType.equals("text/plain")) { //NON-NLS
                    return false;
                }
            }
            if (settings.getCheckType() == CHECK_TYPE.ONLY_MEDIA_AND_EXE) {
                if (!FileExtMismatchDetectorModuleSettings.MEDIA_AND_EXE_MIME_TYPES.contains(currActualSigType)) {
                    return false;
                }
            }
        }

        //get known allowed values from the map for this type
        Set<String> allowedExtSet = mimeTypeToExtsMap.get(currActualSigType);
        if (allowedExtSet != null) {
            // see if the filename ext is in the allowed list
            for (String e : allowedExtSet) {
                if (e.equals(currActualExt)) {
                    return false;
                }
            }
            return true; //potential mismatch
        }

        return false;
    }

    @Override
    public void shutDown() {
        // We only need to post the summary msg from the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            IngestJobTotals jobTotals;
            synchronized (this) {
                jobTotals = totalsForIngestJobs.remove(jobId);
            }
            if (jobTotals != null) {
                StringBuilder detailsSb = new StringBuilder();
                detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
                detailsSb.append("<tr><td>").append(FileExtMismatchDetectorModuleFactory.getModuleName()).append("</td></tr>"); //NON-NLS
                detailsSb.append("<tr><td>").append( //NON-NLS
                        NbBundle.getMessage(this.getClass(), "FileExtMismatchIngestModule.complete.totalProcTime"))
                        .append("</td><td>").append(jobTotals.processTime).append("</td></tr>\n"); //NON-NLS
                detailsSb.append("<tr><td>").append( //NON-NLS
                        NbBundle.getMessage(this.getClass(), "FileExtMismatchIngestModule.complete.totalFiles"))
                        .append("</td><td>").append(jobTotals.numFiles).append("</td></tr>\n"); //NON-NLS
                detailsSb.append("</table>"); //NON-NLS

                services.postMessage(IngestMessage.createMessage(IngestMessage.MessageType.INFO, FileExtMismatchDetectorModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(),
                                "FileExtMismatchIngestModule.complete.svcMsg.text"),
                        detailsSb.toString()));
            }
        }
    }
}
