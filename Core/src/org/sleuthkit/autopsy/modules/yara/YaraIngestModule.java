/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

import java.nio.file.Path;
import java.nio.file.Paths;
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

/**
 * An ingest module that runs the yara against the given files.
 *
 */
public class YaraIngestModule extends FileIngestModuleAdapter {

    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private final static Logger logger = Logger.getLogger(YaraIngestModule.class.getName());
    private static final String YARA_DIR = "yara";
    private static final Map<Long, Path> pathsByJobId = new ConcurrentHashMap<>();

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

            YaraIngestHelper.compileRules(settings.getSelectedRuleSetNames(), tempDir);
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

        if (settings.onlyExecutableFiles()) {
            String extension = file.getNameExtension();
            if (!extension.equals("exe")) {
                return ProcessResult.OK;
            }
        }

        // Skip the file if its 0 in length.
        if (file.getSize() == 0) {
            return ProcessResult.OK;
        }

        try {
            List<BlackboardArtifact> artifacts = YaraIngestHelper.scanFileForMatches(file, getTempDirectory(jobId).toFile());
            
            if(!artifacts.isEmpty()) {
                Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
                blackboard.postArtifacts(artifacts, YaraIngestModuleFactory.getModuleName());
            }
            
        } catch (BlackboardException | NoCurrentCaseException | IngestModuleException | TskCoreException | YaraWrapperException ex) {
            logger.log(Level.SEVERE, "YARA ingest module failed to process file.", ex);
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

}
