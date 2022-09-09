/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import org.sleuthkit.autopsy.report.ReportModule;
import java.util.logging.Level;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.caseuco.CaseUcoExporter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountManager;
import org.sleuthkit.datamodel.OsAccountManager.NotUserSIDException;
import org.sleuthkit.datamodel.OsAccountRealm;
import org.sleuthkit.datamodel.OsAccountRealmManager;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TaggingManager.ContentTagChange;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
import org.sleuthkit.datamodel.blackboardutils.CommunicationArtifactsHelper;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments;

/**
 * Creates a portable case from tagged files
 */
public class PortableCaseReportModule implements ReportModule {

    private static final Logger logger = Logger.getLogger(PortableCaseReportModule.class.getName());
    private static final String FILE_FOLDER_NAME = "PortableCaseFiles";  // NON-NLS
    private static final String UNKNOWN_FILE_TYPE_FOLDER = "Other";  // NON-NLS
    private static final String MAX_ID_TABLE_NAME = "portable_case_max_ids";  // NON-NLS
    private static final String CASE_UCO_FILE_NAME = "portable_CASE_UCO_output";
    private static final String CASE_UCO_TMP_DIR = "case_uco_tmp";
    private PortableCaseReportModuleSettings settings;

    // These are the types for the exported file subfolders
    private static final List<FileTypeCategory> FILE_TYPE_CATEGORIES = Arrays.asList(FileTypeCategory.AUDIO, FileTypeCategory.DOCUMENTS,
            FileTypeCategory.EXECUTABLE, FileTypeCategory.IMAGE, FileTypeCategory.VIDEO);

    // These are attribute types that have special handling and should not be copied
    // into the new artifact directly.
    private static final List<Integer> SPECIALLY_HANDLED_ATTRS = Arrays.asList(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT.getTypeID(),
            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS.getTypeID(), BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID());

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

    // Map of old OS account id to new OS account
    private final Map<Long, OsAccount> oldOsAccountIdToNewOsAccount = new HashMap<>();

    // Map of old OS account realm id to new OS account ream id
    private final Map<Long, OsAccountRealm> oldRealmIdToNewRealm = new HashMap<>();

    // Map of the old host id to the new host
    private final Map<Long, Host> oldHostIdToNewHost = new HashMap<>();

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
     * @param progressPanel The report progress panel
     */
    private void handleCancellation(ReportProgressPanel progressPanel) {
        logger.log(Level.INFO, "Portable case creation canceled by user"); // NON-NLS
        progressPanel.setIndeterminate(false);
        progressPanel.complete(ReportProgressPanel.ReportStatus.CANCELED);
        cleanup();
    }

