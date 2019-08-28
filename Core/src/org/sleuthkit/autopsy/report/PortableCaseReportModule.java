/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.util.logging.Level;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.report.caseuco.CaseUcoFormatExporter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Creates a portable case from tagged files
 */
class PortableCaseReportModule implements ReportModule {
    private static final Logger logger = Logger.getLogger(PortableCaseReportModule.class.getName());
    private static final String FILE_FOLDER_NAME = "PortableCaseFiles";  // NON-NLS
    private static final String UNKNOWN_FILE_TYPE_FOLDER = "Other";  // NON-NLS
    private static final String MAX_ID_TABLE_NAME = "portable_case_max_ids";  // NON-NLS
    private PortableCaseOptions options;
    
    // These are the types for the exported file subfolders
    private static final List<FileTypeCategory> FILE_TYPE_CATEGORIES = Arrays.asList(FileTypeCategory.AUDIO, FileTypeCategory.DOCUMENTS,
            FileTypeCategory.EXECUTABLE, FileTypeCategory.IMAGE, FileTypeCategory.VIDEO);
    
    private Case currentCase = null;
    private SleuthkitCase portableSkCase = null;
    private final String caseName;
    private File caseFolder = null;
    private File copiedFilesFolder = null;
    
    // Maps old object ID from current case to new object in portable case
    private final Map<Long, Content> oldIdToNewContent = new HashMap<>();
    
    // Maps new object ID to the new object
    private final Map<Long, Content> newIdToContent = new HashMap<>();
    
    // Maps old TagName to new TagName
    private final Map<TagName, TagName> oldTagNameToNewTagName = new HashMap<>();

    // Map of old artifact type ID to new artifact type ID. There will only be changes if custom artifact types are present.
    private final Map<Integer, Integer> oldArtTypeIdToNewArtTypeId = new HashMap<>();
    
    // Map of old attribute type ID to new attribute type ID. There will only be changes if custom attr types are present.
    private final Map<Integer, BlackboardAttribute.Type> oldAttrTypeIdToNewAttrType = new HashMap<>();
    
    // Map of old artifact ID to new artifact
    private final Map<Long, BlackboardArtifact> oldArtifactIdToNewArtifact = new HashMap<>();
    
    PortableCaseReportModule() {
        caseName = Case.getCurrentCase().getDisplayName() + " (Portable)"; // NON-NLS
    }

    @NbBundle.Messages({
        "PortableCaseReportModule.getName.name=Portable Case"
    })
    @Override
    public String getName() {
        return Bundle.PortableCaseReportModule_getName_name();
    }

    @NbBundle.Messages({
        "PortableCaseReportModule.getDescription.description=Copies selected items to a new single-user case that can be easily shared"
    })
    @Override
    public String getDescription() {
        return Bundle.PortableCaseReportModule_getDescription_description();
    }

    @Override
    public String getRelativeFilePath() {
        return caseName;
    }
    
