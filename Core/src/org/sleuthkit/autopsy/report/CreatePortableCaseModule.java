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

import org.openide.util.lookup.ServiceProvider;
import javax.swing.JPanel;
import java.util.logging.Level;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.utils.FileTypeUtils.FileTypeCategory;
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
@ServiceProvider(service = GeneralReportModule.class)
public class CreatePortableCaseModule implements GeneralReportModule {
    private static final Logger logger = Logger.getLogger(CreatePortableCaseModule.class.getName());
    private static final String FILE_FOLDER_NAME = "PortableCaseFiles";
    private static final String UNKNOWN_FILE_TYPE_FOLDER = "Other";
    private static final String MAX_ID_TABLE_NAME = "portable_case_max_ids";
    private CreatePortableCasePanel configPanel;
    
    // These are the types for the exported file subfolders
    private static final List<FileTypeCategory> FILE_TYPE_CATEGORIES = Arrays.asList(FileTypeCategory.AUDIO, FileTypeCategory.DOCUMENTS,
            FileTypeCategory.EXECUTABLE, FileTypeCategory.IMAGE, FileTypeCategory.VIDEO);
    
    private Case currentCase = null;
    private SleuthkitCase portableSkCase = null;
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
    
    public CreatePortableCaseModule() {
        // Nothing to do here
    }

    @NbBundle.Messages({
        "CreatePortableCaseModule.getName.name=Portable Case"
    })
    @Override
    public String getName() {
        return Bundle.CreatePortableCaseModule_getName_name();
    }

    @NbBundle.Messages({
        "CreatePortableCaseModule.getDescription.description=Copies selected tagged items to a new single-user case that will work anywhere"
    })
    @Override
    public String getDescription() {
        return Bundle.CreatePortableCaseModule_getDescription_description();
    }

