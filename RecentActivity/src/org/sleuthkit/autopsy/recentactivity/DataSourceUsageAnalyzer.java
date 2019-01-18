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
        "DataSourceUsageAnalyzer.customVolume.label=OS Drive ({0})",
        "DataSourceUsageAnalyzer.windowsVolume.label=OS Drive (Windows)",
        "DataSourceUsageAnalyzer.osxVolume.label=OS Drive (OS X)",
        "DataSourceUsageAnalyzer.androidVolume.label=OS Drive (Android)",
        "DataSourceUsageAnalyzer.redhatLinuxVolume.label=OS Drive (Linux Redhat)",
        "DataSourceUsageAnalyzer.novellSUSEVolume.label=OS Drive (Linux Novell SUSE)",
        "DataSourceUsageAnalyzer.fedoraLinuxVolume.label=OS Drive (Linux Fedora)",
        "DataSourceUsageAnalyzer.slackwareLinuxVolume.label=OS Drive (Linux Slackware)",
        "DataSourceUsageAnalyzer.debianLinuxVolume.label=OS Drive (Linux Debian)",
        "DataSourceUsageAnalyzer.mandrakeLinuxVolume.label=OS Drive (Linux Mandrake)",
        "DataSourceUsageAnalyzer.yellowDogLinuxVolume.label=OS Drive (Linux Yellow Dog)",
        "DataSourceUsageAnalyzer.sunJDSLinuxVolume.label=OS Drive (Linux Sun JDS)",
        "DataSourceUsageAnalyzer.solarisSparcVolume.label=OS Drive (Linux Solaris/Sparc)",
        "DataSourceUsageAnalyzer.gentooLinuxVolume.label=OS Drive (Linux Gentoo)",
        "DataSourceUsageAnalyzer.unitedLinuxVolume.label=OS Drive (Linux United Linux)",
        "DataSourceUsageAnalyzer.ubuntuLinuxVolume.label=OS Drive (Linux Ubuntu)"})
    @Override
    void process(Content dataSource, IngestJobContext context) {

        this.dataSource = dataSource;
        try {
            createDataSourceUsageArtifacts();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained a volume with operating system specific files", ex);
        }

    }

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
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_customVolume_label(progNameAttr.getDisplayString());
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_osx_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_osxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_androidOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_androidVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_redhatLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_redhatLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_novellSUSEOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_novellSUSEVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_fedoraLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_fedoraLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_slackwareLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_slackwareLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_debianLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_debianLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_mandrakeLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_mandrakeLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_yellowDogLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_yellowDogLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_sunJDSLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_sunJDSLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_solarisSparcOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_solarisSparcVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_gentooLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_gentooLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_unitedLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_unitedLinuxVolume_label();
                    } else if (progNameAttr.getDisplayString().contains(Bundle.ExtractOs_ubuntuLinuxOs_label())) {
                        dataSourceUsageDescription = Bundle.DataSourceUsageAnalyzer_ubuntuLinuxVolume_label();
                    }
                    createDataSourceUsageArtifact(dataSourceUsageDescription);
                }
            }
        }
        if (!windowsOsDetected) {
            if (osSpecificVolumeFilesExist(Arrays.asList(WINDOWS_VOLUME_PATH))) {
                createDataSourceUsageArtifact(Bundle.DataSourceUsageAnalyzer_windowsVolume_label());
            }
        }
    }

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
