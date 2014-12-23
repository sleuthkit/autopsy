/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.util.HashMap;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Detects the type of a file based on signature (magic) values. Posts results
 * to the blackboard.
 */
// TODO: This class does not need to be public.
public class FileTypeIdIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(FileTypeIdIngestModule.class.getName());
    private final FileTypeIdModuleSettings settings;
    private long jobId;
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private final UserDefinedFileTypeDetector userDefinedFileTypeIdentifier;
    private final TikaFileTypeDetector tikaDetector = new TikaFileTypeDetector();

    /**
     * Validate if a given mime type is in the detector's registry.
     *
     * @param mimeType Full string of mime type, e.g. "text/html"
     * @return true if detectable
     */
    public static boolean isMimeTypeDetectable(String mimeType) {
        /* This is an awkward place for this method because it is used only
         * by the file extension mismatch panel.  But, it works.  
         * We probabl dont' want to expose the tika class as the public
         * method to do this and a single class just for this method
         * seems a bit silly.
         */
        TikaFileTypeDetector detector = new TikaFileTypeDetector();
        return detector.isMimeTypeDetectable(mimeType);
    }

    /**
     * Creates an ingest module that detects the type of a file based on
     * signature (magic) values. Posts results to the blackboard.
     *
     * @param settings The ingest module settings.
     */
    FileTypeIdIngestModule(FileTypeIdModuleSettings settings) {
        this.settings = settings;
        userDefinedFileTypeIdentifier = new UserDefinedFileTypeDetector();
        try {
            userDefinedFileTypeIdentifier.loadFileTypes();
        } catch (UserDefinedFileTypesManager.UserDefinedFileTypesException ex) {
            logger.log(Level.SEVERE, "Failed to load file types", ex);
            MessageNotifyUtil.Notify.error(FileTypeIdModuleFactory.getModuleName(), ex.getMessage());            
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        refCounter.incrementAndGet(jobId);
    }

    /**
     * @inheritDoc
     */
    @Override
    public ProcessResult process(AbstractFile file) {
        
        String name = file.getName();
        
        /**
         * Skip unallocated space and unused blocks files.
         */
        if ((file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {

            return ProcessResult.OK;
        }

        /**
         * Skip known files if configured to do so.
         */
        if (settings.skipKnownFiles() && (file.getKnown() == FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        /**
         * Filter out very small files to minimize false positives.
         */
        if (settings.skipSmallFiles() && file.getSize() < settings.minFileSizeInBytes()) {
            return ProcessResult.OK;
        }
        
        try {
            long startTime = System.currentTimeMillis();
            FileType fileType = this.userDefinedFileTypeIdentifier.identify(file);
            if (null != fileType) {
                String moduleName = FileTypeIdModuleFactory.getModuleName();
                BlackboardArtifact getInfoArtifact = file.getGenInfoArtifact();
                BlackboardAttribute typeAttr = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG.getTypeID(), moduleName, fileType.getMimeType());
                getInfoArtifact.addAttribute(typeAttr);

                if (fileType.alertOnMatch()) {
                    BlackboardArtifact artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                    BlackboardAttribute setNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), moduleName, fileType.getFilesSetName());
                    artifact.addAttribute(setNameAttribute);

                    /**
                     * Use the MIME type as the category, i.e., the rule that
                     * determined this file belongs to the interesting files
                     * set.
                     */
                    BlackboardAttribute ruleNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID(), moduleName, fileType.getMimeType());
                    artifact.addAttribute(ruleNameAttribute);
                }

            } else {
                tikaDetector.detectAndSave(file);
            }
            addToTotals(jobId, (System.currentTimeMillis() - startTime));
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex); //NON-NLS
            return ProcessResult.ERROR;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error matching file signature", e); //NON-NLS
            return ProcessResult.ERROR;
        }
    }

    /**
     * @inheritDoc
     */
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
     * @param jobId The ingest job identifier.
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