    /**
     * Convenience method to avoid code duplication. Assumes that if an
     * exception is supplied then the error is SEVERE. Otherwise it is logged as
     * a WARNING.
     *
     * @param logWarning    Warning to write to the log
     * @param dialogWarning Warning to write to a pop-up window
     * @param ex            The exception (can be null)
     * @param progressPanel The report progress panel
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
        "PortableCaseReportModule.generateReport.errorCopyingAutopsy=Error copying application",
        "# {0} - attribute type name",
        "PortableCaseReportModule.generateReport.errorLookingUpAttrType=Error looking up attribute type {0}",
        "PortableCaseReportModule.generateReport.compressingCase=Compressing case...",
        "PortableCaseReportModule_generateReport_copyingAutopsy=Copying application..."
    })
    /**
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
    public void generateReport(String reportPath, PortableCaseReportModuleSettings options, ReportProgressPanel progressPanel) {
        this.settings = options;
        progressPanel.setIndeterminate(true);
        progressPanel.start();
        progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_verifying());

        // Clear out any old values
        cleanup();

        // Validate the input parameters
        File outputDir = new File(reportPath);
        if (!outputDir.exists()) {
            handleError("Output folder " + outputDir.toString() + " does not exist",
                    Bundle.PortableCaseReportModule_generateReport_outputDirDoesNotExist(outputDir.toString()), null, progressPanel); // NON-NLS
            return;
        }

        if (!outputDir.isDirectory()) {
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

        // If the applciation is included add an extra level to the directory structure
        if (options.includeApplication()) {
            outputDir = Paths.get(outputDir.toString(), caseName).toFile();
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
            for (TagName tagName : tagNames) {
                TagName newTagName = portableSkCase.getTaggingManager().addOrUpdateTagName(tagName.getDisplayName(), tagName.getDescription(), tagName.getColor(), tagName.getKnownStatus());
                oldTagNameToNewTagName.put(tagName, newTagName);
            }
        } catch (TskCoreException ex) {
            handleError("Error copying tags", Bundle.PortableCaseReportModule_generateReport_errorCopyingTags(), ex, progressPanel); // NON-NLS
            return;
        }

        // Set up tracking to support any custom artifact or attribute types
        for (BlackboardArtifact.ARTIFACT_TYPE type : BlackboardArtifact.ARTIFACT_TYPE.values()) {
            oldArtTypeIdToNewArtTypeId.put(type.getTypeID(), type.getTypeID());
        }
        for (BlackboardAttribute.ATTRIBUTE_TYPE type : BlackboardAttribute.ATTRIBUTE_TYPE.values()) {
            try {
                oldAttrTypeIdToNewAttrType.put(type.getTypeID(), portableSkCase.getBlackboard().getAttributeType(type.getLabel()));
            } catch (TskCoreException ex) {
                handleError("Error looking up attribute name " + type.getLabel(),
                        Bundle.PortableCaseReportModule_generateReport_errorLookingUpAttrType(type.getLabel()),
                        ex, progressPanel); // NON-NLS
            }
        }

        // Copy the tagged files
        try {
            for (TagName tagName : tagNames) {
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
            for (TagName tagName : tagNames) {
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
        if (!setNames.isEmpty()) {
            try {
                List<AnalysisResult> interestingFiles = currentCase.getSleuthkitCase().getBlackboard().getAnalysisResultsByType(BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getTypeID());
                for (AnalysisResult art : interestingFiles) {
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
                List<AnalysisResult> interestingResults = currentCase.getSleuthkitCase().getBlackboard().getAnalysisResultsByType(BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getTypeID());
                for (AnalysisResult art : interestingResults) {
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

            try {
                List<AnalysisResult> interestingResults = currentCase.getSleuthkitCase().getBlackboard().getAnalysisResultsByType(BlackboardArtifact.Type.TSK_INTERESTING_ITEM.getTypeID());
                for (AnalysisResult art : interestingResults) {
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
                handleError("Error copying interesting items", Bundle.PortableCaseReportModule_generateReport_errorCopyingInterestingResults(), ex, progressPanel); // NON-NLS
                return;
            }
        }

        // Check for cancellation 
        if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
            handleCancellation(progressPanel);
            return;
        }

        //Attempt to generate and included the CASE-UCO report.
        generateCaseUcoReport(tagNames, setNames, progressPanel);

        if (options.includeApplication()) {
            try {
                progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_copyingAutopsy());
                copyApplication(getApplicationBasePath(), outputDir.getAbsolutePath());
                createAppLaunchBatFile(outputDir.getAbsolutePath());
            } catch (IOException ex) {
                handleError("Error copying autopsy", Bundle.PortableCaseReportModule_generateReport_errorCopyingAutopsy(), ex, progressPanel); // NON-NLS
            }
        }

        // Compress the case (if desired)
        if (options.shouldCompress()) {
            progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateReport_compressingCase());

            if (!compressCase(progressPanel, options.includeApplication() ? outputDir.getAbsolutePath() : caseFolder.getAbsolutePath())) {
                // Errors have been handled already
                return;
            }

            // Check for cancellation 
            if (progressPanel.getStatus() == ReportProgressPanel.ReportStatus.CANCELED) {
                handleCancellation(progressPanel);
                return;
            }
        }

        // Close the case connections and clear out the maps
        cleanup();

        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);

    }

    /**
     * Generates a CASE-UCO report for all files that have a specified TagName
     * or TSK_INTERESTING artifacts that are flagged by the specified SET_NAMEs.
     *
     * Only one copy of the file will be saved in the report if it is the source
     * of more than one of the above.
     *
     * @param tagNames      TagNames to included in the report.
     * @param setNames      SET_NAMEs to include in the report.
     * @param progressPanel ProgressPanel to relay progress messages.
     */
    @NbBundle.Messages({
        "PortableCaseReportModule.generateCaseUcoReport.errorCreatingReportFolder=Could not make report folder",
        "PortableCaseReportModule.generateCaseUcoReport.errorGeneratingCaseUcoReport=Problem while generating CASE-UCO report",
        "PortableCaseReportModule.generateCaseUcoReport.startCaseUcoReportGeneration=Creating a CASE-UCO report of the portable case",
        "PortableCaseReportModule.generateCaseUcoReport.successCaseUcoReportGeneration=Successfully created a CASE-UCO report of the portable case"
    })
    private void generateCaseUcoReport(List<TagName> tagNames, List<String> setNames, ReportProgressPanel progressPanel) {
        //Create the 'Reports' directory to include a CASE-UCO report.
        Path reportsDirectory = Paths.get(caseFolder.toString(), "Reports");
        if (!reportsDirectory.toFile().mkdir()) {
            logger.log(Level.SEVERE, "Could not make the report folder... skipping "
                    + "CASE-UCO report generation for the portable case");
            return;
        }

        Path reportFile = reportsDirectory.resolve(CASE_UCO_FILE_NAME);

        progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateCaseUcoReport_startCaseUcoReportGeneration());
        try (OutputStream stream = new FileOutputStream(reportFile.toFile());
                JsonWriter reportWriter = new JsonWriter(new OutputStreamWriter(stream, "UTF-8"))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            reportWriter.setIndent("    ");
            reportWriter.beginObject();
            reportWriter.name("@graph");
            reportWriter.beginArray();

            String caseTempDirectory = currentCase.getTempDirectory();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            TagsManager tagsManager = currentCase.getServices().getTagsManager();

            //Create temp directory to filter out duplicate files.
            //Clear out the old directory if it exists.
            Path tmpDir = Paths.get(caseTempDirectory, CASE_UCO_TMP_DIR);
            FileUtils.deleteDirectory(tmpDir.toFile());
            Files.createDirectory(tmpDir);

            CaseUcoExporter exporter = new CaseUcoExporter(currentCase.getSleuthkitCase());
            for (JsonElement element : exporter.exportSleuthkitCase()) {
                gson.toJson(element, reportWriter);
            }

            //Load all interesting BlackboardArtifacts that belong to the selected SET_NAMEs
            //binned by data source id.
            Multimap<Long, BlackboardArtifact> artifactsWithSetName = getInterestingArtifactsBySetName(skCase, setNames);

            //Search each data source looking for content tags and interesting
            //items that match the selected tag names and set names.
            for (DataSource dataSource : currentCase.getSleuthkitCase().getDataSources()) {
                // Helper flag to ensure each data source is only written once in 
                // a report.
                boolean dataSourceHasBeenIncluded = false;

                //Search content tags and artifact tags that match
                for (TagName tagName : tagNames) {
                    for (ContentTag ct : tagsManager.getContentTagsByTagName(tagName, dataSource.getId())) {
                        dataSourceHasBeenIncluded |= addUniqueFile(ct.getContent(),
                                dataSource, tmpDir, gson, exporter, reportWriter, dataSourceHasBeenIncluded);
                    }
                    for (BlackboardArtifactTag bat : tagsManager.getBlackboardArtifactTagsByTagName(tagName, dataSource.getId())) {
                        dataSourceHasBeenIncluded |= addUniqueFile(bat.getContent(),
                                dataSource, tmpDir, gson, exporter, reportWriter, dataSourceHasBeenIncluded);
                    }
                }
                //Search artifacts that this data source contains
                for (BlackboardArtifact bArt : artifactsWithSetName.get(dataSource.getId())) {
                    Content sourceContent = bArt.getParent();
                    dataSourceHasBeenIncluded |= addUniqueFile(sourceContent, dataSource,
                            tmpDir, gson, exporter, reportWriter, dataSourceHasBeenIncluded);
                }
            }

            // Finish the report.
            reportWriter.endArray();
            reportWriter.endObject();
            progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateCaseUcoReport_successCaseUcoReportGeneration());
        } catch (IOException | TskCoreException ex) {
            progressPanel.updateStatusLabel(Bundle.PortableCaseReportModule_generateCaseUcoReport_errorGeneratingCaseUcoReport());
            logger.log(Level.SEVERE, "Error encountered while trying to create "
                    + "CASE-UCO output for portable case.. the portable case will be "
                    + "completed without a CASE-UCO report.", ex);
        }
    }

    /**
     * Load all interesting BlackboardArtifacts that belong to the selected
     * SET_NAME. This operation would be duplicated for every data source, since
     * the Sleuthkit API does not have a notion of searching by data source id.
     *
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
    private Multimap<Long, BlackboardArtifact> getInterestingArtifactsBySetName(SleuthkitCase skCase, List<String> setNames) throws TskCoreException {
        Multimap<Long, BlackboardArtifact> artifactsWithSetName = ArrayListMultimap.create();
        if (!setNames.isEmpty()) {
            List<BlackboardArtifact> allArtifacts = skCase.getBlackboardArtifacts(
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            allArtifacts.addAll(skCase.getBlackboardArtifacts(
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT));
            allArtifacts.addAll(skCase.getBlackboardArtifacts(
                    BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM));

            for (BlackboardArtifact bArt : allArtifacts) {
                BlackboardAttribute setAttr = bArt.getAttribute(
                        new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME));
                if (setNames.contains(setAttr.getValueString())) {
                    artifactsWithSetName.put(bArt.getDataSource().getId(), bArt);
                }
            }
        }
        return artifactsWithSetName;
    }

    /**
     * Adds the content if and only if it has not already been seen.
     *
     * @param content                   Content to add to the report.
     * @param dataSource                Parent dataSource of the content
     *                                  instance.
     * @param tmpDir                    Path to the tmpDir to enforce uniqueness
     * @param gson
     * @param exporter
     * @param reportWriter              Report generator instance to add the
     *                                  content to
     * @param dataSourceHasBeenIncluded Flag determining if the data source
     *                                  should be written to the report (false
     *                                  indicates that it should be written).
     *
     * @throws IOException      If an I/O error occurs.
     * @throws TskCoreException If an internal database error occurs.
     *
     * return True if the file was written during this operation.
     */
    private boolean addUniqueFile(Content content, DataSource dataSource,
            Path tmpDir, Gson gson, CaseUcoExporter exporter, JsonWriter reportWriter,
            boolean dataSourceHasBeenIncluded) throws IOException, TskCoreException {
        if (content instanceof AbstractFile && !(content instanceof DataSource)) {
            AbstractFile absFile = (AbstractFile) content;
            Path filePath = tmpDir.resolve(Long.toString(absFile.getId()));
            if (!absFile.isDir() && !Files.exists(filePath)) {
                if (!dataSourceHasBeenIncluded) {
                    for (JsonElement element : exporter.exportDataSource(dataSource)) {
                        gson.toJson(element, reportWriter);
                    }
                }
                String subFolder = getExportSubfolder(absFile);
                String fileName = absFile.getId() + "-" + FileUtil.escapeFileName(absFile.getName());
                for (JsonElement element : exporter.exportAbstractFile(absFile, Paths.get(FILE_FOLDER_NAME, subFolder, fileName).toString())) {
                    gson.toJson(element, reportWriter);
                }
                Files.createFile(filePath);
                return true;
            }
        }
        return false;
    }

    /**
     * @SuppressWarnings("deprecation") - we need to support already existing
     * interesting file and artifact hits.
     */
    @SuppressWarnings("deprecation")
    private List<String> getAllInterestingItemsSets() throws NoCurrentCaseException, TskCoreException {

        // Get the set names in use for the current case.
        List<String> setNames = new ArrayList<>();
        Map<String, Long> setCounts;

        // There may not be a case open when configuring report modules for Command Line execution
        // Get all SET_NAMEs from interesting item artifacts
        String innerSelect = "SELECT (value_text) AS set_name FROM blackboard_attributes WHERE (artifact_type_id = '"
                + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() + "' OR artifact_type_id = '"
                + BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ITEM.getTypeID() + "' OR artifact_type_id = '"
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
     * Create the case directory and case database. portableSkCase will be set
     * if this completes without error.
     *
     * @param outputDir     The parent for the case folder
     * @param progressPanel
     */
    @NbBundle.Messages({
        "# {0} - case folder",
        "PortableCaseReportModule.createCase.caseDirExists=Case folder {0} already exists",
        "PortableCaseReportModule.createCase.errorCreatingCase=Error creating case",
        "# {0} - folder",
        "PortableCaseReportModule.createCase.errorCreatingFolder=Error creating folder {0}",
        "PortableCaseReportModule.createCase.errorStoringMaxIds=Error storing maximum database IDs",})
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
        if (!copiedFilesFolder.mkdir()) {
            handleError("Error creating folder " + copiedFilesFolder.toString(),
                    Bundle.PortableCaseReportModule_createCase_errorCreatingFolder(copiedFilesFolder.toString()), null, progressPanel); // NON-NLS
            return;
        }

        // Create subfolders for the copied files
        for (FileTypeCategory cat : FILE_TYPE_CATEGORIES) {
            File subFolder = Paths.get(copiedFilesFolder.toString(), cat.getDisplayName()).toFile();
            if (!subFolder.mkdir()) {
                handleError("Error creating folder " + subFolder.toString(),
                        Bundle.PortableCaseReportModule_createCase_errorCreatingFolder(subFolder.toString()), null, progressPanel);    // NON-NLS
                return;
            }
        }
        File unknownTypeFolder = Paths.get(copiedFilesFolder.toString(), UNKNOWN_FILE_TYPE_FOLDER).toFile();
        if (!unknownTypeFolder.mkdir()) {
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
        if (!portableDbAccessManager.tableExists(ContentViewerTagManager.TABLE_NAME)) {
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
                if (!oldTagNameToNewTagName.containsKey(tag.getName())) {
                    throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName()); // NON-NLS
                }
                ContentTagChange newContentTag = portableSkCase.getTaggingManager().addContentTag(newIdToContent.get(newFileId), oldTagNameToNewTagName.get(tag.getName()), tag.getComment(), tag.getBeginByteOffset(), tag.getEndByteOffset());

                // Get the image tag data associated with this tag (empty string if there is none)
                // and save it if present
                String appData = getImageTagDataForContentTag(tag);
                if (!appData.isEmpty()) {
                    addImageTagToPortableCase(newContentTag.getAddedTag(), appData);
                }
            }
        }
    }

    /**
     * Gets the image tag data for a given content tag
     *
     * @param tag The ContentTag in the current case
     *
     * @return The app_data string for this content tag or an empty string if
     *         there was none
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

            // Copy any attachments
            copyAttachments(newArtifact, tag.getArtifact(), portableSkCase.getAbstractFileById(newContentId));

            // Copy any files associated with this artifact through the TSK_PATH_ID attribute
            copyPathID(newArtifact, tag.getArtifact());

            // Tag the artfiact
            if (!oldTagNameToNewTagName.containsKey(tag.getName())) {
                throw new TskCoreException("TagName map is missing entry for ID " + tag.getName().getId() + " with display name " + tag.getName().getDisplayName()); // NON-NLS
            }
            portableSkCase.getTaggingManager().addArtifactTag(newArtifact, oldTagNameToNewTagName.get(tag.getName()), tag.getComment());
        }
    }

    /**
     * Copy an artifact into the new case. Will also copy any associated
     * artifacts
     *
     * @param newContentId   The content ID (in the portable case) of the source
     *                       content
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

        List<BlackboardAttribute> oldAttrs = artifactToCopy.getAttributes();

        // Copy over each attribute, making sure the type is in the new case.
        for (BlackboardAttribute oldAttr : oldAttrs) {

            // Skip attributes that are handled elsewhere
            if (SPECIALLY_HANDLED_ATTRS.contains(oldAttr.getAttributeType().getTypeID())) {
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

        // Figure out the data source ID. We can't always get it from newContent because it could be null
        // for OS accounts, which means we also can't assume it's been added to the case already.
        Long newDataSourceId;
        if (newIdToContent.get(newContentId).getDataSource() != null) {
            // We can use the new content to get the id since the data source is in its parent hierarchy.
            // Everything would have already been copied to the portable case.
            newDataSourceId = newIdToContent.get(newContentId).getDataSource().getId();
        } else {
            // The newContent has no data source parent, so we'll have to use the old artifact.
            if (artifactToCopy.getDataSource() == null) {
                // Shouldn't happen with the current code
                throw new TskCoreException("Can not copy artifact with ID: " + artifactToCopy.getArtifactID() + " because it is not associated with a data source");
            }
            newDataSourceId = copyContent(artifactToCopy.getDataSource());
        }

        // Create the new artifact
        int newArtifactTypeId = getNewArtifactTypeId(artifactToCopy);
        BlackboardArtifact.Type newArtifactType = portableSkCase.getBlackboard().getArtifactType(newArtifactTypeId);
        BlackboardArtifact newArtifact;

        // First, check if the artifact being copied is an AnalysisResult or a DataArtifact. If it
        // is neither, attempt to reload it as the appropriate subclass.
        if (!((artifactToCopy instanceof AnalysisResult) || (artifactToCopy instanceof DataArtifact))) {
            try {
                if (newArtifactType.getCategory().equals(BlackboardArtifact.Category.ANALYSIS_RESULT)) {
                    AnalysisResult ar = currentCase.getSleuthkitCase().getBlackboard().getAnalysisResultById(artifactToCopy.getId());
                    if (ar != null) {
                        artifactToCopy = ar;
                    }
                } else {
                    DataArtifact da = currentCase.getSleuthkitCase().getBlackboard().getDataArtifactById(artifactToCopy.getId());
                    if (da != null) {
                        artifactToCopy = da;
                    }
                }
            } catch (TskCoreException ex) {
                // If the lookup failed, just use the orginal BlackboardArtifact
            }
        }

        try {
            if (artifactToCopy instanceof AnalysisResult) {
                AnalysisResult analysisResultToCopy = (AnalysisResult) artifactToCopy;
                newArtifact = portableSkCase.getBlackboard().newAnalysisResult(newArtifactType, newContentId,
                        newDataSourceId, analysisResultToCopy.getScore(),
                        analysisResultToCopy.getConclusion(), analysisResultToCopy.getConfiguration(),
                        analysisResultToCopy.getJustification(), newAttrs).getAnalysisResult();
            } else if (artifactToCopy instanceof DataArtifact) {
                DataArtifact dataArtifactToCopy = (DataArtifact) artifactToCopy;
                Long newOsAccountId = null;
                if (dataArtifactToCopy.getOsAccountObjectId().isPresent()) {
                    copyOsAccount(dataArtifactToCopy.getOsAccountObjectId().get());
                    newOsAccountId = oldOsAccountIdToNewOsAccount.get((dataArtifactToCopy.getOsAccountObjectId().get())).getId();
                }
                newArtifact = portableSkCase.getBlackboard().newDataArtifact(newArtifactType, newContentId,
                        newDataSourceId,
                        newAttrs, newOsAccountId);
            } else {
                if (newArtifactType.getCategory().equals(BlackboardArtifact.Category.ANALYSIS_RESULT)) {
                    newArtifact = portableSkCase.getBlackboard().newAnalysisResult(newArtifactType, newContentId,
                            newDataSourceId, Score.SCORE_NONE,
                            null, null, null, newAttrs).getAnalysisResult();
                } else {
                    newArtifact = portableSkCase.getBlackboard().newDataArtifact(newArtifactType, newContentId,
                            newDataSourceId,
                            newAttrs, null);
                }
            }
        } catch (BlackboardException ex) {
            throw new TskCoreException("Error copying artifact with ID: " + artifactToCopy.getId());
        }

        oldArtifactIdToNewArtifact.put(artifactToCopy.getArtifactID(), newArtifact);
        return newArtifact;
    }

    /**
     * Get the artifact type ID in the portable case and create new artifact
     * type if needed. For built-in artifacts this will be the same as the
     * original.
     *
     * @param oldArtifact The artifact in the current case
     *
     * @return The corresponding artifact type ID in the portable case
     */
    private int getNewArtifactTypeId(BlackboardArtifact oldArtifact) throws TskCoreException {
        if (oldArtTypeIdToNewArtTypeId.containsKey(oldArtifact.getArtifactTypeID())) {
            return oldArtTypeIdToNewArtTypeId.get(oldArtifact.getArtifactTypeID());
        }

        BlackboardArtifact.Type oldCustomType = currentCase.getSleuthkitCase().getBlackboard().getArtifactType(oldArtifact.getArtifactTypeName());
        try {
            BlackboardArtifact.Type newCustomType = portableSkCase.getBlackboard().getOrAddArtifactType(oldCustomType.getTypeName(), oldCustomType.getDisplayName());
            oldArtTypeIdToNewArtTypeId.put(oldArtifact.getArtifactTypeID(), newCustomType.getTypeID());
            return newCustomType.getTypeID();
        } catch (BlackboardException ex) {
            throw new TskCoreException("Error creating new artifact type " + oldCustomType.getTypeName(), ex); // NON-NLS
        }
    }

    /**
     * Get the attribute type ID in the portable case and create new attribute
     * type if needed. For built-in attributes this will be the same as the
     * original.
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
            BlackboardAttribute.Type newCustomType = portableSkCase.getBlackboard().getOrAddAttributeType(oldAttrType.getTypeName(),
                    oldAttrType.getValueType(), oldAttrType.getDisplayName());
            oldAttrTypeIdToNewAttrType.put(oldAttribute.getAttributeType().getTypeID(), newCustomType);
            return newCustomType;
        } catch (BlackboardException ex) {
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
        "PortableCaseReportModule.copyContentToPortableCase.copyingFile=Copying file {0}",})
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
            BlackboardArtifact artifactToCopy = (BlackboardArtifact) content;
            newContent = copyArtifact(parentId, artifactToCopy);
        } else if (content instanceof OsAccount) {
            newContent = copyOsAccount(content.getId());
        } else {
            // Get or create the host (if needed) before beginning transaction.
            Host newHost = null;
            if (content instanceof DataSource) {
                newHost = copyHost(((DataSource) content).getHost());
            }

            // Copy the associated OS account (if needed) before beginning transaction.
            if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (file.getOsAccountObjectId().isPresent()) {
                    copyOsAccount(file.getOsAccountObjectId().get());
                }
            }

            // Load the hashes if we have an image to avoid getting new connections with an open transaction.
            String md5 = "";
            String sha1 = "";
            String sha256 = "";
            if (content instanceof Image) {
                md5 = ((Image) content).getMd5();
                sha1 = ((Image) content).getSha1();
                sha256 = ((Image) content).getSha256();
            }

            CaseDbTransaction trans = portableSkCase.beginTransaction();
            try {
                if (content instanceof Image) {
                    Image image = (Image) content;
                    newContent = portableSkCase.addImage(image.getType(), image.getSsize(), image.getSize(), image.getName(),
                            new ArrayList<>(), image.getTimeZone(), md5, sha1, sha256, image.getDeviceId(), newHost, trans);
                } else if (content instanceof VolumeSystem) {
                    VolumeSystem vs = (VolumeSystem) content;
                    newContent = portableSkCase.addVolumeSystem(parentId, vs.getType(), vs.getOffset(), vs.getBlockSize(), trans);
                } else if (content instanceof Volume) {
                    Volume vs = (Volume) content;
                    newContent = portableSkCase.addVolume(parentId, vs.getAddr(), vs.getStart(), vs.getLength(),
                            vs.getDescription(), vs.getFlags(), trans);
                } else if (content instanceof Pool) {
                    Pool pool = (Pool) content;
                    newContent = portableSkCase.addPool(parentId, pool.getType(), trans);
                } else if (content instanceof FileSystem) {
                    FileSystem fs = (FileSystem) content;
                    newContent = portableSkCase.addFileSystem(parentId, fs.getImageOffset(), fs.getFsType(), fs.getBlock_size(),
                            fs.getBlock_count(), fs.getRoot_inum(), fs.getFirst_inum(), fs.getLastInum(),
                            fs.getName(), trans);
                } else if (content instanceof BlackboardArtifact) {
                    BlackboardArtifact artifactToCopy = (BlackboardArtifact) content;
                    newContent = copyArtifact(parentId, artifactToCopy);
                } else if (content instanceof AbstractFile) {
                    AbstractFile abstractFile = (AbstractFile) content;

                    if (abstractFile instanceof LocalFilesDataSource) {
                        LocalFilesDataSource localFilesDS = (LocalFilesDataSource) abstractFile;
                        newContent = portableSkCase.addLocalFilesDataSource(localFilesDS.getDeviceId(), localFilesDS.getName(), localFilesDS.getTimeZone(), newHost, trans);
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
                                if (!oldIdToNewContent.containsKey(oldParent.getId())) {
                                    throw new TskCoreException("Parent of file with ID " + abstractFile.getId() + " has not been created"); // NON-NLS
                                }
                                Content newParent = oldIdToNewContent.get(oldParent.getId());

                                // Construct the relative path to the copied file
                                String relativePath = FILE_FOLDER_NAME + File.separator + exportSubFolder + File.separator + fileName;

                                Long newOsAccountId = null;
                                if (abstractFile.getOsAccountObjectId().isPresent()) {
                                    newOsAccountId = oldOsAccountIdToNewOsAccount.get(abstractFile.getOsAccountObjectId().get()).getId();
                                }

                                newContent = portableSkCase.addLocalFile(abstractFile.getName(), relativePath, abstractFile.getSize(),
                                        abstractFile.getCtime(), abstractFile.getCrtime(), abstractFile.getAtime(), abstractFile.getMtime(),
                                        abstractFile.getMd5Hash(), abstractFile.getSha256Hash(), abstractFile.getKnown(), abstractFile.getMIMEType(),
                                        true, TskData.EncodingType.NONE,
                                        newOsAccountId, abstractFile.getOwnerUid().orElse(null),
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
            } catch (TskCoreException ex) {
                trans.rollback();
                throw (ex);
            }
        }

        // Save the new object
        oldIdToNewContent.put(content.getId(), newContent);
        newIdToContent.put(newContent.getId(), newContent);
        return oldIdToNewContent.get(content.getId()).getId();
    }

    /**
     * Copy a host into the portable case and add it to the oldHostIdToNewHost
     * map.
     *
     * @param oldHost The host to copy
     *
     * @return The new host
     *
     * @throws TskCoreException
     */
    private Host copyHost(Host oldHost) throws TskCoreException {
        Host newHost;
        if (oldHostIdToNewHost.containsKey(oldHost.getHostId())) {
            newHost = oldHostIdToNewHost.get(oldHost.getHostId());
        } else {
            newHost = portableSkCase.getHostManager().newHost(oldHost.getName());
            oldHostIdToNewHost.put(oldHost.getHostId(), newHost);
        }
        return newHost;
    }

    /**
     * Copy an OS Account to the new case and add it to the
     * oldOsAccountIdToNewOsAccountId map. Will also copy the associated realm.
     *
     * @param oldOsAccountId The OS account id in the current case.
     */
    private OsAccount copyOsAccount(Long oldOsAccountId) throws TskCoreException {
        // If it has already been copied, we're done.
        if (oldOsAccountIdToNewOsAccount.containsKey(oldOsAccountId)) {
            return oldOsAccountIdToNewOsAccount.get(oldOsAccountId);
        }

        // Load the OS account from the current case.
        OsAccountManager oldOsAcctManager = currentCase.getSleuthkitCase().getOsAccountManager();
        OsAccount oldOsAccount = oldOsAcctManager.getOsAccountByObjectId(oldOsAccountId);

        // Load the realm associated with the OS account.
        OsAccountRealmManager oldRealmManager = currentCase.getSleuthkitCase().getOsAccountRealmManager();
        OsAccountRealm oldRealm = oldRealmManager.getRealmByRealmId(oldOsAccount.getRealmId());

        // Copy the realm to the portable case if necessary.
        if (!oldRealmIdToNewRealm.containsKey(oldOsAccount.getRealmId())) {
            OsAccountRealmManager newRealmManager = portableSkCase.getOsAccountRealmManager();

            Host newHost = null;
            if (oldRealm.getScopeHost().isPresent()) {
                Host host = oldRealm.getScopeHost().get();
                newHost = copyHost(host);
            } else {
                if (oldRealm.getScope().equals(OsAccountRealm.RealmScope.DOMAIN)) {
                    // There is currently no way to get a domain-scoped host in Autopsy. When this changes
                    // we will need to update this code. This will require a new version of newWindowsRealm() that
                    // does not require a host, or some other way to create a realm with no host.
                    throw new TskCoreException("Failed to copy OsAccountRealm with ID=" + oldOsAccount.getRealmId() + " - can not currently handle domain-scoped hosts");
                } else {
                    throw new TskCoreException("Failed to copy OsAccountRealm with ID=" + oldOsAccount.getRealmId() + " because it is non-domain scoped but has no scope host");
                }
            }

            // We currently only support one realm name.
            String realmName = null;
            List<String> names = oldRealm.getRealmNames();
            if (!names.isEmpty()) {
                realmName = names.get(0);
            }

            try {
                OsAccountRealm newRealm = newRealmManager.newWindowsRealm(oldRealm.getRealmAddr().orElse(null), realmName, newHost, oldRealm.getScope());
                oldRealmIdToNewRealm.put(oldOsAccount.getRealmId(), newRealm);
            } catch (NotUserSIDException ex) {
                throw new TskCoreException("Failed to copy OsAccountRealm with ID=" + oldOsAccount.getRealmId(), ex);
            }
        }

        OsAccountManager newOsAcctManager = portableSkCase.getOsAccountManager();
        try {
            OsAccount newOsAccount = newOsAcctManager.newWindowsOsAccount(oldOsAccount.getAddr().orElse(null),
                    oldOsAccount.getLoginName().orElse(null), oldRealmIdToNewRealm.get(oldOsAccount.getRealmId()));
            oldOsAccountIdToNewOsAccount.put(oldOsAccountId, newOsAccount);
            return newOsAccount;
        } catch (NotUserSIDException ex) {
            throw new TskCoreException("Failed to copy OsAccount with ID=" + oldOsAccount.getId(), ex);
        }
    }

    /**
     * Copy path ID attribute to new case along with the referenced file.
     *
     * @param newArtifact The new artifact in the portable case. Should not have
     *                    a TSK_PATH_ID attribute.
     * @param oldArtifact The old artifact.
     *
     * @throws TskCoreException
     */
    private void copyPathID(BlackboardArtifact newArtifact, BlackboardArtifact oldArtifact) throws TskCoreException {
        // Get the path ID attribute
        BlackboardAttribute oldPathIdAttr = oldArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID));
        if (oldPathIdAttr != null) {
            // Copy the file and remake the attribute if the path ID is valid
            long oldContentId = oldPathIdAttr.getValueLong();
            if (oldContentId > 0) {
                Content oldContent = currentCase.getSleuthkitCase().getContentById(oldContentId);
                long newContentId = copyContent(oldContent);
                newArtifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID,
                        String.join(",", oldPathIdAttr.getSources()), newContentId));
            }
        }
    }

    /**
     * Copy attachments to the portable case.
     *
     * @param newArtifact The new artifact in the portable case. Should not have
     *                    a TSK_ATTACHMENTS attribute.
     * @param oldArtifact The old artifact.
     * @param newFile     The new file in the portable case associated with the
     *                    artifact.
     *
     * @throws TskCoreException
     */
    private void copyAttachments(BlackboardArtifact newArtifact, BlackboardArtifact oldArtifact, AbstractFile newFile) throws TskCoreException {
        // Get the attachments from TSK_ATTACHMENTS attribute.
        BlackboardAttribute attachmentsAttr = oldArtifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS));
        if (attachmentsAttr != null) {
            try {
                MessageAttachments msgAttachments = BlackboardJsonAttrUtil.fromAttribute(attachmentsAttr, MessageAttachments.class);

                Collection<MessageAttachments.FileAttachment> oldFileAttachments = msgAttachments.getFileAttachments();
                List<MessageAttachments.FileAttachment> newFileAttachments = new ArrayList<>();
                for (MessageAttachments.FileAttachment oldFileAttachment : oldFileAttachments) {
                    long attachedFileObjId = oldFileAttachment.getObjectId();
                    if (attachedFileObjId >= 0) {
                        // Copy the attached file and save to the MessageAttachments object
                        AbstractFile attachedFile = currentCase.getSleuthkitCase().getAbstractFileById(attachedFileObjId);
                        if (attachedFile == null) {
                            throw new TskCoreException("Error loading file with object ID " + attachedFileObjId + " from portable case");
                        }
                        long newFileID = copyContent(attachedFile);
                        newFileAttachments.add(new MessageAttachments.FileAttachment(portableSkCase.getAbstractFileById(newFileID)));
                    }
                }

                // Get the name of the module(s) that created the attachment
                String newSourceStr = "";
                List<String> oldSources = attachmentsAttr.getSources();
                if (!oldSources.isEmpty()) {
                    newSourceStr = String.join(",", oldSources);
                }

                // Add the attachment. The account type specified in the constructor will not be used.
                CommunicationArtifactsHelper communicationArtifactsHelper = new CommunicationArtifactsHelper(currentCase.getSleuthkitCase(),
                        newSourceStr, newFile, Account.Type.EMAIL, null);
                communicationArtifactsHelper.addAttachments(newArtifact, new MessageAttachments(newFileAttachments, msgAttachments.getUrlAttachments()));
            } catch (BlackboardJsonAttrUtil.InvalidJsonException ex) {
                throw new TskCoreException(String.format("Unable to parse json for MessageAttachments object in artifact: %s", oldArtifact.getName()), ex);
            }
        } else {    // backward compatibility - email message attachments are derived files, children of the message.
            for (Content childContent : oldArtifact.getChildren()) {
                if (childContent instanceof AbstractFile) {
                    copyContent(childContent);
                }
            }
        }
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

        for (FileTypeCategory cat : FILE_TYPE_CATEGORIES) {
            if (cat.getMediaTypes().contains(abstractFile.getMIMEType())) {
                return cat.getDisplayName();
            }
        }
        return UNKNOWN_FILE_TYPE_FOLDER;
    }

    /**
     * Returns base path of the users autopsy installation.
     *
     * @return Path of autopsy installation.
     */
    private Path getApplicationBasePath() {
        return getAutopsyExePath().getParent().getParent();
    }

    /**
     * Find the path of the installed version of autopsy.
     *
     * @return Path to the installed autopsy.exe.
     */
    private Path getAutopsyExePath() {
        // If this is an installed version, there should be an <appName>64.exe file in the bin folder
        String exeName = getAutopsyExeName();
        String installPath = PlatformUtil.getInstallPath();

        return Paths.get(installPath, "bin", exeName);
    }

    /**
     * Generate the name of the autopsy exe.
     *
     * @return The name of the autopsy exe.
     */
    private String getAutopsyExeName() {
        String appName = UserPreferences.getAppName();
        return appName + "64.exe";
    }

    /**
     * Copy the sorceFolder to destBaseFolder/appName.
     *
     * @param sourceFolder   Autopsy installation directory.
     * @param destBaseFolder Report base direction.
     *
     * @throws IOException
     */
    private void copyApplication(Path sourceFolder, String destBaseFolder) throws IOException {

        // Create an appName folder in the destination 
        Path destAppFolder = Paths.get(destBaseFolder, UserPreferences.getAppName());
        if (!destAppFolder.toFile().exists() && !destAppFolder.toFile().mkdirs()) {
            throw new IOException("Failed to create directory " + destAppFolder.toString());
        }

        // Now copy the files
        FileUtils.copyDirectory(sourceFolder.toFile(), destAppFolder.toFile());
    }

    /**
     * Create a bat file at destBaseFolder that will launch the portable case.
     *
     * @param destBaseFolder Folder to create the bat file in.
     *
     * @throws IOException
     */
    private void createAppLaunchBatFile(String destBaseFolder) throws IOException {
        Path filePath = Paths.get(destBaseFolder, "open.bat");
        String appName = UserPreferences.getAppName();
        String exePath = "\"%~dp0" + appName + "\\bin\\" + getAutopsyExeName() + "\"";
        String casePath = "..\\" + caseName;
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(exePath + " \"" + casePath + "\"");
        }
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
        oldOsAccountIdToNewOsAccount.clear();
        oldRealmIdToNewRealm.clear();
        oldHostIdToNewHost.clear();

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

    /*
     * @Override public JPanel getConfigurationPanel() { configPanel = new
     * CreatePortableCasePanel(); return configPanel; }
     */
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
        "PortableCaseReportModule.compressCase.canceled=Compression canceled by user",})
    private boolean compressCase(ReportProgressPanel progressPanel, String folderToCompress) {

        closePortableCaseDatabase();

        // Make a temporary folder for the compressed case
        Path dirToCompress = Paths.get(folderToCompress);
        File tempZipFolder = Paths.get(dirToCompress.getParent().toString(), "temp", "portableCase" + System.currentTimeMillis()).toFile();
        if (!tempZipFolder.mkdirs()) {
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
                "a", // Add to archive
                zipFile.getAbsolutePath(),
                dirToCompress.toAbsolutePath().toString(),
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
            FileUtils.cleanDirectory(dirToCompress.toFile());
            FileUtils.copyDirectory(tempZipFolder, dirToCompress.toFile());
            FileUtils.deleteDirectory(new File(tempZipFolder.getParent()));
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
