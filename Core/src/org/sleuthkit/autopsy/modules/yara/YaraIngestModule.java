/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.yara;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.apache.commons.lang3.RandomStringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.yara.YaraWrapperException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An ingest module that runs the yara against the given files.
 *
 */
public class YaraIngestModule extends FileIngestModuleAdapter {

    // 15MB
    private static final int FILE_SIZE_THRESHOLD_MB = 100;
    private static final int FILE_SIZE_THRESHOLD_BYTE = FILE_SIZE_THRESHOLD_MB * 1024 * 1024;
    private static final int YARA_SCAN_TIMEOUT_SEC = 30 * 60 * 60; // 30 minutes.
    
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private final static Logger logger = Logger.getLogger(YaraIngestModule.class.getName());
    private static final String YARA_DIR = "yara";
    private static final Map<Long, Path> pathsByJobId = new ConcurrentHashMap<>();
    private static final String RULESET_DIR = "RuleSets";

    private final YaraIngestJobSettings settings;

    private IngestJobContext context = null;
    private Long jobId;

    /**
     * Constructor.
     *
     * @param settings
     */
    YaraIngestModule(YaraIngestJobSettings settings) {
        this.settings = settings;
    }

    @Messages({
        "YaraIngestModule_windows_error_msg=The YARA ingest module is only available on 64bit Windows.",})

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        this.jobId = context.getJobId();

        if (!PlatformUtil.isWindowsOS() || !PlatformUtil.is64BitOS()) {
            throw new IngestModule.IngestModuleException(Bundle.YaraIngestModule_windows_error_msg());
        }

        if (refCounter.incrementAndGet(jobId) == 1) {
            // compile the selected rules & put into temp folder based on jobID
            Path tempDir = getTempDirectory(jobId);
            Path tempRuleSetDir = Paths.get(tempDir.toString(), RULESET_DIR);
            if(!tempRuleSetDir.toFile().exists()) {
                tempRuleSetDir.toFile().mkdir();
            }

            if(settings.hasSelectedRuleSets()) {
                YaraIngestHelper.compileRules(settings.getSelectedRuleSetNames(), tempRuleSetDir);
            } else {
                logger.log(Level.INFO, "YARA ingest module: No rule set was selected for this ingest job.");
            }
        }
    }

    @Override
    public void shutDown() {
        if (context != null && refCounter.decrementAndGet(jobId) == 0) {
            // do some clean up.
            Path jobPath = pathsByJobId.get(jobId);
            if (jobPath != null) {
                jobPath.toFile().delete();
                pathsByJobId.remove(jobId);
            }
        }
    }

    @Override
    public ProcessResult process(AbstractFile file) {

        if(!settings.hasSelectedRuleSets()) {
            return ProcessResult.OK;
        }
        
        if (settings.onlyExecutableFiles()) {
            String extension = file.getNameExtension();
            if (!extension.equals("exe")) {
                return ProcessResult.OK;
            }
        }

        // Skip the file if its 0 in length or a directory.
        if (file.getSize() == 0 || 
                file.isDir() || 
                file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
            return ProcessResult.OK;
        }

        try {
            List<BlackboardArtifact> artifacts = new ArrayList<>();
            File ruleSetsDir = Paths.get(getTempDirectory(jobId).toString(), RULESET_DIR).toFile();
            
            // If the file size is less than FILE_SIZE_THRESHOLD_BYTE read the file
            // into a buffer, else make a local copy of the file.
            if(file.getSize() < FILE_SIZE_THRESHOLD_BYTE) {
                byte[] fileBuffer = new byte[(int)file.getSize()];
                
                int dataRead = file.read(fileBuffer, 0, file.getSize());
                if(dataRead != 0) {
                    artifacts.addAll( YaraIngestHelper.scanFileForMatches(file, ruleSetsDir, fileBuffer, dataRead, YARA_SCAN_TIMEOUT_SEC));
                } 
            } else {
                File tempCopy = createLocalCopy(file);
                artifacts.addAll( YaraIngestHelper.scanFileForMatches(file, ruleSetsDir, tempCopy, YARA_SCAN_TIMEOUT_SEC));
                tempCopy.delete();
            }
            
            if(!artifacts.isEmpty()) {
                Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
                blackboard.postArtifacts(artifacts, YaraIngestModuleFactory.getModuleName(), context.getJobId());
            }
            
        } catch (BlackboardException | NoCurrentCaseException | IngestModuleException | TskCoreException | YaraWrapperException ex) {
            logger.log(Level.SEVERE, String.format("YARA ingest module failed to process file id %d", file.getId()), ex);
            return ProcessResult.ERROR;
        } catch(IOException ex) {
            logger.log(Level.SEVERE, String.format("YARA ingest module failed to make a local copy of given file id %d", file.getId()), ex);
            return ProcessResult.ERROR;
        }
        
        return ProcessResult.OK;
    }

    /**
     * Return the temp directory for this jobId. If the folder does not exit it
     * will be created.
     *
     * @param jobId The current jobId
     *
     * @return The path of the temporary directory for the given jobId.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    private synchronized Path getTempDirectory(long jobId) throws IngestModuleException {
        Path jobPath = pathsByJobId.get(jobId);
        if (jobPath != null) {
            return jobPath;
        }

        Path baseDir;
        try {
            baseDir = Paths.get(Case.getCurrentCaseThrows().getTempDirectory(), YARA_DIR);
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException("Failed to create YARA ingest model temp directory, no open case.", ex);
        }

        // Make the base yara directory, as needed
        if (!baseDir.toFile().exists()) {
            baseDir.toFile().mkdirs();
        }

        String randomDirName = String.format("%s_%d", RandomStringUtils.randomAlphabetic(8), jobId);
        jobPath = Paths.get(baseDir.toString(), randomDirName);
        jobPath.toFile().mkdir();

        pathsByJobId.put(jobId, jobPath);

        return jobPath;
    }
    
    /**
     * Create a local copy of the given AbstractFile.
     * 
     * @param file AbstractFile to make a copy of.
     * 
     * @return A File object representation of the local copy.
     * 
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     * @throws IOException 
     */
    protected File createLocalCopy(AbstractFile file) throws IngestModuleException, IOException {
        String tempFileName = RandomStringUtils.randomAlphabetic(15) + file.getId() + ".temp";
        
        File tempFile = Paths.get(getTempDirectory(context.getJobId()).toString(), tempFileName).toFile();
        ContentUtils.writeToFile(file, tempFile, context::dataSourceIngestIsCancelled);
        
        return tempFile;
    }

}
