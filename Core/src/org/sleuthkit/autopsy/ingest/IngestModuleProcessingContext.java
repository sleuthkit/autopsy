/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Acts as a facade for the parts of the ingest framework that make up the
 * processing context of an ingest module.
 */
public final class IngestModuleProcessingContext {

    private final IngestJob ingestJob;
    private final IngestModuleFactory moduleFactory;
    private final IngestManager ingestManager;
    private final IngestScheduler scheduler;
    private final Case autopsyCase;
    private final SleuthkitCase sleuthkitCase;

    IngestModuleProcessingContext(IngestJob ingestJob, IngestModuleFactory moduleFactory) {
        this.ingestJob = ingestJob;
        this.moduleFactory = moduleFactory;
        ingestManager = IngestManager.getDefault();
        scheduler = IngestScheduler.getInstance();
        autopsyCase = Case.getCurrentCase();
        sleuthkitCase = this.autopsyCase.getSleuthkitCase();
    }

    public boolean isIngestJobCancelled() {
        return this.ingestJob.isCancelled();
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public Case getCase() {
        return autopsyCase;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public SleuthkitCase getSleuthkitCase() {
        return sleuthkitCase;
    }

    public String getOutputDirectoryAbsolutePath() {
        return autopsyCase.getCaseDirectory() + File.separator + Case.getModulesOutputDirRelPath() + File.separator + moduleFactory.getModuleDisplayName();
    }

    public String getOutputDirectoryRelativePath() {
        return "ModuleOutput" + File.separator + moduleFactory.getModuleDisplayName();
    }

    public void submitFilesForIngest(List<AbstractFile> files) {
        for (AbstractFile file : files) {
            scheduler.getFileScheduler().scheduleIngestOfDerivedFile(ingestJob.getId(), file);
        }
    }

    public void postIngestMessage(long ID, IngestMessage.MessageType messageType, String subject, String detailsHtml) {
        IngestMessage message = IngestMessage.createMessage(ID, messageType, moduleFactory.getModuleDisplayName(), subject, detailsHtml);
        ingestManager.postIngestMessage(message);
    }

    public void postIngestMessage(long ID, IngestMessage.MessageType messageType, String subject) {
        IngestMessage message = IngestMessage.createMessage(ID, messageType, moduleFactory.getModuleDisplayName(), subject);
        ingestManager.postIngestMessage(message);
    }

    public void postErrorIngestMessage(long ID, String subject, String detailsHtml) {
        IngestMessage message = IngestMessage.createErrorMessage(ID, moduleFactory.getModuleDisplayName(), subject, detailsHtml);
        ingestManager.postIngestMessage(message);
    }

    public void postWarningIngestMessage(long ID, String subject, String detailsHtml) {
        IngestMessage message = IngestMessage.createWarningMessage(ID, moduleFactory.getModuleDisplayName(), subject, detailsHtml);
        ingestManager.postIngestMessage(message);
    }

    public void postDataMessage(long ID, String subject, String detailsHtml, String uniqueKey, BlackboardArtifact data) {
        IngestMessage message = IngestMessage.createDataMessage(ID, moduleFactory.getModuleDisplayName(), subject, detailsHtml, uniqueKey, data);
        ingestManager.postIngestMessage(message);
    }

    public void fireDataEvent(BlackboardArtifact.ARTIFACT_TYPE artifactType) {
        ModuleDataEvent event = new ModuleDataEvent(moduleFactory.getModuleDisplayName(), artifactType);
        IngestManager.fireModuleDataEvent(event);
    }

    public void fireDataEvent(BlackboardArtifact.ARTIFACT_TYPE artifactType, Collection<BlackboardArtifact> artifactIDs) {
        ModuleDataEvent event = new ModuleDataEvent(moduleFactory.getModuleDisplayName(), artifactType, artifactIDs);
        IngestManager.fireModuleDataEvent(event);
    }

    // RJCTODO: Make story to convert existing core modules to use logging methods, address sloppy use of level...
    public void logInfo(Class moduleClass, String message, Throwable ex) {
        Logger.getLogger(moduleClass.getName()).log(Level.INFO, message, ex);
    }

    public void logWarning(Class moduleClass, String message, Throwable ex) {
        Logger.getLogger(moduleClass.getName()).log(Level.WARNING, message, ex);
    }

    public void logError(Class moduleClass, String message, Throwable ex) {
        Logger.getLogger(moduleClass.getName()).log(Level.SEVERE, message, ex);
    }

    // RJCTODO: Leave public or create blackboard attribute factory methods, 
    // perhaps as many as eleven. End goal is for this to be package    
    public String getModuleDisplayName() {
        return this.moduleFactory.getModuleDisplayName();
    }
}
