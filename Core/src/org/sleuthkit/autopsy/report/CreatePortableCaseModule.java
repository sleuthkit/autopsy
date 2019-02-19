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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
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
    private CreatePortableCasePanel configPanel;
    
    // These are the types for the exported file subfolders
    private static final List<FileTypeCategory> FILE_TYPE_CATEGORIES = Arrays.asList(FileTypeCategory.AUDIO, FileTypeCategory.DOCUMENTS,
            FileTypeCategory.EXECUTABLE, FileTypeCategory.IMAGE, FileTypeCategory.VIDEO);
    
    private Case currentCase = null;
    private SleuthkitCase skCase = null;
    private File caseFolder = null;
    private File copiedFilesFolder = null;
    
    // Maps old object ID from current case to new object in portable case
    private final Map<Long, Content> oldIdToNewContent = new HashMap<>();
    
    // Maps new object ID to the new object
    private final Map<Long, Content> newIdToContent = new HashMap<>();
    
    // Maps old TagName to new TagName
    private final Map<TagName, TagName> oldTagNameToNewTagName = new HashMap<>();
    
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
        "# {0} - output folder",
        "CreatePortableCaseModule.generateReport.outputDirDoesNotExist=Output folder {0} does not exist",
        "# {0} - output folder",
        "CreatePortableCaseModule.generateReport.outputDirIsNotDir=Output folder {0} is not a folder",
        "CreatePortableCaseModule.generateReport.noTagsSelected=No tags selected for export.",
        "CreatePortableCaseModule.generateReport.caseClosed=Current case has been closed",
        "CreatePortableCaseModule.generateReport.errorCopyingTags=Error copying tags",
        "CreatePortableCaseModule.generateReport.errorCopyingFiles=Error copying tagged files"
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
        // skCase and caseFolder will be set here.
        progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_generateReport_creatingCase());
        createCase(outputDir, progressPanel);
        if (skCase == null) {
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
                TagName newTagName = skCase.addOrUpdateTagName(tagName.getDisplayName(), tagName.getDescription(), tagName.getColor(), tagName.getKnownStatus());
                oldTagNameToNewTagName.put(tagName, newTagName);
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tags", Bundle.CreatePortableCaseModule_generateReport_errorCopyingTags(), ex, progressPanel);
            return;
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

        // Close the case connections and clear out the maps
        cleanup();
        
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
        
    }

    /**
     * Create the case directory and case database. 
     * skCase will be set if this completes without error.
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
            skCase = currentCase.createPortableCase(caseName, caseFolder);
        } catch (TskCoreException ex) {
            handleError("Error creating case " + caseName + " in folder " + caseFolder.toString(),
                Bundle.CreatePortableCaseModule_createCase_errorCreatingCase(), ex, progressPanel);  
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
     * Add all files with a given tag to the portable case.
     * 
     * @param oldTagName
     * @param progressPanel
     * @throws TskCoreException 
     */
    @NbBundle.Messages({
        "# {0} - File name",
        "CreatePortableCaseModule.addFilesToPortableCase.copyingFile=Copying file {0}",  
    })
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
                AbstractFile file = (AbstractFile) content;
                String filePath = file.getParentPath() + file.getName();
                progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_addFilesToPortableCase_copyingFile(filePath));
                
                long newFileId;
                CaseDbTransaction trans = skCase.beginTransaction();
                try {
                    newFileId = copyContent(file, trans);
                    trans.commit();
                } catch (TskCoreException ex) {
                    trans.rollback();
                    throw(ex);
                }
                
                // Tag the file
                if (! oldTagNameToNewTagName.containsKey(tag.getName())) {
                    throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName());
                }
                skCase.addContentTag(newIdToContent.get(newFileId), oldTagNameToNewTagName.get(tag.getName()), tag.getComment(), tag.getBeginByteOffset(), tag.getEndByteOffset());
            }
        }  
    }
    
    /**
     * Returns the object ID for the given content object in the portable case.
     * 
     * @param content The content object to copy into the portable case
     * @param trans   The current transaction
     * 
     * @return the new object ID for this content
     * 
     * @throws TskCoreException 
     */
    private long copyContent(Content content, CaseDbTransaction trans) throws TskCoreException {
                
        // Check if we've already copied this content
        if (oldIdToNewContent.containsKey(content.getId())) {
            return oldIdToNewContent.get(content.getId()).getId();
        }
        
        // Otherwise:
        // - Make parent of this object (if applicable)
        // - Copy this content
        long parentId = 0;
        if (content.getParent() != null) {
            parentId = copyContent(content.getParent(), trans);
        }
        
        Content newContent;
        if (content instanceof Image) {
            Image image = (Image)content;
            newContent = skCase.addImage(image.getType(), image.getSsize(), image.getSize(), image.getName(), 
                    new ArrayList<>(), image.getTimeZone(), image.getMd5(), image.getSha1(), image.getSha256(), image.getDeviceId(), trans);
        } else if (content instanceof VolumeSystem) {
            VolumeSystem vs = (VolumeSystem)content;
            newContent = skCase.addVolumeSystem(parentId, vs.getType(), vs.getOffset(), vs.getBlockSize(), trans);
        } else if (content instanceof Volume) {
            Volume vs = (Volume)content;
            newContent = skCase.addVolume(parentId, vs.getAddr(), vs.getStart(), vs.getLength(), 
                    vs.getDescription(), vs.getFlags(), trans);
        } else if (content instanceof FileSystem) {
            FileSystem fs = (FileSystem)content;
            newContent = skCase.addFileSystem(parentId, fs.getImageOffset(), fs.getFsType(), fs.getBlock_size(), 
                    fs.getBlock_count(), fs.getRoot_inum(), fs.getFirst_inum(), fs.getLastInum(), 
                    fs.getName(), trans);
        } else if (content instanceof AbstractFile) {
            AbstractFile abstractFile = (AbstractFile)content;
            
            if (abstractFile instanceof LocalFilesDataSource) {
                LocalFilesDataSource localFilesDS = (LocalFilesDataSource)abstractFile;
                newContent = skCase.addLocalFilesDataSource(localFilesDS.getDeviceId(), localFilesDS.getName(), localFilesDS.getTimeZone(), trans);    
            } else {
                if (abstractFile.isDir()) {
                    newContent = skCase.addLocalDirectory(parentId, abstractFile.getName(), trans);
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

                        newContent = skCase.addLocalFile(abstractFile.getName(), relativePath, abstractFile.getSize(),
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
        currentCase = null;
        if (skCase != null) {
            // We want to close the database connections here but it is currently not possible. JIRA-4736
            skCase = null;
        }
        caseFolder = null;
        copiedFilesFolder = null;
    }
    

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new CreatePortableCasePanel();
        return configPanel;
    }    
}
