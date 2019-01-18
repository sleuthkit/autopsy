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
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Analyzes data sources using heuristics to determine which types of operating
 * systems the images may have been used by.
 *
 */
@Messages({"DataSourceUsageAnalyzer.parentModuleName=Recent Activity"})
class DataSourceUsageAnalyzer extends Extract {

    private static final Logger logger = Logger.getLogger(DataSourceUsageAnalyzer.class.getName());
    private static final String WINDOWS_VOLUME_PATH = "/windows/system32";
    private Content dataSource;

    @Messages({
        "# {0} - OS name",
        "DataSourceUsageAnalyzer.customVolume.label=OS Drive ({0})"
    })
    @Override
    void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        try {
            createDataSourceUsageArtifacts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained a volume with operating system specific files", ex);
        }

    }

    /**
     * Create TSK_DATA_SOURCE_USAGE artifacts based on OS_INFO artifacts
     * existing as well as other criteria such as specific paths existing.
     *
     * @throws TskCoreException
     */
    private void createDataSourceUsageArtifacts() throws TskCoreException {
        boolean windowsOsDetected = false;
        List<BlackboardArtifact> osInfoArtifacts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO);
        for (BlackboardArtifact osInfoArt : osInfoArtifacts) {
            //if it is the current data source
            if (osInfoArt.getDataSource().getId() == dataSource.getId()) {
                BlackboardAttribute progNameAttr = osInfoArt.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));
                if (progNameAttr != null) {
                    String dataSourceUsageDescription = "";
                    if (progNameAttr.getDisplayString().toLowerCase().contains("windows")) { //non-nls
                        windowsOsDetected = true;
                        //use the program name when it appears to be windows
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_customVolume_label(progNameAttr.getDisplayString());
                    } else {
                        ExtractOs.OS_TYPE osType = ExtractOs.OS_TYPE.fromOsInfoLabel(moduleName);
                        if (osType != null) {
                            dataSourceUsageDescription = osType.getDsUsageLabel();
                        } else {
                            //unable to determine name for DATA_SOURCE_USAGE artifact using program name
                            dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_customVolume_label(progNameAttr.getDisplayString());
                        }
                    }
                    createDataSourceUsageArtifact(dataSourceUsageDescription);
                }
            }
        }
        if (!windowsOsDetected) {  //if we didn't find a windows OS_INFO artifact check if we still think it is a windows volume
            if (osSpecificVolumeFilesExist(Arrays.asList(WINDOWS_VOLUME_PATH))) {
                createDataSourceUsageArtifact(Bundle.DataSourceUsageAnalyzer_windowsVolume_label());
            }
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
        if (!dataSourceUsageDescription.isEmpty()) {
            //if the data source usage description is not empty create a data source usage artifact if an Usage artifact does not already exist with the same description
            List<BlackboardArtifact> artifacts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource.getId());
            for (BlackboardArtifact artifact : artifacts) {
                if (artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION)).getValueString().equals(dataSourceUsageDescription)) {
                    return; //already exists don't create a duplicate
                }
            }
            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                    Bundle.DataSourceUsageAnalyzer_parentModuleName(),
                    dataSourceUsageDescription)); //NON-NLS
            addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource, bbattributes);
        }
    }

    /**
     * Check if any of the specified file paths exist, if they do return true
     * otherwise return false.
     *
     * @param filesToCheckFor - List of file paths to check for
     *
     * @return true if any specified files exist false if none exist
     */
    private boolean osSpecificVolumeFilesExist(List<String> filesToCheckFor) throws TskCoreException {
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> files = new ArrayList<>();
        for (String filePath : filesToCheckFor) {
            files.addAll(fileManager.findFiles(dataSource, FilenameUtils.getName(filePath), FilenameUtils.getPath(filePath)));
        }
        return !files.isEmpty();
    }
}