    @Override
    public String getRelativeFilePath() {
        return "";
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
        "CreatePortableCaseModule.generateReport.verifying=Verifying selected parameters...",
        "CreatePortableCaseModule.generateReport.creatingCase=Creating portable case database...",
        "CreatePortableCaseModule.generateReport.copyingTags=Copying tags...",
        "# {0} - tag name",
        "CreatePortableCaseModule.generateReport.copyingFiles=Copying files tagged as {0}...",
        "# {0} - tag name",
        "CreatePortableCaseModule.generateReport.copyingArtifacts=Copying artifacts tagged as {0}...",
        "# {0} - output folder",
        "CreatePortableCaseModule.generateReport.outputDirDoesNotExist=Output folder {0} does not exist",
        "# {0} - output folder",
        "CreatePortableCaseModule.generateReport.outputDirIsNotDir=Output folder {0} is not a folder",
        "CreatePortableCaseModule.generateReport.noTagsSelected=No tags selected for export.",
        "CreatePortableCaseModule.generateReport.caseClosed=Current case has been closed",
        "CreatePortableCaseModule.generateReport.errorCopyingTags=Error copying tags",
        "CreatePortableCaseModule.generateReport.errorCopyingFiles=Error copying tagged files",
        "CreatePortableCaseModule.generateReport.errorCopyingArtifacts=Error copying tagged artifacts",
        "# {0} - attribute type name",
        "CreatePortableCaseModule.generateReport.errorLookingUpAttrType=Error looking up attribute type {0}",
    })
    @Override
    public void generateReport(String reportPath, ReportProgressPanel progressPanel) {
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_generateReport_verifying());
        
        // Clear out any old values
        cleanup();
        
        // Validate the input parameters
        File outputDir = new File(configPanel.getOutputFolder());
        if (! outputDir.exists()) {
            handleError("Output folder " + outputDir.toString() + " does not exist",
                    Bundle.CreatePortableCaseModule_generateReport_outputDirDoesNotExist(outputDir.toString()), null, progressPanel);
            return;
        }
        
        if (! outputDir.isDirectory()) {
            handleError("Output folder " + outputDir.toString() + " is not a folder",
                    Bundle.CreatePortableCaseModule_generateReport_outputDirIsNotDir(outputDir.toString()), null, progressPanel);
            return;
        }
        
        List<TagName> tagNames = configPanel.getSelectedTagNames();
        if (tagNames.isEmpty()) {
            handleError("No tags selected for export",
                    Bundle.CreatePortableCaseModule_generateReport_noTagsSelected(), null, progressPanel);
            return;            
        }
        
        // Save the current case object
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            handleError("Current case has been closed",
                    Bundle.CreatePortableCaseModule_generateReport_caseClosed(), null, progressPanel);
            return;
        } 
        
        
        // Create the case.
        // portableSkCase and caseFolder will be set here.
        progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_generateReport_creatingCase());
        createCase(outputDir, progressPanel);
        if (portableSkCase == null) {
            // The error has already been handled
            return;
        }
        
        // Check for cancellation 
        if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
            cleanup();
            return;
        }
        
        // Copy the selected tags
        progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_generateReport_copyingTags());
        try {
            for(TagName tagName:tagNames) {
                TagName newTagName = portableSkCase.addOrUpdateTagName(tagName.getDisplayName(), tagName.getDescription(), tagName.getColor(), tagName.getKnownStatus());
                oldTagNameToNewTagName.put(tagName, newTagName);
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tags", Bundle.CreatePortableCaseModule_generateReport_errorCopyingTags(), ex, progressPanel);
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
                        Bundle.CreatePortableCaseModule_generateReport_errorLookingUpAttrType(type.getLabel()),
                        ex, progressPanel);
            }
        }        
        
        // Copy the tagged files
        try {
            for(TagName tagName:tagNames) {
                // Check for cancellation 
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    return;
                }
                progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_generateReport_copyingFiles(tagName.getDisplayName()));
                addFilesToPortableCase(tagName, progressPanel);
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tagged files", Bundle.CreatePortableCaseModule_generateReport_errorCopyingFiles(), ex, progressPanel);
            return;
        } 
        
        // Copy the tagged artifacts and associated files
        try {
            for(TagName tagName:tagNames) {
                // Check for cancellation 
                if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                    return;
                }
                progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_generateReport_copyingArtifacts(tagName.getDisplayName()));
                addArtifactsToPortableCase(tagName, progressPanel);
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tagged artifacts", Bundle.CreatePortableCaseModule_generateReport_errorCopyingArtifacts(), ex, progressPanel);
            return;
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
        "CreatePortableCaseModule.createCase.caseDirExists=Case folder {0} already exists",
        "CreatePortableCaseModule.createCase.errorCreatingCase=Error creating case",
        "# {0} - folder",
        "CreatePortableCaseModule.createCase.errorCreatingFolder=Error creating folder {0}",
        "CreatePortableCaseModule.createCase.errorStoringMaxIds=Error storing maximum database IDs",
    })
    private void createCase(File outputDir, ReportProgressPanel progressPanel) {

        // Create the case folder
        String caseName = currentCase.getDisplayName() + " (Portable)";
        caseFolder = Paths.get(outputDir.toString(), caseName).toFile();

        if (caseFolder.exists()) {
            handleError("Case folder " + caseFolder.toString() + " already exists",
                Bundle.CreatePortableCaseModule_createCase_caseDirExists(caseFolder.toString()), null, progressPanel);  
            return;
        }
        
        // Create the case
        try {
            portableSkCase = currentCase.createPortableCase(caseName, caseFolder);
        } catch (TskCoreException ex) {
            handleError("Error creating case " + caseName + " in folder " + caseFolder.toString(),
                Bundle.CreatePortableCaseModule_createCase_errorCreatingCase(), ex, progressPanel);  
            return;
        }
        
        // Store the highest IDs
        try {
            saveHighestIds();
        } catch (TskCoreException ex) {
            handleError("Error storing maximum database IDs",
                Bundle.CreatePortableCaseModule_createCase_errorStoringMaxIds(), ex, progressPanel);  
            return;
        }
        
        // Create the base folder for the copied files
        copiedFilesFolder = Paths.get(caseFolder.toString(), FILE_FOLDER_NAME).toFile();
        if (! copiedFilesFolder.mkdir()) {
            handleError("Error creating folder " + copiedFilesFolder.toString(),
                    Bundle.CreatePortableCaseModule_createCase_errorCreatingFolder(copiedFilesFolder.toString()), null, progressPanel);
            return;
        }
        
        // Create subfolders for the copied files
        for (FileTypeCategory cat:FILE_TYPE_CATEGORIES) {
            File subFolder = Paths.get(copiedFilesFolder.toString(), cat.getDisplayName()).toFile();
            if (! subFolder.mkdir()) {
                handleError("Error creating folder " + subFolder.toString(),
                    Bundle.CreatePortableCaseModule_createCase_errorCreatingFolder(subFolder.toString()), null, progressPanel);   
                return;
            }
        }
        File unknownTypeFolder = Paths.get(copiedFilesFolder.toString(), UNKNOWN_FILE_TYPE_FOLDER).toFile();
        if (! unknownTypeFolder.mkdir()) {
            handleError("Error creating folder " + unknownTypeFolder.toString(),
                Bundle.CreatePortableCaseModule_createCase_errorCreatingFolder(unknownTypeFolder.toString()), null, progressPanel);   
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
                            + " max_id TEXT)";
        
        portableSkCase.getCaseDbAccessManager().createTable(MAX_ID_TABLE_NAME, tableSchema);

        currentCaseDbManager.select("max(obj_id) as max_id from tsk_objects", new StoreMaxIdCallback("tsk_objects"));
        currentCaseDbManager.select("max(tag_id) as max_id from content_tags", new StoreMaxIdCallback("content_tags"));
        currentCaseDbManager.select("max(tag_id) as max_id from blackboard_artifact_tags", new StoreMaxIdCallback("blackboard_artifact_tags")); 
        currentCaseDbManager.select("max(examiner_id) as max_id from tsk_examiners", new StoreMaxIdCallback("tsk_examiners")); 
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
                    throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName());
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
                throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName());
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
                    throw new TskCoreException("Unexpected attribute value type found: " + oldAttr.getValueType().getLabel());
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
            throw new TskCoreException("Error creating new artifact type " + oldCustomType.getTypeName(), ex);
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
            throw new TskCoreException("Error creating new attribute type " + oldAttrType.getTypeName(), ex);
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
        "CreatePortableCaseModule.copyContentToPortableCase.copyingFile=Copying file {0}",  
    })    
    private long copyContentToPortableCase(Content content, ReportProgressPanel progressPanel) throws TskCoreException {
        progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_copyContentToPortableCase_copyingFile(content.getUniquePath()));
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
                                    throw new TskCoreException("Parent of file with ID " + abstractFile.getId() + " has not been created");
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
                                        + abstractFile.getId(), ex);
                            }
                        }
                    }
                } else {
                    throw new TskCoreException("Trying to copy unexpected Content type " + content.getClass().getName());
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
        
        currentCase = null;
        if (portableSkCase != null) {
            portableSkCase.close();
            portableSkCase = null;
        }
        caseFolder = null;
        copiedFilesFolder = null;
    }

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new CreatePortableCasePanel();
        return configPanel;
    }    
    
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
                        Long maxId = rs.getLong("max_id");
                        String query = " (table_name, max_id) VALUES ('" + tableName + "', '" + maxId + "')";
                        portableSkCase.getCaseDbAccessManager().insert(MAX_ID_TABLE_NAME, query);

                    } catch (SQLException ex) {
                        logger.log(Level.WARNING, "Unable to get maximum ID from result set", ex);
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Unable to save maximum ID from result set", ex);
                    }
                    
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to get maximum ID from result set", ex);
            }
        }
    }
}
