/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import org.apache.commons.io.FileUtils;
import org.openide.util.lookup.ServiceProvider;
import javax.swing.JPanel;
import java.util.logging.Level;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
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
 *
 */
@ServiceProvider(service = GeneralReportModule.class)
public class CreatePortableCaseModule implements GeneralReportModule {
    private static final Logger logger = Logger.getLogger(CreatePortableCaseModule.class.getName());
    private static final String FILE_FOLDER_NAME = "PortableCaseFiles";
    private CreatePortableCasePanel configPanel;
    
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
    
    CaseDbTransaction trans = null;
    
    public CreatePortableCaseModule() {
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
     * @param logWarning
     * @param dialogWarning
     * @param ex
     * @param progressPanel 
     */
    private void handleError(String logWarning, String dialogWarning, Exception ex, ReportProgressPanel progressPanel) {
        if (ex == null) {
            logger.log(Level.WARNING, logWarning); //NON-NLS
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
        "# {0} - Autopsy file",
        "CreatePortableCaseModule.createCase.errorWritingAutFile=Error writing to file {0}",  
        "CreatePortableCaseModule.createCase.errorCreatingDatabase=Error creating case database",
    })
    private void createCase(File outputDir, ReportProgressPanel progressPanel) {
        
        
        String caseName;

        // Create the case folder
        caseName = currentCase.getDisplayName() + " (Portable)";
        caseFolder = Paths.get(outputDir.toString(), caseName).toFile();

        if (caseFolder.exists()) {
            
            // TEMP TEMP TEMP !!!!!!!!!!!
            // JUST DELETE FOR TESTING
            try {
                FileUtils.deleteDirectory(caseFolder);
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
            
            //handleError("Case folder " + caseFolder.toString() + " already exists",
            //    Bundle.CreatePortableCaseModule_createCase_caseDirExists(caseFolder.toString()), null, progressPanel);  
            //return;
        }
        caseFolder.mkdirs();
            
        String dbFilePath = Paths.get(caseFolder.toString(), "autopsy.db").toString();

        // Put a fake .aut file in it (TEMP obviously)
        String autFileName = "PortableCaseTest.aut";
        File autFile = Paths.get(caseFolder.toString(), autFileName).toFile();
        try (PrintWriter writer = new PrintWriter(autFile, "UTF-8")) {
                String data =
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                        "<AutopsyCase>" +
                        "  <SchemaVersion>4.0</SchemaVersion>" +
                        "  <CreatedDate>2019/01/08 15:13:03 (EST)</CreatedDate>" +
                        "  <ModifiedDate>2019/01/08 15:13:05 (EST)</ModifiedDate>" +
                        "  <CreatedByAutopsyVersion>4.10.0</CreatedByAutopsyVersion>" +
                        "  <SavedByAutopsyVersion>4.10.0</SavedByAutopsyVersion>" +
                        "  <Case>" +
                        "    <Name>PortableCaseTest1_20190108_151303</Name>" +
                        "    <DisplayName>PortableCaseTest</DisplayName>" +
                        "    <Number/>" +
                        "    <Examiner/>" +
                        "    <ExaminerPhone/>" +
                        "    <ExaminerEmail/>" +
                        "    <CaseNotes/>" +
                        "    <CaseType>Single-user case</CaseType>" +
                        "    <Database/>" +
                        "    <CaseDatabase>autopsy.db</CaseDatabase>" +
                        "    <TextIndex/>" +
                        "  </Case>" +
                        "</AutopsyCase>";
                writer.println(data);    
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
            handleError("Error writing to file " + autFile.toString(),
                    Bundle.CreatePortableCaseModule_createCase_errorWritingAutFile(autFile), ex, progressPanel);
            return;
        }
        
        // Create the folder for the copied files
        copiedFilesFolder = Paths.get(caseFolder.toString(), FILE_FOLDER_NAME).toFile();
        copiedFilesFolder.mkdir();
        
        // Create the Sleuthkit case
        try {
            skCase = SleuthkitCase.newCase(dbFilePath);
        } catch (TskCoreException ex) {
            handleError("Error creating case database",
                    Bundle.CreatePortableCaseModule_createCase_errorCreatingDatabase(), ex, progressPanel);
            return;
        }
    }
    
    @NbBundle.Messages({
        "# {0} - File name",
        "CreatePortableCaseModule.addFilesToPortableCase.copyingFile=Copying file {0}",  
    })
    private void addFilesToPortableCase(TagName oldTagName, ReportProgressPanel progressPanel) throws TskCoreException {
        
        // Get all the tags in the current case
        List<ContentTag> tags = currentCase.getServices().getTagsManager().getContentTagsByTagName(oldTagName);
        
        System.out.println("\nFiles tagged with " + oldTagName.getDisplayName());
        
        // Copy the files into the portable case and tag
        for (ContentTag tag : tags) {
            Content content = tag.getContent();
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                String filePath = file.getParentPath() + file.getName();
                progressPanel.updateStatusLabel(Bundle.CreatePortableCaseModule_addFilesToPortableCase_copyingFile(filePath));
                
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {
                    
                }
                
                long newFileId;
                System.out.println("  Want to export file " + content.getName());
                trans = skCase.beginTransaction();
                try {
                    newFileId = copyContent(file);
                    System.out.println("  Exported file " + content.getName() + " Has new ID " + newFileId);
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
     * @param child
     * @return
     * @throws TskCoreException 
     */
    private long copyContent(Content content) throws TskCoreException {
                
        System.out.println("copyContent: " + content.getName() + " " + content.getId());
        if (oldIdToNewContent.containsKey(content.getId())) {
            System.out.println("  In map - new ID = " + oldIdToNewContent.get(content.getId()).getId());
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
        if (content instanceof Image) {
            Image image = (Image)content;
            newContent = skCase.addImage(image.getType(), image.getSsize(), image.getSize(), image.getName(), 
                    new ArrayList<>(), image.getTimeZone(), image.getMd5(), image.getSha1(), image.getSha256(), image.getDeviceId(), trans);
            System.out.println("  Created new image " + image.getName() + " with obj ID " + newContent.getId());
        } else if (content instanceof VolumeSystem) {
            VolumeSystem vs = (VolumeSystem)content;
            newContent = skCase.addVolumeSystem(parentId, vs.getType(), vs.getOffset(), vs.getBlockSize(), trans);
            System.out.println("  Created new vs " + vs.getName() + " with obj ID " +  newContent.getId());
        } else if (content instanceof Volume) {
            Volume vs = (Volume)content;
            newContent = skCase.addVolume(parentId, vs.getAddr(), vs.getStart(), vs.getLength(), 
                    vs.getDescription(), vs.getFlags(), trans);
            System.out.println("  Created new volume " + vs.getName() + " with obj ID " + newContent.getId());
        } else if (content instanceof FileSystem) {
            FileSystem fs = (FileSystem)content;
            newContent = skCase.addFileSystem(parentId, fs.getImageOffset(), fs.getFsType(), fs.getBlock_size(), 
                    fs.getBlock_count(), fs.getRoot_inum(), fs.getFirst_inum(), fs.getLastInum(), 
                    fs.getName(), trans);
            System.out.println("  Created new file system " + fs.getName() + " with obj ID " + newContent.getId());
        } else if (content instanceof AbstractFile) {
            AbstractFile abstractFile = (AbstractFile)content;
            
            if (abstractFile instanceof LocalFilesDataSource) {
                LocalFilesDataSource localFilesDS = (LocalFilesDataSource)abstractFile;
                newContent = skCase.addLocalFilesDataSource(localFilesDS.getDeviceId(), localFilesDS.getName(), localFilesDS.getTimeZone(), trans);                
                System.out.println("  Created new local file data source " + abstractFile.getName() + " with obj ID " + newContent.getId());   
            } else {
                if (abstractFile.isDir()) {
                    
                    newContent = skCase.addLocalDirectory(parentId, abstractFile.getName(), trans);
                    System.out.println("  Created new local directory " + abstractFile.getName() + " with obj ID " + newContent.getId()
                            + " (" + abstractFile.getClass().getName() + ")");   
                    if (abstractFile.isRoot()) {
                        System.out.println("    That was the root directory");
                    }
                } else {
                    try {
                        // Copy the file
                        String fileName = abstractFile.getId() + "-" + FileUtil.escapeFileName(abstractFile.getName());
                        File localFile= new File(copiedFilesFolder, fileName);
                        System.out.println("###   Copying to file " + localFile.getAbsolutePath());
                        ContentUtils.writeToFile(abstractFile, localFile);

                        // Construct the relative path to the copied file
                        String relativePath = FILE_FOLDER_NAME + File.separator + fileName;
                        
                        // Get the new parent object in the portable case database
                        Content oldParent = abstractFile.getParent();
                        if (! oldIdToNewContent.containsKey(oldParent.getId())) {
                            System.out.println("Old parent ID: " + oldParent.getId() + ", new parent ID: " + parentId);
                            throw new TskCoreException("Parent has not been created");
                        }
                        Content newParent = oldIdToNewContent.get(oldParent.getId());

                        newContent = skCase.addLocalFile(abstractFile.getName(), relativePath, abstractFile.getSize(),
                                abstractFile.getCtime(), abstractFile.getCrtime(), abstractFile.getAtime(), abstractFile.getMtime(),
                                true, TskData.EncodingType.NONE, 
                                newParent, trans);
                        ((AbstractFile)newContent).setKnown(abstractFile.getKnown());
                        ((AbstractFile)newContent).setMIMEType(abstractFile.getMIMEType());
                        ((AbstractFile)newContent).setMd5Hash(abstractFile.getMd5Hash());
                        ((AbstractFile)newContent).save(trans);
                        
                        System.out.println("  Creating new local file " + abstractFile.getName() + " with obj ID " + newContent.getId() 
                                + " (" + abstractFile.getClass().getName() + ")");   
                    } catch (IOException ex) {
                        throw new TskCoreException("Error copying file " + abstractFile.getName() + " with original obj ID " 
                                + abstractFile.getId(), ex);
                    }
                }
            }
            
        } else {
            // Uh oh?
            System.out.println("  Oh no!!! Trying to copy instance of " + content.getClass().getName());
            throw new TskCoreException("Unknown type!!!");
        }
        
        // Save the new object
        oldIdToNewContent.put(content.getId(), newContent);
        newIdToContent.put(newContent.getId(), newContent);
        return oldIdToNewContent.get(content.getId()).getId();
    }
    
    /**
     * Clear out the maps and other fields
     */
    private void cleanup() {
        oldIdToNewContent.clear();
        newIdToContent.clear();
        oldTagNameToNewTagName.clear();
        currentCase = null;
        if (skCase != null) {
            // Do not call close() here! It will close all the handles in the JNI cache. 
            skCase.closeConnections();
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
