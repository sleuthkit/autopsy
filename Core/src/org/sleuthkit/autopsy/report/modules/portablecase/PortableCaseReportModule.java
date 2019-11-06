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
package org.sleuthkit.autopsy.report.modules.portablecase;

import org.sleuthkit.autopsy.report.ReportModule;
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
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.modules.caseuco.CaseUcoFormatExporter;
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
public class PortableCaseReportModule implements ReportModule {
    private static final Logger logger = Logger.getLogger(PortableCaseReportModule.class.getName());
    private static final String FILE_FOLDER_NAME = "PortableCaseFiles";  // NON-NLS
    private static final String UNKNOWN_FILE_TYPE_FOLDER = "Other";  // NON-NLS
    private static final String MAX_ID_TABLE_NAME = "portable_case_max_ids";  // NON-NLS
    private PortableCaseReportModuleSettings settings;
    
    // These are the types for the exported file subfolders
    private static final List<FileTypeCategory> FILE_TYPE_CATEGORIES = Arrays.asList(FileTypeCategory.AUDIO, FileTypeCategory.DOCUMENTS,
            FileTypeCategory.EXECUTABLE, FileTypeCategory.IMAGE, FileTypeCategory.VIDEO);
    
    private Case currentCase = null;
    private SleuthkitCase portableSkCase = null;
    private String caseName = "";
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
    
