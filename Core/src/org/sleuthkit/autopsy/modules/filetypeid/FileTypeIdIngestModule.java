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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;

/**
 * Detects the type of a file based on signature (magic) values. Posts results
 * to the blackboard.
 */
public class FileTypeIdIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(FileTypeIdIngestModule.class.getName());
    private static final long MIN_FILE_SIZE = 512;
    private final FileTypeIdModuleSettings settings;
    private long jobId;  

    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private TikaFileTypeDetector tikaDetector = new TikaFileTypeDetector();

    private static class IngestJobTotals {
        long matchTime = 0;
        long numFiles = 0;
    }
    
    /**
     * Update the match time total and increment num of files for this job
     * @param ingestJobId  
     * @param matchTimeInc amount of time to add
     */
    private static synchronized void addToTotals(long ingestJobId, long matchTimeInc) {
        IngestJobTotals ingestJobTotals = totalsForIngestJobs.get(ingestJobId);
        if (ingestJobTotals == null) {
            ingestJobTotals = new IngestJobTotals();
            totalsForIngestJobs.put(ingestJobId, ingestJobTotals);
        }        
        
        ingestJobTotals.matchTime += matchTimeInc;
        ingestJobTotals.numFiles++;
        totalsForIngestJobs.put(ingestJobId, ingestJobTotals);
    }
    
    FileTypeIdIngestModule(FileTypeIdModuleSettings settings) {
        this.settings = settings;
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        refCounter.incrementAndGet(jobId);
    }    
    
    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        // skip non-files
        if ((abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (abstractFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)) {

            return ProcessResult.OK;
        }

        if (settings.skipKnownFiles() && (abstractFile.getKnown() == FileKnown.KNOWN)) {
            return ProcessResult.OK;
        }

        if (abstractFile.getSize() < MIN_FILE_SIZE) {
            return ProcessResult.OK;
        }

        try {
            long startTime = System.currentTimeMillis();
            tikaDetector.detectAndSave(abstractFile);
            addToTotals(jobId, (System.currentTimeMillis() - startTime)); //add match time
            return ProcessResult.OK;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error matching file signature", ex); //NON-NLS
            return ProcessResult.ERROR;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error matching file signature", e); //NON-NLS
            return ProcessResult.ERROR;
        }
    }

    @Override
    public void shutDown() {
        // We only need to post the summary msg from the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            IngestJobTotals jobTotals;
            synchronized(this) {
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
}