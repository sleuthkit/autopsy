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
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Analyzes data sources using heuristics to determine which types of operating
 * systems the images may have been used by.
 *
 */
@Messages({"DataSourceUsageAnalyzer.displayName=Data Source Usage Analyzer"})
class DataSourceUsageAnalyzer extends Extract {

    private static final Logger logger = Logger.getLogger(DataSourceUsageAnalyzer.class.getName());
    private static final int FAT_EXFAT_FLAGS = TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT16.getValue()
            | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_FAT32.getValue()
            | TskData.TSK_FS_TYPE_ENUM.TSK_FS_TYPE_EXFAT.getValue();
    private static final long HUNDRED_GB = 100 * 1024 * 1024 * 1024l;

    private static final String ANDROID_MEDIACARD_ROOT_FILENAMES[]
            = // files expected in root folder of an Android media card
            {".android_secure", "android", "audio",
                "photos", "dcim", "music", "pictures", "videos"}; //NON-NLS
    private Content dataSource;
    private final IngestJobContext context;

    DataSourceUsageAnalyzer(IngestJobContext context) {
        super(Bundle.DataSourceUsageAnalyzer_displayName(), context);
        this.context = context;
    }

    @Messages({
        "# {0} - OS name",
        "DataSourceUsageAnalyzer.customVolume.label=OS Drive ({0})",
        "Progress_Message_Analyze_Usage=Data Sources Usage Analysis",})
    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        try {
            progressBar.progress(Bundle.Progress_Message_Analyze_Usage());
            createDataSourceUsageArtifacts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained a volume with operating system specific files", ex);
        }

    }

    private void createDataSourceUsageArtifacts() throws TskCoreException {
        createOSInfoDataSourceUsageArtifacts();
        if (context.dataSourceIngestIsCancelled()) {
            return;
        }
        createAndroidMediaCardArtifacts();
        if (context.dataSourceIngestIsCancelled()) {
            return;
        }
        createDJIDroneDATArtitifacts();
    }

    /**
     * Create TSK_DATA_SOURCE_USAGE artifacts based on OS_INFO artifacts
     * existing as well as other criteria such as specific paths existing.
     *
     * @throws TskCoreException
     */
    private void createOSInfoDataSourceUsageArtifacts() throws TskCoreException {
        boolean windowsOsDetected = false;
        List<BlackboardArtifact> osInfoArtifacts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO);
        for (BlackboardArtifact osInfoArt : osInfoArtifacts) {
            //if it is the current data source
            if (osInfoArt.getDataSource().getId() == dataSource.getId()) {
                BlackboardAttribute progNameAttr = osInfoArt.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));
                if (progNameAttr != null) {
                    if (progNameAttr.getValueString().isEmpty()) {
                        //skip empty Program Name text
                    } else if (progNameAttr.getDisplayString().toLowerCase().contains("windows")) { //non-nls
                        windowsOsDetected = true;
                        //use the program name when it appears to be windows
                        createDataSourceUsageArtifact(Bundle.DataSourceUsageAnalyzer_customVolume_label(progNameAttr.getDisplayString()));
                    } else {
                        ExtractOs.OS_TYPE osType = ExtractOs.OS_TYPE.fromOsInfoLabel(progNameAttr.getValueString());
                        if (osType != null) {
                            createDataSourceUsageArtifact(osType.getDsUsageLabel());
                        } else {
                            //unable to determine name for DATA_SOURCE_USAGE artifact using program name
                            createDataSourceUsageArtifact(Bundle.DataSourceUsageAnalyzer_customVolume_label(progNameAttr.getDisplayString()));
                        }
                    }
                }
            }
        }
        if (!windowsOsDetected) {  //if we didn't find a windows OS_INFO artifact check if we still think it is a windows volume
            checkIfOsSpecificVolume(ExtractOs.OS_TYPE.WINDOWS);
        }
    }

    /**
     * If a TSK_DATA_SOURCE_USAGE artifact does not exist with the given
     * description create one.
     *
     * @param dataSourceUsageDescription the text for the description attribute
     *                                   of the TSK_DATA_SOURCE_USAGE artifact
     *
     * @throws TskCoreException
     */
    private void createDataSourceUsageArtifact(String dataSourceUsageDescription) throws TskCoreException {
        //if the data source usage description is not empty create a data source usage artifact if an Usage artifact does not already exist with the same description
        List<BlackboardArtifact> artifacts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource.getId());
        for (BlackboardArtifact artifact : artifacts) {
            if (artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION)).getValueString().equals(dataSourceUsageDescription)) {
                return; //already exists don't create a duplicate
            }
        }
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                getRAModuleName(),
                dataSourceUsageDescription)); //NON-NLS
        postArtifact(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_DATA_SOURCE_USAGE, dataSource, bbattributes));
    }

    /**
     * Check if any of the specified file paths exist for the specified OS_TYPE
     * exist, if they do create a TSK_DATA_SOURCE_USAGE artifact does if one
     * does not exist with the given description.
     *
     * @param osType - the OS_TYPE to check for
     */
    private void checkIfOsSpecificVolume(ExtractOs.OS_TYPE osType) throws TskCoreException {
        for (String filePath : osType.getFilePaths()) {
            for (AbstractFile file : currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource,
                    FilenameUtils.getName(filePath), FilenameUtils.getPath(filePath))) {
                createDataSourceUsageArtifact(osType.getDsUsageLabel());
                return;
            }
        }
    }

    /**
     * Checks to see if the data source might be an Android media card or a
     * Flash drive. If so, creates TSK_DATA_SOURCE_USAGE artifact.
     *
     * @return true if any specified files exist false if none exist
     *
     * @throws TskCoreException
     */
    @Messages({
        "DataSourceUsage_AndroidMedia=Android Media Card",
        "DataSourceUsage_FlashDrive=Flash Drive"
    })
    private void createAndroidMediaCardArtifacts() {

        if (dataSource instanceof Image) {
            Image image = (Image) dataSource;
            try {
                if (image.getSize() > HUNDRED_GB) {
                    return;
                }

                List<FileSystem> fileSystems = image.getFileSystems();
                if (fileSystems.isEmpty() || fileSystems.size() > 1) {
                    return;
                }

                FileSystem fileSystem = fileSystems.get(0);
                if (fileSystem == null || (fileSystem.getFsType().getValue() & FAT_EXFAT_FLAGS) == 0) {
                    return;
                }

                if (hasAndroidMediaCardRootNames()) {
                    return;
                }

                // If none of the Android paths is found but it meets other criteria, it might be just a flash drive
                createDataSourceUsageArtifact(Bundle.DataSourceUsage_FlashDrive());

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Exception while checking image: {0} for Andriod media card", image.getName() + ex.getMessage()); //NON-NLS
            }
        }
    }

    /**
     * Checks the data source for any android media card root files
     *
     * @return True if root files were found
     *
     * @throws TskCoreException
     */
    private boolean hasAndroidMediaCardRootNames() throws TskCoreException {
        FileManager fileManager = currentCase.getServices().getFileManager();
        for (String fileName : ANDROID_MEDIACARD_ROOT_FILENAMES) {
            for (AbstractFile file : fileManager.findFiles(dataSource, fileName, "/")) { // NON-NLS
                if (file.getParentPath().equals("/") && file.getName().equalsIgnoreCase(fileName)) { // NON-NLS
                    createDataSourceUsageArtifact(Bundle.DataSourceUsage_AndroidMedia());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks to see if the data source might be a DJI internal drone storage
     * card. If so, creates TSK_DATA_SOURCE_USAGE artifact.
     *
     * @return true if any specified files exist false if none exist
     *
     * @throws TskCoreException
     */
    @Messages({
        "DataSourceUsage_DJU_Drone_DAT=DJI Internal SD Card"
    })
    private void createDJIDroneDATArtitifacts() throws TskCoreException {
        FileManager fileManager = currentCase.getServices().getFileManager();
        // The underscores are SQL wild cards.
        List<AbstractFile> files = fileManager.findFiles(dataSource, "FLY___.DAT");
        if (files != null && !files.isEmpty()) {
            createDataSourceUsageArtifact(Bundle.DataSourceUsage_DJU_Drone_DAT());
        }
    }
}