    /**
     * Convenience method for handling cancellation
     * 
     * @param progressPanel  The report progress panel
     */
    private void handleCancellation(ReportProgressPanel progressPanel) {
        logger.log(Level.INFO, "Portable case creation canceled by user"); // NON-NLS
        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.CANCELED);
        cleanup();
    }
    
    /**
     * Convenience method to avoid code duplication.
     * Assumes that if an exception is supplied then the error is SEVERE. Otherwise
     * it is logged as a WARNING.
     * 
     * @param logWarning     Warning to write to the log
     * @param dialogWarning  Warning to write to a pop-up window
     * @param ex             The exception (can be null)
     * @param progressPanel  The report progress panel
     */
    private void handleError(String logWarning, String dialogWarning, Exception ex, ReportProgressPanel progressPanel) {
        if (ex == null) {
            logger.log(Level.WARNING, logWarning);
        } else {
            logger.log(Level.SEVERE, logWarning, ex);
        }
        MessageNotifyUtil.Message.error(dialogWarning);
        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR);
        cleanup();
    }

    @NbBundle.Messages({
        "PortableCaseReportModule.generateReport.verifying=Verifying selected parameters...",
        "PortableCaseReportModule.generateReport.creatingCase=Creating portable case database...",
        "PortableCaseReportModule.generateReport.copyingTags=Copying tags...",
        "# {0} - tag name",
        "PortableCaseReportModule.generateReport.copyingFiles=Copying files tagged as {0}...",
        "# {0} - tag name",
        "PortableCaseReportModule.generateReport.copyingArtifacts=Copying artifacts tagged as {0}...",
        "# {0} - output folder",
        "PortableCaseReportModule.generateReport.outputDirDoesNotExist=Output folder {0} does not exist",
        "# {0} - output folder",
        "PortableCaseReportModule.generateReport.outputDirIsNotDir=Output folder {0} is not a folder",
        "PortableCaseReportModule.generateReport.caseClosed=Current case has been closed",
        "PortableCaseReportModule.generateReport.interestingItemError=Error loading intersting items",
        "PortableCaseReportModule.generateReport.noContentToCopy=No interesting files, results, or tagged items to copy",
        "PortableCaseReportModule.generateReport.errorCopyingTags=Error copying tags",
        "PortableCaseReportModule.generateReport.errorCopyingFiles=Error copying tagged files",
        "PortableCaseReportModule.generateReport.errorCopyingArtifacts=Error copying tagged artifacts",
        "PortableCaseReportModule.generateReport.errorCopyingInterestingFiles=Error copying interesting files",
        "PortableCaseReportModule.generateReport.errorCopyingInterestingResults=Error copying interesting results",
        "# {0} - attribute type name",
        "PortableCaseReportModule.generateReport.errorLookingUpAttrType=Error looking up attribute type {0}",
        "PortableCaseReportModule.generateReport.compressingCase=Compressing case...",
    })

    void generateReport(String reportPath, PortableCaseOptions options, ReportProgressPanel progressPanel) {
        this.options = options;
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_verifying());
        
        // Clear out any old values
        cleanup();
        
        // Validate the input parameters
        File outputDir = new File(reportPath);
        if (! outputDir.exists()) {
            handleError("Output folder " + outputDir.toString() + " does not exist",
                    Bundle.PortableCaseReportModule_generateReport_outputDirDoesNotExist(outputDir.toString()), null, progressPanel); // NON-NLS
            return;
        }
        
        if (! outputDir.isDirectory()) {
            handleError("Output folder " + outputDir.toString() + " is not a folder",
                    Bundle.PortableCaseReportModule_generateReport_outputDirIsNotDir(outputDir.toString()), null, progressPanel); // NON-NLS
            return;
        }
        
        // Save the current case object
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            handleError("Current case has been closed",
                    Bundle.PortableCaseReportModule_generateReport_caseClosed(), null, progressPanel); // NON-NLS
            return;
        } 
        
        // Check that there will be something to copy
        List<TagName> tagNames = options.getSelectedTagNames();
        List<String> setNames = options.getSelectedSetNames();
        if (tagNames.isEmpty() && setNames.isEmpty()) {  
            handleError("No content to copy", 
                    Bundle.PortableCaseReportModule_generateReport_noContentToCopy(), null, progressPanel); // NON-NLS
            return;
        }
        
        // Create the case.
        // portableSkCase and caseFolder will be set here.
        progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_creatingCase());
        createCase(outputDir, progressPanel);
        if (portableSkCase == null) {
            // The error has already been handled
            return;
        }
        
        // Check for cancellation 
        if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
            handleCancellation(progressPanel);
            return;
        }
        
        // Copy the selected tags
        progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_copyingTags());
        try {
            for(TagName tagName:tagNames) {
                TagName newTagName = portableSkCase.addOrUpdateTagName(tagName.getDisplayName(), tagName.getDescription(), tagName.getColor(), tagName.getKnownStatus());
                oldTagNameToNewTagName.put(tagName, newTagName);
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tags", Bundle.PortableCaseReportModule_generateReport_errorCopyingTags(), ex, progressPanel); // NON-NLS
            return;
        }
                
        // Set up tracking to support any custom artifact or attribute types
        for (BlackboardArtifact.ARTIFACT_TYPE type:BlackboardArtifact.ARTIFACT_TYPE.values()) {
            oldArtTypeIdToNewArtTypeId.put(type.getTypeID(), type.getTypeID());
        }
        for (BlackboardAttribute.ATTRIBUTE_TYPE type:BlackboardAttribute.ATTRIBUTE_TYPE.values()) {
            try {
                oldAttrTypeIdToNewAttrType.put(type.getTypeID(), portableSkCase.getAttributeType(type.getLabel()));
            } catch (TskCoreException ex) {
                handleError("Error looking up attribute name " + type.getLabel(),
                        Bundle.PortableCaseReportModule_generateReport_errorLookingUpAttrType(type.getLabel()),
                        ex, progressPanel); // NON-NLS
            }
        }        
        
        // Copy the tagged files
        try {
            for(TagName tagName:tagNames) {
                // Check for cancellation 
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    handleCancellation(progressPanel);
                    return;
                }
                progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_copyingFiles(tagName.getDisplayName()));
                addFilesToPortableCase(tagName, progressPanel);
                
                // Check for cancellation 
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    handleCancellation(progressPanel);
                    return;
                }
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tagged files", Bundle.PortableCaseReportModule_generateReport_errorCopyingFiles(), ex, progressPanel); // NON-NLS
            return;
        } 
        
        // Copy the tagged artifacts and associated files
        try {
            for(TagName tagName:tagNames) {
                // Check for cancellation 
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    handleCancellation(progressPanel);
                    return;
                }
                progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_copyingArtifacts(tagName.getDisplayName()));
                addArtifactsToPortableCase(tagName, progressPanel);
                
                // Check for cancellation 
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    handleCancellation(progressPanel);
                    return;
                }
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tagged artifacts", Bundle.PortableCaseReportModule_generateReport_errorCopyingArtifacts(), ex, progressPanel); // NON-NLS
            return;
        }
        
        // Copy interesting files and results
        if (! setNames.isEmpty()) {
            try {
                List<BlackboardArtifact> interestingFiles = currentCase.getSleuthkitCase().getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                for (BlackboardArtifact art:interestingFiles) {
                    // Check for cancellation 
                    if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                        handleCancellation(progressPanel);
                        return;
                    }
                    
                    BlackboardAttribute setAttr = art.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                    if (setNames.contains(setAttr.getValueString())) {
                        copyContentToPortableCase(art, progressPanel);
                    }
                }
            } catch (TskCoreException ex) {
                handleError("Error copying interesting files", Bundle.PortableCaseReportModule_generateReport_errorCopyingInterestingFiles(), ex, progressPanel); // NON-NLS
                return;
            }

            try {
                List<BlackboardArtifact> interestingResults = currentCase.getSleuthkitCase().getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
                for (BlackboardArtifact art:interestingResults) {
                    // Check for cancellation 
                    if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                        handleCancellation(progressPanel);
                        return;
                    }
                    BlackboardAttribute setAttr = art.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                    if (setNames.contains(setAttr.getValueString())) {
                        copyContentToPortableCase(art, progressPanel);
                    }
                }
            } catch (TskCoreException ex) {
                handleError("Error copying interesting results", Bundle.PortableCaseReportModule_generateReport_errorCopyingInterestingResults(), ex, progressPanel); // NON-NLS
                return;
            }
        }        
        
        // Check for cancellation 
        if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
            handleCancellation(progressPanel);
            return;
        }
        
        File reportsFolder = Paths.get(caseFolder.toString(), "Reports").toFile();
        if(!reportsFolder.mkdir()) {
            handleError("Could not make report folder", "Could not make report folder", null, progressPanel); // NON-NLS
            return;
        }
        
        try {
            CaseUcoFormatExporter.export(tagNames, setNames, reportsFolder, progressPanel);
        } catch (IOException | SQLException | NoCurrentCaseException | TskCoreException ex) {
            handleError("Problem while generating CASE-UCO report", 
                    "Problem while generating CASE-UCO report", ex, progressPanel); // NON-NLS
        }
        
        // Compress the case (if desired)
        if (options.shouldCompress()) {
            progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_compressingCase());
            
            boolean success = compressCase(progressPanel);
            
            // Check for cancellation 
            if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                handleCancellation(progressPanel);
                return;
            }
            
            if (! success) {
                // Errors have been handled already
                return;
            }
        }
        
        // Close the case connections and clear out the maps
        cleanup();
        
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
        
    }

    /**
     * Create the case directory and case database. 
     * portableSkCase will be set if this completes without error.
     * 
     * @param outputDir  The parent for the case folder
     * @param progressPanel 
     */
    @NbBundle.Messages({
        "# {0} - case folder",
        "PortableCaseReportModule.createCase.caseDirExists=Case folder {0} already exists",
        "PortableCaseReportModule.createCase.errorCreatingCase=Error creating case",
        "# {0} - folder",
        "PortableCaseReportModule.createCase.errorCreatingFolder=Error creating folder {0}",
        "PortableCaseReportModule.createCase.errorStoringMaxIds=Error storing maximum database IDs",
    })
    private void createCase(File outputDir, ReportProgressPanel progressPanel) {

        // Create the case folder
        caseFolder = Paths.get(outputDir.toString(), caseName).toFile();

        if (caseFolder.exists()) {
            handleError("Case folder " + caseFolder.toString() + " already exists",
                Bundle.PortableCaseReportModule_createCase_caseDirExists(caseFolder.toString()), null, progressPanel); // NON-NLS  
            return;
        }
        
        // Create the case
        try {
            portableSkCase = currentCase.createPortableCase(caseName, caseFolder);
        } catch (TskCoreException ex) {
            handleError("Error creating case " + caseName + " in folder " + caseFolder.toString(),
                Bundle.PortableCaseReportModule_createCase_errorCreatingCase(), ex, progressPanel);   // NON-NLS
            return;
        }
        
        // Store the highest IDs
        try {
            saveHighestIds();
        } catch (TskCoreException ex) {
            handleError("Error storing maximum database IDs",
                Bundle.PortableCaseReportModule_createCase_errorStoringMaxIds(), ex, progressPanel);   // NON-NLS
            return;
        }
        
        // Create the base folder for the copied files
        copiedFilesFolder = Paths.get(caseFolder.toString(), FILE_FOLDER_NAME).toFile();
        if (! copiedFilesFolder.mkdir()) {
            handleError("Error creating folder " + copiedFilesFolder.toString(),
                    Bundle.PortableCaseReportModule_createCase_errorCreatingFolder(copiedFilesFolder.toString()), null, progressPanel); // NON-NLS
            return;
        }
        
        // Create subfolders for the copied files
        for (FileTypeCategory cat:FILE_TYPE_CATEGORIES) {
            File subFolder = Paths.get(copiedFilesFolder.toString(), cat.getDisplayName()).toFile();
            if (! subFolder.mkdir()) {
                handleError("Error creating folder " + subFolder.toString(),
                    Bundle.PortableCaseReportModule_createCase_errorCreatingFolder(subFolder.toString()), null, progressPanel);    // NON-NLS
                return;
            }
        }
        File unknownTypeFolder = Paths.get(copiedFilesFolder.toString(), UNKNOWN_FILE_TYPE_FOLDER).toFile();
        if (! unknownTypeFolder.mkdir()) {
            handleError("Error creating folder " + unknownTypeFolder.toString(),
                Bundle.PortableCaseReportModule_createCase_errorCreatingFolder(unknownTypeFolder.toString()), null, progressPanel);   // NON-NLS 
            return;
        }
                
    }
    
    /**
     * Save the current highest IDs to the portable case.
     * 
     * @throws TskCoreException 
     */
    private void saveHighestIds() throws TskCoreException {
        
        CaseDbAccessManager currentCaseDbManager = currentCase.getSleuthkitCase().getCaseDbAccessManager();
        
        String tableSchema = "( table_name TEXT PRIMARY KEY, "
                            + " max_id TEXT)"; // NON-NLS
        
        portableSkCase.getCaseDbAccessManager().createTable(MAX_ID_TABLE_NAME, tableSchema);

        currentCaseDbManager.select("max(obj_id) as max_id from tsk_objects", new StoreMaxIdCallback("tsk_objects")); // NON-NLS
        currentCaseDbManager.select("max(tag_id) as max_id from content_tags", new StoreMaxIdCallback("content_tags")); // NON-NLS
        currentCaseDbManager.select("max(tag_id) as max_id from blackboard_artifact_tags", new StoreMaxIdCallback("blackboard_artifact_tags"));  // NON-NLS
        currentCaseDbManager.select("max(examiner_id) as max_id from tsk_examiners", new StoreMaxIdCallback("tsk_examiners"));  // NON-NLS
    }
    
    /**
     * Add all files with a given tag to the portable case.
     * 
     * @param oldTagName    The TagName object from the current case
     * @param progressPanel The progress panel
     * 
     * @throws TskCoreException 
     */
    private void addFilesToPortableCase(TagName oldTagName, ReportProgressPanel progressPanel) throws TskCoreException {
        
        // Get all the tags in the current case
        List<ContentTag> tags = currentCase.getServices().getTagsManager().getContentTagsByTagName(oldTagName);
        
        // Copy the files into the portable case and tag
        for (ContentTag tag : tags) {
            
            // Check for cancellation 
            if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                return;
            }
            
            Content content = tag.getContent();
            if (content instanceof AbstractFile) {
                long newFileId = copyContentToPortableCase(content, progressPanel);
                
                // Tag the file
                if (! oldTagNameToNewTagName.containsKey(tag.getName())) {
                    throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName()); // NON-NLS
                }
                portableSkCase.addContentTag(newIdToContent.get(newFileId), oldTagNameToNewTagName.get(tag.getName()), tag.getComment(), tag.getBeginByteOffset(), tag.getEndByteOffset());
            }
        }  
    }  
    
    /**
     * Add all artifacts with a given tag to the portable case.
     * 
     * @param oldTagName    The TagName object from the current case
     * @param progressPanel The progress panel
     * 
     * @throws TskCoreException 
     */
    private void addArtifactsToPortableCase(TagName oldTagName, ReportProgressPanel progressPanel) throws TskCoreException {
       
        List<BlackboardArtifactTag> tags = currentCase.getServices().getTagsManager().getBlackboardArtifactTagsByTagName(oldTagName);
        
        // Copy the artifacts into the portable case along with their content and tag
        for (BlackboardArtifactTag tag : tags) {
            
            // Check for cancellation 
            if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                return;
            }
            
            // Copy the source content
            Content content = tag.getContent();
            long newContentId = copyContentToPortableCase(content, progressPanel);
            
            // Copy the artifact
            BlackboardArtifact newArtifact = copyArtifact(newContentId, tag.getArtifact());
            
            // Tag the artfiact
            if (! oldTagNameToNewTagName.containsKey(tag.getName())) {
                throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName()); // NON-NLS
            }
            portableSkCase.addBlackboardArtifactTag(newArtifact, oldTagNameToNewTagName.get(tag.getName()), tag.getComment());
        }  
    }    
    
    /**
     * Copy an artifact into the new case. Will also copy any associated artifacts
     * 
     * @param newContentId   The content ID (in the portable case) of the source content
     * @param artifactToCopy The artifact to copy
     * 
     * @return The new artifact in the portable case
     * 
     * @throws TskCoreException 
     */
    private BlackboardArtifact copyArtifact(long newContentId, BlackboardArtifact artifactToCopy) throws TskCoreException {
        
        if (oldArtifactIdToNewArtifact.containsKey(artifactToCopy.getArtifactID())) {
            return oldArtifactIdToNewArtifact.get(artifactToCopy.getArtifactID());
        }
        
        // First create the associated artifact (if present)
        BlackboardAttribute oldAssociatedAttribute = artifactToCopy.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT));
        List<BlackboardAttribute> newAttrs = new ArrayList<>();
        if (oldAssociatedAttribute != null) {
            BlackboardArtifact oldAssociatedArtifact = currentCase.getSleuthkitCase().getBlackboardArtifact(oldAssociatedAttribute.getValueLong());
            BlackboardArtifact newAssociatedArtifact = copyArtifact(newContentId, oldAssociatedArtifact);
            newAttrs.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, 
                        String.join(",", oldAssociatedAttribute.getSources()), newAssociatedArtifact.getArtifactID()));
        }
        
        // Create the new artifact
        int newArtifactTypeId = getNewArtifactTypeId(artifactToCopy);
        BlackboardArtifact newArtifact = portableSkCase.newBlackboardArtifact(newArtifactTypeId, newContentId);
        List<BlackboardAttribute> oldAttrs = artifactToCopy.getAttributes();
        
        // Copy over each attribute, making sure the type is in the new case.
        for (BlackboardAttribute oldAttr:oldAttrs) {
            
            // The associated artifact has already been handled
            if (oldAttr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID()) {
                continue;
            }
                
            BlackboardAttribute.Type newAttributeType = getNewAttributeType(oldAttr);
            switch (oldAttr.getValueType()) {
                case BYTE:
                    newAttrs.add(new BlackboardAttribute(newAttributeType, String.join(",", oldAttr.getSources()),
                            oldAttr.getValueBytes()));
                    break;
                case DOUBLE:
                    newAttrs.add(new BlackboardAttribute(newAttributeType, String.join(",", oldAttr.getSources()),
                            oldAttr.getValueDouble()));
                    break;
                case INTEGER:
                    newAttrs.add(new BlackboardAttribute(newAttributeType, String.join(",", oldAttr.getSources()),
                            oldAttr.getValueInt()));
                    break;
                case DATETIME:    
                case LONG:
                    newAttrs.add(new BlackboardAttribute(newAttributeType, String.join(",", oldAttr.getSources()),
                            oldAttr.getValueLong()));
                    break;
                case STRING:
                    newAttrs.add(new BlackboardAttribute(newAttributeType, String.join(",", oldAttr.getSources()),
                            oldAttr.getValueString()));
                    break;
                default:
                    throw new TskCoreException("Unexpected attribute value type found: " + oldAttr.getValueType().getLabel()); // NON-NLS
            }
        }
        
        newArtifact.addAttributes(newAttrs);
        
        oldArtifactIdToNewArtifact.put(artifactToCopy.getArtifactID(), newArtifact);
        return newArtifact;
    }
    
    /**
     * Get the artifact type ID in the portable case and create new artifact type if needed.
     * For built-in artifacts this will be the same as the original.
     * 
     * @param oldArtifact The artifact in the current case
     * 
     * @return The corresponding artifact type ID in the portable case
     */
    private int getNewArtifactTypeId(BlackboardArtifact oldArtifact) throws TskCoreException {
        if (oldArtTypeIdToNewArtTypeId.containsKey(oldArtifact.getArtifactTypeID())) {
            return oldArtTypeIdToNewArtTypeId.get(oldArtifact.getArtifactTypeID());
        }
        
        BlackboardArtifact.Type oldCustomType = currentCase.getSleuthkitCase().getArtifactType(oldArtifact.getArtifactTypeName());
        try {
            BlackboardArtifact.Type newCustomType = portableSkCase.addBlackboardArtifactType(oldCustomType.getTypeName(), oldCustomType.getDisplayName());
            oldArtTypeIdToNewArtTypeId.put(oldArtifact.getArtifactTypeID(), newCustomType.getTypeID());
            return newCustomType.getTypeID();
        } catch (TskDataException ex) {
            throw new TskCoreException("Error creating new artifact type " + oldCustomType.getTypeName(), ex); // NON-NLS
        }
    }
    
    /**
     * Get the attribute type ID in the portable case and create new attribute type if needed.
     * For built-in attributes this will be the same as the original.
     * 
     * @param oldAttribute The attribute in the current case
     * 
     * @return The corresponding attribute type in the portable case
     */
    private BlackboardAttribute.Type getNewAttributeType(BlackboardAttribute oldAttribute) throws TskCoreException {
        BlackboardAttribute.Type oldAttrType = oldAttribute.getAttributeType();
        if (oldAttrTypeIdToNewAttrType.containsKey(oldAttrType.getTypeID())) {
            return oldAttrTypeIdToNewAttrType.get(oldAttrType.getTypeID());
        }
        
        try {
            BlackboardAttribute.Type newCustomType = portableSkCase.addArtifactAttributeType(oldAttrType.getTypeName(), 
                    oldAttrType.getValueType(), oldAttrType.getDisplayName());
            oldAttrTypeIdToNewAttrType.put(oldAttribute.getAttributeType().getTypeID(), newCustomType);
            return newCustomType;
        } catch (TskDataException ex) {
            throw new TskCoreException("Error creating new attribute type " + oldAttrType.getTypeName(), ex); // NON-NLS
        }
    }

    /**
     * Top level method to copy a content object to the portable case.
     * 
     * @param content       The content object to copy
     * @param progressPanel The progress panel
     * 
     * @return The object ID of the copied content in the portable case
     * 
     * @throws TskCoreException 
     */
    @NbBundle.Messages({
        "# {0} - File name",
        "PortableCaseReportModule.copyContentToPortableCase.copyingFile=Copying file {0}",  
    })    
    private long copyContentToPortableCase(Content content, ReportProgressPanel progressPanel) throws TskCoreException {
        progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_copyContentToPortableCase_copyingFile(content.getUniquePath()));
        return copyContent(content);
    }
    
    /**
     * Returns the object ID for the given content object in the portable case.
     * 
     * @param content The content object to copy into the portable case
     * 
     * @return the new object ID for this content
     * 
     * @throws TskCoreException 
     */
    private long copyContent(Content content) throws TskCoreException {
                
        // Check if we've already copied this content
        if (oldIdToNewContent.containsKey(content.getId())) {
            return oldIdToNewContent.get(content.getId()).getId();
        }
        
        // Otherwise:
        // - Make parent of this object (if applicable)
        // - Copy this content
        long parentId = 0;
        if (content.getParent() != null) {
            parentId = copyContent(content.getParent());
        }
        
        Content newContent;
        if (content instanceof BlackboardArtifact) {
            BlackboardArtifact artifactToCopy = (BlackboardArtifact)content;
            newContent = copyArtifact(parentId, artifactToCopy);
        } else {
            CaseDbTransaction trans = portableSkCase.beginTransaction();
            try {
                if (content instanceof Image) {
                    Image image = (Image)content;
                    newContent = portableSkCase.addImage(image.getType(), image.getSsize(), image.getSize(), image.getName(), 
                            new ArrayList<>(), image.getTimeZone(), image.getMd5(), image.getSha1(), image.getSha256(), image.getDeviceId(), trans);
                } else if (content instanceof VolumeSystem) {
                    VolumeSystem vs = (VolumeSystem)content;
                    newContent = portableSkCase.addVolumeSystem(parentId, vs.getType(), vs.getOffset(), vs.getBlockSize(), trans);
                } else if (content instanceof Volume) {
                    Volume vs = (Volume)content;
                    newContent = portableSkCase.addVolume(parentId, vs.getAddr(), vs.getStart(), vs.getLength(), 
                            vs.getDescription(), vs.getFlags(), trans);
                } else if (content instanceof FileSystem) {
                    FileSystem fs = (FileSystem)content;
                    newContent = portableSkCase.addFileSystem(parentId, fs.getImageOffset(), fs.getFsType(), fs.getBlock_size(), 
                            fs.getBlock_count(), fs.getRoot_inum(), fs.getFirst_inum(), fs.getLastInum(), 
                            fs.getName(), trans);
                } else if (content instanceof BlackboardArtifact) {
                    BlackboardArtifact artifactToCopy = (BlackboardArtifact)content;
                    newContent = copyArtifact(parentId, artifactToCopy);
                } else if (content instanceof AbstractFile) {
                    AbstractFile abstractFile = (AbstractFile)content;
            
                    if (abstractFile instanceof LocalFilesDataSource) {
                        LocalFilesDataSource localFilesDS = (LocalFilesDataSource)abstractFile;
                        newContent = portableSkCase.addLocalFilesDataSource(localFilesDS.getDeviceId(), localFilesDS.getName(), localFilesDS.getTimeZone(), trans);    
                    } else {
                        if (abstractFile.isDir()) {
                            newContent = portableSkCase.addLocalDirectory(parentId, abstractFile.getName(), trans);
                        } else {
                            try {
                                // Copy the file
                                String fileName = abstractFile.getId() + "-" + FileUtil.escapeFileName(abstractFile.getName());
                                String exportSubFolder = getExportSubfolder(abstractFile);
                                File exportFolder = Paths.get(copiedFilesFolder.toString(), exportSubFolder).toFile();
                                File localFile = new File(exportFolder, fileName);
                                ContentUtils.writeToFile(abstractFile, localFile);

                                // Get the new parent object in the portable case database
                                Content oldParent = abstractFile.getParent();
                                if (! oldIdToNewContent.containsKey(oldParent.getId())) {
                                    throw new TskCoreException("Parent of file with ID " + abstractFile.getId() + " has not been created"); // NON-NLS
                                }
                                Content newParent = oldIdToNewContent.get(oldParent.getId());

                                // Construct the relative path to the copied file
                                String relativePath = FILE_FOLDER_NAME + File.separator +  exportSubFolder + File.separator + fileName;

                                newContent = portableSkCase.addLocalFile(abstractFile.getName(), relativePath, abstractFile.getSize(),
                                        abstractFile.getCtime(), abstractFile.getCrtime(), abstractFile.getAtime(), abstractFile.getMtime(),
                                        abstractFile.getMd5Hash(), abstractFile.getKnown(), abstractFile.getMIMEType(),
                                        true, TskData.EncodingType.NONE, 
                                        newParent, trans);
                            } catch (IOException ex) {
                                throw new TskCoreException("Error copying file " + abstractFile.getName() + " with original obj ID " 
                                        + abstractFile.getId(), ex); // NON-NLS
                            }
                        }
                    }
                } else {
                    throw new TskCoreException("Trying to copy unexpected Content type " + content.getClass().getName()); // NON-NLS
                }
                trans.commit();
            }  catch (TskCoreException ex) {
                trans.rollback();
                throw(ex);
            }
        }
        
        // Save the new object
        oldIdToNewContent.put(content.getId(), newContent);
        newIdToContent.put(newContent.getId(), newContent);
        return oldIdToNewContent.get(content.getId()).getId();
    }
    
    /**
     * Return the subfolder name for this file based on MIME type
     * 
     * @param abstractFile the file
     * 
     * @return the name of the appropriate subfolder for this file type 
     */
    private String getExportSubfolder(AbstractFile abstractFile) {
        if (abstractFile.getMIMEType() == null || abstractFile.getMIMEType().isEmpty()) {
            return UNKNOWN_FILE_TYPE_FOLDER;
        }
        
        for (FileTypeCategory cat:FILE_TYPE_CATEGORIES) {
            if (cat.getMediaTypes().contains(abstractFile.getMIMEType())) {
                return cat.getDisplayName();
            }
        }
        return UNKNOWN_FILE_TYPE_FOLDER;
    }
    
    /**
     * Clear out the maps and other fields and close the database connections.
     */
    private void cleanup() {
        oldIdToNewContent.clear();
        newIdToContent.clear();
        oldTagNameToNewTagName.clear();
        oldArtTypeIdToNewArtTypeId.clear();
        oldAttrTypeIdToNewAttrType.clear();
        oldArtifactIdToNewArtifact.clear();

        closePortableCaseDatabase();
        
        currentCase = null;
        caseFolder = null;
        copiedFilesFolder = null;
    }
    
    /**
     * Close the portable case
     */
    private void closePortableCaseDatabase() {
        if (portableSkCase != null) {
            portableSkCase.close();
            portableSkCase = null;
        }
    }

    /*@Override
    public JPanel getConfigurationPanel() {
        configPanel = new CreatePortableCasePanel();
        return configPanel;
    }    */
    
    private class StoreMaxIdCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        private final String tableName;
        
        StoreMaxIdCallback(String tableName) {
            this.tableName = tableName;
        }
        
        @Override
        public void process(ResultSet rs) {

            try {
                while (rs.next()) {
                    try {
                        Long maxId = rs.getLong("max_id"); // NON-NLS
                        String query = " (table_name, max_id) VALUES ('" + tableName + "', '" + maxId + "')"; // NON-NLS
                        portableSkCase.getCaseDbAccessManager().insert(MAX_ID_TABLE_NAME, query);

                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get maximum ID from result set", ex); // NON-NLS
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Unable to save maximum ID from result set", ex); // NON-NLS
                    }
                    
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get maximum ID from result set", ex); // NON-NLS
            }
        }
    }
    
    @NbBundle.Messages({
        "PortableCaseReportModule.compressCase.errorFinding7zip=Could not locate 7-Zip executable",
        "# {0} - Temp folder path",
        "PortableCaseReportModule.compressCase.errorCreatingTempFolder=Could not create temporary folder {0}",
        "PortableCaseReportModule.compressCase.errorCompressingCase=Error compressing case",
        "PortableCaseReportModule.compressCase.canceled=Compression canceled by user",
    }) 
    private boolean compressCase(ReportProgressPanel progressPanel) {
    
        // Close the portable case database (we still need some of the variables that would be cleared by cleanup())
        closePortableCaseDatabase();
        
        // Make a temporary folder for the compressed case
        File tempZipFolder = Paths.get(currentCase.getTempDirectory(), "portableCase" + System.currentTimeMillis()).toFile(); // NON-NLS
        if (! tempZipFolder.mkdir()) {
            handleError("Error creating temporary folder " + tempZipFolder.toString(), 
                    Bundle.PortableCaseReportModule_compressCase_errorCreatingTempFolder(tempZipFolder.toString()), null, progressPanel); // NON-NLS
            return false;
        }
        
        // Find 7-Zip
        File sevenZipExe = locate7ZipExecutable();
        if (sevenZipExe == null) {
            handleError("Error finding 7-Zip exectuable", Bundle.PortableCaseReportModule_compressCase_errorFinding7zip(), null, progressPanel); // NON-NLS
            return false;
        }
        
        // Create the chunk option
        String chunkOption = "";
        if (options.getChunkSize() != ChunkSize.NONE) {
            chunkOption = "-v" + options.getChunkSize().getSevenZipParam();
        }
        
        File zipFile = Paths.get(tempZipFolder.getAbsolutePath(), caseName + ".zip").toFile(); // NON-NLS
        ProcessBuilder procBuilder = new ProcessBuilder();
        procBuilder.command(
                sevenZipExe.getAbsolutePath(),
                "a",     // Add to archive
                zipFile.getAbsolutePath(),
                caseFolder.getAbsolutePath(),
                chunkOption
        );
        
        try {
            Process process = procBuilder.start();
            
            while (process.isAlive()) {
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    process.destroy();
                    return false;
                }
                Thread.sleep(200);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                // Save any errors so they can be logged
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append(System.getProperty("line.separator")); // NON-NLS
                    }
                }
                
                handleError("Error compressing case\n7-Zip output: " + sb.toString(), Bundle.PortableCaseReportModule_compressCase_errorCompressingCase(), null, progressPanel); // NON-NLS
                return false;
            }
        } catch (IOException | InterruptedException ex) {
            handleError("Error compressing case", Bundle.PortableCaseReportModule_compressCase_errorCompressingCase(), ex, progressPanel); // NON-NLS
            return false;
        }
        
        // Delete everything in the case folder then copy over the compressed file(s)
        try {
            FileUtils.cleanDirectory(caseFolder);
            FileUtils.copyDirectory(tempZipFolder, caseFolder);
            FileUtils.deleteDirectory(tempZipFolder);
        } catch (IOException ex) {
            handleError("Error compressing case", Bundle.PortableCaseReportModule_compressCase_errorCompressingCase(), ex, progressPanel); // NON-NLS
            return false;
        }
     
        return true;
    }
    
    /**
     * Locate the 7-Zip executable from the release folder.
     *
     * @return 7-Zip executable
     */
    private static File locate7ZipExecutable() {
        if (!PlatformUtil.isWindowsOS()) {
            return null;
        }

        String executableToFindName = Paths.get("7-Zip", "7z.exe").toString(); // NON-NLS
        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, PortableCaseReportModule.class.getPackage().getName(), false);
        if (null == exeFile) {
            return null;
        }

        if (!exeFile.canExecute()) {
            return null;
        }

        return exeFile;
    }
    
    /**
     * Enum for storing the display name for each chunk type and the
     * parameter needed for 7-Zip.
     */
    enum ChunkSize {
        
        NONE("Do not split", ""), // NON-NLS
        ONE_HUNDRED_MB("Split into 100 MB chunks", "100m"),
        CD("Split into 700 MB chunks (CD)", "700m"),
        ONE_GB("Split into 1 GB chunks", "1000m"),
        DVD("Split into 4.5 GB chunks (DVD)", "4500m"); // NON-NLS
        
        private final String displayName;
        private final String sevenZipParam;

        /**
         * Create a chunk size object.
         * 
         * @param displayName
         * @param sevenZipParam 
         */
        private ChunkSize(String displayName, String sevenZipParam) {
            this.displayName = displayName;
            this.sevenZipParam = sevenZipParam;
        }
        
        String getDisplayName() {
            return displayName;
        }
        
        String getSevenZipParam() {
            return sevenZipParam;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Convenience class to hold the options from the config panel.
     */
    static class PortableCaseOptions {
        
        private final List<TagName> tagNames = new ArrayList<>();
        private final List<String> setNames = new ArrayList<>();
        private boolean compress;
        private ChunkSize chunkSize;
        
        PortableCaseOptions(List<String> setNames, List<TagName> tagNames,
                boolean compress, ChunkSize chunkSize) {
            this.setNames.addAll(setNames);
            this.tagNames.addAll(tagNames);
            this.compress = compress;
            this.chunkSize = chunkSize;
        }
        
        PortableCaseOptions() {
            this.compress = false;
            this.chunkSize = ChunkSize.NONE;
        }
        
        void updateSetNames(List<String> setNames) {
            this.setNames.clear();
            this.setNames.addAll(setNames);
        }
        
        void updateTagNames(List<TagName> tagNames) {
            this.tagNames.clear();
            this.tagNames.addAll(tagNames);
        }
        
        void updateCompression(boolean compress, ChunkSize chunkSize) {
            this.compress = compress;
            this.chunkSize = chunkSize;
        }
        
        boolean isValid() {
            return (( !setNames.isEmpty()) || ( ! tagNames.isEmpty()));
        }
        
        List<String> getSelectedSetNames() {
            return new ArrayList<>(setNames);
        }
        
        List<TagName> getSelectedTagNames() {
            return new ArrayList<>(tagNames);
        }
        
        boolean shouldCompress() {
            return compress;
        }
        
        ChunkSize getChunkSize() {
            return chunkSize;
        }
    }
}
