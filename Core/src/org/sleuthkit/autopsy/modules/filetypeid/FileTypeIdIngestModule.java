/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.filetypeid.CustomFileTypesManager.CustomFileTypesException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Detects the type of a file based on signature (magic) values. Posts results
 * to the blackboard.
 */
@NbBundle.Messages({"CannotRunFileTypeDetection=Unable to run file type detection."})
public class FileTypeIdIngestModule implements FileIngestModule {
    
    private static final Logger logger = Logger.getLogger(FileTypeIdIngestModule.class.getName());
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();

    private long jobId;
    private FileTypeDetector fileTypeDetector;

    /**
     * Validate if a given mime type is in the detector's registry.
     *
     * @deprecated Use FileTypeDetector.mimeTypeIsDetectable(String mimeType)
     * instead.
     * @param mimeType Full string of mime type, e.g. "text/html"
     *
     * @return true if detectable
     */
    @Deprecated
    public static boolean isMimeTypeDetectable(String mimeType) {
        try {
            return new FileTypeDetector().isDetectable(mimeType);
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Failed to create file type detector", ex); //NON-NLS
            return false;
        }
    }

    /**
     * Creates an ingest module that detects the type of a file based on
     * signature (magic) values. Posts results to the blackboard.
     */
    FileTypeIdIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        refCounter.incrementAndGet(jobId);
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile file) {
        /**
         * Attempt to detect the file type. Do it within an exception firewall,
         * so that any issues with reading file content or complaints from tika
         * do not take the module down.
         */
        try {
            long startTime = System.currentTimeMillis();
            String mimeType = fileTypeDetector.getMIMEType(file);
            file.setMIMEType(mimeType);
            FileType fileType = detectUserDefinedFileType(file);
            if (fileType != null && fileType.shouldCreateInterestingFileHit()) {
                createInterestingFileHit(file, fileType);
            }
            addToTotals(jobId, (System.currentTimeMillis() - startTime));
            return ProcessResult.OK;
        } catch (Exception e) {
            logger.log(Level.WARNING, String.format("Error while attempting to determine file type of file %d", file.getId()), e); //NON-NLS
            return ProcessResult.ERROR;
        }
    }

    /**
     * Determines whether or not a file matches a user-defined custom file type.
     *
     * @param file The file to test.
     *
     * @return The file type if a match is found; otherwise null.
     *
     * @throws CustomFileTypesException If there is an issue getting an instance
     *                                  of CustomFileTypesManager.
     */
    private FileType detectUserDefinedFileType(AbstractFile file) throws CustomFileTypesManager.CustomFileTypesException {
        
        if (CustomFileTypesManager.getInstance().getUserDefinedFileTypes().isEmpty()) {
            return null;
        }
        
        /*
         * Read in the beginning of the file once.
         */
        byte[] buf = new byte[1024];
        int bufLen;
        try {
            bufLen = file.read(buf, 0, 1024);
        } catch (TskCoreException ex) {
            // Proceed for now - the error will likely get logged next time the file is read.
            bufLen = 0; 
        }
        return detectUserDefinedFileType(file, buf, bufLen);
    }

    /**
     * Determines whether or not a file matches a user-defined custom file type.
     *
     * @param file The file to test.
     * @param startOfFileBuffer  The beginning of the file data.
     * @param bufLen The length of startOfFileBuffer.
     *
     * @return The file type if a match is found; otherwise null.
     *
     * @throws CustomFileTypesException If there is an issue getting an instance
     *                                  of CustomFileTypesManager.
     */
    private FileType detectUserDefinedFileType(AbstractFile file, byte[] startOfFileBuffer, int bufLen) throws CustomFileTypesManager.CustomFileTypesException {    
        FileType retValue = null;

        CustomFileTypesManager customFileTypesManager = CustomFileTypesManager.getInstance();
        List<FileType> fileTypesList = customFileTypesManager.getUserDefinedFileTypes();
        for (FileType fileType : fileTypesList) {
            if (fileType.matches(file, startOfFileBuffer, bufLen)) {
                retValue = fileType;
                break;
            }
        }

        return retValue;
    }

    /**
     * Create an Interesting File hit using the specified file type rule.
     *
     * @param file     The file from which to generate an artifact.
     * @param fileType The file type rule for categorizing the hit.
     */
    private void createInterestingFileHit(AbstractFile file, FileType fileType) {

        List<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(
                        TSK_SET_NAME, FileTypeIdModuleFactory.getModuleName(),
                        fileType.getInterestingFilesSetName()),
                new BlackboardAttribute(
                        TSK_CATEGORY, FileTypeIdModuleFactory.getModuleName(),
                        fileType.getMimeType()));
        try {
            Case currentCase = Case.getCurrentCaseThrows();

            Blackboard tskBlackboard = currentCase.getSleuthkitCase().getBlackboard();
            // Create artifact if it doesn't already exist.
            if (!tskBlackboard.artifactExists(file, BlackboardArtifact.Type.TSK_INTERESTING_ITEM, attributes)) {
                BlackboardArtifact artifact = file.newAnalysisResult(
                        BlackboardArtifact.Type.TSK_INTERESTING_ITEM, Score.SCORE_LIKELY_NOTABLE, 
                        null, fileType.getInterestingFilesSetName(), null, 
                        attributes)
                        .getAnalysisResult();
                try {
                    /*
                     * post the artifact which will index the artifact for
                     * keyword search, and fire an event to notify UI of this
                     * new artifact
                     */
                    tskBlackboard.postArtifact(artifact, FileTypeIdModuleFactory.getModuleName(), jobId);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, String.format("Unable to index TSK_INTERESTING_ITEM blackboard artifact %d (file obj_id=%d)", artifact.getArtifactID(), file.getId()), ex); //NON-NLS
                }
            }

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Unable to create TSK_INTERESTING_ITEM artifact for file (obj_id=%d)", file.getId()), ex); //NON-NLS
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
        }
    }

    @Override
    public void shutDown() {
        /**
         * If this is the instance of this module for this ingest job, post a
         * summary message to the ingest messages box.
         */
        if (refCounter.decrementAndGet(jobId) == 0) {
            IngestJobTotals jobTotals;
            synchronized (this) {
                jobTotals = totalsForIngestJobs.remove(jobId);
            }
            if (jobTotals != null) {
                StringBuilder detailsSb = new StringBuilder();
                detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
                detailsSb.append("<tr><td>").append(FileTypeIdModuleFactory.getModuleName()).append("</td></tr>"); //NON-NLS
                detailsSb.append("<tr><td>") //NON-NLS
                        .append(NbBundle.getMessage(this.getClass(), "FileTypeIdIngestModule.complete.totalProcTime"))
                        .append("</td><td>").append(jobTotals.matchTime).append("</td></tr>\n"); //NON-NLS
                detailsSb.append("<tr><td>") //NON-NLS
                        .append(NbBundle.getMessage(this.getClass(), "FileTypeIdIngestModule.complete.totalFiles"))
                        .append("</td><td>").append(jobTotals.numFiles).append("</td></tr>\n"); //NON-NLS
                detailsSb.append("</table>"); //NON-NLS
                IngestServices.getInstance().postMessage(IngestMessage.createMessage(IngestMessage.MessageType.INFO, FileTypeIdModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(),
                                "FileTypeIdIngestModule.complete.srvMsg.text"),
                        detailsSb.toString()));
            }
        }
    }

    /**
     * Update the match time total and increment number of files processed for
     * this ingest job.
     *
     * @param jobId        The ingest job identifier.
     * @param matchTimeInc Amount of time to add.
     */
    private static synchronized void addToTotals(long jobId, long matchTimeInc) {
        IngestJobTotals ingestJobTotals = totalsForIngestJobs.get(jobId);
        if (ingestJobTotals == null) {
            ingestJobTotals = new IngestJobTotals();
            totalsForIngestJobs.put(jobId, ingestJobTotals);
        }

        ingestJobTotals.matchTime += matchTimeInc;
        ingestJobTotals.numFiles++;
        totalsForIngestJobs.put(jobId, ingestJobTotals);
    }

    private static class IngestJobTotals {

        long matchTime = 0;
        long numFiles = 0;
    }
}