    public PortableCaseReportModule() {
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
        try {
            caseName = Case.getCurrentCaseThrows().getDisplayName() + " (Portable)"; // NON-NLS
        } catch (NoCurrentCaseException ex) {
            // a case may not be open yet
            return "";
        }
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
        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, dialogWarning);
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
        "PortableCaseReportModule.generateReport.errorReadingTags=Error while reading tags from case database",
        "PortableCaseReportModule.generateReport.errorReadingSets=Error while reading interesting items sets from case database",
        "PortableCaseReportModule.generateReport.noContentToCopy=No interesting files, results, or tagged items to copy",
        "PortableCaseReportModule.generateReport.errorCopyingTags=Error copying tags",
        "PortableCaseReportModule.generateReport.errorCopyingFiles=Error copying tagged files",
        "PortableCaseReportModule.generateReport.errorCopyingArtifacts=Error copying tagged artifacts",
        "PortableCaseReportModule.generateReport.errorCopyingInterestingFiles=Error copying interesting files",
        "PortableCaseReportModule.generateReport.errorCopyingInterestingResults=Error copying interesting results",
        "PortableCaseReportModule.generateReport.errorCreatingImageTagTable=Error creating image tags table",
        "# {0} - attribute type name",
        "PortableCaseReportModule.generateReport.errorLookingUpAttrType=Error looking up attribute type {0}",
        "PortableCaseReportModule.generateReport.compressingCase=Compressing case...",
        "PortableCaseReportModule.generateReport.errorCreatingReportFolder=Could not make report folder",
        "PortableCaseReportModule.generateReport.errorGeneratingUCOreport=Problem while generating CASE-UCO report"
    })

    public void generateReport(String reportPath, PortableCaseReportModuleSettings options, ReportProgressPanel progressPanel) {
        this.settings = options;
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
            caseName = currentCase.getDisplayName() + " (Portable)"; // NON-NLS
        } catch (NoCurrentCaseException ex) {
            handleError("Current case has been closed",
                    Bundle.PortableCaseReportModule_generateReport_caseClosed(), null, progressPanel); // NON-NLS
            return;
        } 
        
        // Check that there will be something to copy
        List<TagName> tagNames;
        if (options.areAllTagsSelected()) {
            try {
                tagNames = Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();
            } catch (NoCurrentCaseException | TskCoreException ex) {
                handleError("Unable to get all tags", 
                    Bundle.PortableCaseReportModule_generateReport_errorReadingTags(), ex, progressPanel); // NON-NLS
                return;
            }
        } else {
            tagNames = options.getSelectedTagNames();
        }
        
        List<String> setNames;
        if (options.areAllSetsSelected()) {
            try {
                setNames = getAllInterestingItemsSets();
            } catch (NoCurrentCaseException | TskCoreException ex) {
                handleError("Unable to get all interesting items sets", 
                    Bundle.PortableCaseReportModule_generateReport_errorReadingSets(), ex, progressPanel); // NON-NLS
                return;
            }
        } else {
            setNames = options.getSelectedSetNames();
        }
        
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
        
        // Set up the table for the image tags
        try {
            initializeImageTags(progressPanel);
        } catch (TskCoreException ex) {
            handleError("Error creating image tag table", Bundle.PortableCaseReportModule_generateReport_errorCreatingImageTagTable(), ex, progressPanel); // NON-NLS
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
            handleError("Could not make report folder", Bundle.PortableCaseReportModule_generateReport_errorCreatingReportFolder(), null, progressPanel); // NON-NLS
            return;
        }
        
        try {
            CaseUcoFormatExporter.export(tagNames, setNames, reportsFolder, progressPanel);
        } catch (IOException | SQLException | NoCurrentCaseException | TskCoreException ex) {
            handleError("Problem while generating CASE-UCO report", 
                    Bundle.PortableCaseReportModule_generateReport_errorGeneratingUCOreport(), ex, progressPanel); // NON-NLS
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
    
    private List<String> getAllInterestingItemsSets() throws NoCurrentCaseException, TskCoreException {

        // Get the set names in use for the current case.
        List<String> setNames = new ArrayList<>();
        Map<String, Long> setCounts;

        // There may not be a case open when configuring report modules for Command Line execution
        // Get all SET_NAMEs from interesting item artifacts
        String innerSelect = "SELECT (value_text) AS set_name FROM blackboard_attributes WHERE (artifact_type_id = '"
                + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() + "' OR artifact_type_id = '"
                + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() + "') AND attribute_type_id = '"
                + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + "'"; // NON-NLS

        // Get the count of each SET_NAME
        String query = "set_name, count(1) AS set_count FROM (" + innerSelect + ") set_names GROUP BY set_name"; // NON-NLS

        GetInterestingItemSetNamesCallback callback = new GetInterestingItemSetNamesCallback();
        Case.getCurrentCaseThrows().getSleuthkitCase().getCaseDbAccessManager().select(query, callback);
        setCounts = callback.getSetCountMap();
        setNames.addAll(setCounts.keySet());
        return setNames;
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
     * Set up the image tag table in the portable case
     * 
     * @param progressPanel 
     * 
     * @throws TskCoreException 
     */
    private void initializeImageTags(ReportProgressPanel progressPanel) throws TskCoreException {
  
        // Create the image tags table in the portable case
        CaseDbAccessManager portableDbAccessManager = portableSkCase.getCaseDbAccessManager();
        if (! portableDbAccessManager.tableExists(ContentViewerTagManager.TABLE_NAME)) {
            portableDbAccessManager.createTable(ContentViewerTagManager.TABLE_NAME, ContentViewerTagManager.TABLE_SCHEMA_SQLITE);
        }
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
                ContentTag newContentTag = portableSkCase.addContentTag(newIdToContent.get(newFileId), oldTagNameToNewTagName.get(tag.getName()), tag.getComment(), tag.getBeginByteOffset(), tag.getEndByteOffset());

                // Get the image tag data associated with this tag (empty string if there is none)
                // and save it if present
                String appData = getImageTagDataForContentTag(tag);
                if (! appData.isEmpty()) {
                    addImageTagToPortableCase(newContentTag, appData);
                }
            }
        }  
    }  
    
    /**
     * Gets the image tag data for a given content tag
     * 
     * @param tag The ContentTag in the current case
     * 
     * @return The app_data string for this content tag or an empty string if there was none
     * 
     * @throws TskCoreException 
     */
    private String getImageTagDataForContentTag(ContentTag tag) throws TskCoreException {

        GetImageTagCallback callback = new GetImageTagCallback();
        String query = "* FROM " + ContentViewerTagManager.TABLE_NAME + " WHERE content_tag_id = " + tag.getId();
        currentCase.getSleuthkitCase().getCaseDbAccessManager().select(query, callback);
        return callback.getAppData();
    }
    
    /**
     * CaseDbAccessManager callback to get the app_data string for the image tag
     */
    private static class GetImageTagCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        private static final Logger logger = Logger.getLogger(PortableCaseReportModule.class.getName());
        private String appData = "";
        
        @Override
        public void process(ResultSet rs) {
            try {
                while (rs.next()) {
                    try {
                        appData = rs.getString("app_data"); // NON-NLS
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get app_data from result set", ex); // NON-NLS
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get next result for app_data", ex); // NON-NLS
            }
        }   
        
        /**
         * Get the app_data string
         * 
         * @return the app_data string
         */
        String getAppData() {
            return appData;
        }
    }
    
    /**
     * Add an image tag to the portable case.
     * 
     * @param newContentTag The content tag in the portable case
     * @param appData       The string to copy into app_data
     * 
     * @throws TskCoreException 
     */
    private void addImageTagToPortableCase(ContentTag newContentTag, String appData) throws TskCoreException {
        String insert = "(content_tag_id, app_data) VALUES (" + newContentTag.getId() + ", '" + appData + "')";
        portableSkCase.getCaseDbAccessManager().insert(ContentViewerTagManager.TABLE_NAME, insert);
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
                case JSON:
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
        if (settings.getChunkSize() != PortableCaseReportModuleSettings.ChunkSize.NONE) {
            chunkOption = "-v" + settings.getChunkSize().getSevenZipParam();
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
     * Processes the result sets from the interesting item set name query.
     */
    public static class GetInterestingItemSetNamesCallback implements CaseDbAccessManager.CaseDbAccessQueryCallback {

        private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GetInterestingItemSetNamesCallback.class.getName());
        private final Map<String, Long> setCounts = new HashMap<>();
        
        @Override
        public void process(ResultSet rs) {
            try {
                while (rs.next()) {
                    try {
                        Long setCount = rs.getLong("set_count"); // NON-NLS
                        String setName = rs.getString("set_name"); // NON-NLS

                        setCounts.put(setName, setCount);
                        
                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get data_source_obj_id or value from result set", ex); // NON-NLS
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get next result for values by datasource", ex); // NON-NLS
            }
        }   
        
        /**
         * Gets the counts for each interesting items set
         * 
         * @return A map from each set name to the number of items in it
         */
        public Map<String, Long> getSetCountMap() {
            return setCounts;
        }
    }
}
