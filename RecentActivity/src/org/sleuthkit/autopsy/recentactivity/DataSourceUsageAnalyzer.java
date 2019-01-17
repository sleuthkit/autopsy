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
public class DataSourceUsageAnalyzer extends Extract {

    private static final Logger logger = Logger.getLogger(DataSourceUsageAnalyzer.class.getName());

    private static final String WINDOWS_VOLUME_PATH = "/windows/system32";
    private static final String OSX_VOLUME_PATH = "/System/Library/CoreServices/SystemVersion.plist";
    private static final String ANDROID_VOLUME_PATH = "data/com.android.providers.settings/databases/settings.db";
    //linux specific files reference https://www.novell.com/coolsolutions/feature/11251.html
    private static final String LINUX_RED_HAT_PATHS[] = {"/etc/redhat-release", "/etc/redhat_version"};
    private static final String LINUX_NOVELL_SUSE_PATH = "/etc/SUSE-release";
    private static final String LINUX_FEDORA_PATH = "/etc/fedora-release";
    private static final String LINUX_SLACKWARE_PATHS[] = {"/etc/slackware-release", "/etc/slackware-version"};
    private static final String LINUX_DEBIAN_PATHS[] = {"/etc/debian_release", "/etc/debian_version"};
    private static final String LINUX_MANDRAKE_PATH = "/etc/mandrake-release";
    private static final String LINUX_YELLOW_DOG_PATH = "/etc/yellowdog-release";
    private static final String LINUX_SUN_JDS_PATH = "/etc/sun-release";
    private static final String LINUX_SOLARIS_SPARC_PATH = "/etc/release";
    private static final String LINUX_GENTOO_PATH = "/etc/gentoo-release";
    private static final String LINUX_UNITED_LINUX_PATH = "/etc/UnitedLinux-release";
    private static final String LINUX_UBUNTU_PATH = "/etc/lsb-release";

    private Content dataSource;

    @Messages({
        "DataSourceAnalyzer.windowsVolume.label=OS Drive (Windows)",
        "DataSourceUsageAnalyzer.osxVolume.label=OS Drive (OS X)",
        "DataSourceUsageAnalyzer.osx.label=Mac OS X",
        "DataSourceUsageAnalyzer.androidVolume.label=OS Drive (Android)",
        "DataSourceUsageAnalyzer.androidOs.label=Android",
        "DataSourceUsageAnalyzer.redhatLinuxVolume.label=OS Drive (Linux Redhat)",
        "DataSourceUsageAnalyzer.redhatLinuxOs.label=Linux (Redhat)",
        "DataSourceUsageAnalyzer.novellSUSEVolume.label=OS Drive (Linux Novell SUSE)",
        "DataSourceUsageAnalyzer.novellSUSEOs.label=Linux (Novell SUSE)",
        "DataSourceUsageAnalyzer.fedoraLinuxVolume.label=OS Drive (Linux Fedora)",
        "DataSourceUsageAnalyzer.fedoraLinuxOs.lable=Linux (Fedora)",
        "DataSourceUsageAnalyzer.slackwareLinuxVolume.label=OS Drive (Linux Slackware)",
        "DataSourceUsageAnalyzer.slackwareLinuxOs.label=Linux (Slackware)",
        "DataSourceUsageAnalyzer.debianLinuxVolume.label=OS Drive (Linux Debian)",
        "DataSourceUsageAnalyzer.debianLinuxOs.label=Linux (Debian)",
        "DataSourceUsageAnalyzer.mandrakeLinuxVolume.label=OS Drive (Linux Mandrake)",
        "DataSourceUsageAnalyzer.mandrakeLinuxOs.label=Linux (Mandrake)",
        "DataSourceUsageAnalyzer.yellowDogLinuxVolume.label=OS Drive (Linux Yellow Dog)",
        "DataSourceUsageAnalyzer.yellowDogLinuxOs.label=Linux (Yellow Dog)",
        "DataSourceUsageAnalyzer.sunJDSLinuxVolume.label=OS Drive (Linux Sun JDS)",
        "DataSourceUsageAnalyzer.sunJDSLinuxOs.label=Linux (Sun JDS)",
        "DataSourceUsageAnalyzer.solarisSparcVolume.label=OS Drive (Linux Solaris/Sparc)",
        "DataSourceUsageAnalyzer.solarisSparcOs.label=Linux (Solaris/Sparc)",
        "DataSourceUsageAnalyzer.gentooLinuxVolume.label=OS Drive (Linux Gentoo)",
        "DataSourceUsageAnalyzer.gentooLinuxOs.label=Linux (Gentoo)",
        "DataSourceUsageAnalyzer.unitedLinuxVolume.label=OS Drive (Linux United Linux)",
        "DataSourceUsageAnalyzer.unitedLinuxOs.label=Linux (United Linux)",
        "DataSourceUsageAnalyzer.ubuntuLinuxVolume.label=OS Drive (Linux Ubuntu)",
        "DataSourceUsageAnalyzer.ubuntuLinuxOs.label=Linux (Ubuntu)"})
    @Override
    void process(Content dataSource, IngestJobContext context) {

        this.dataSource = dataSource;
        try {
            checkForOSFiles(Arrays.asList(WINDOWS_VOLUME_PATH), Bundle.DataSourceAnalyzer_windowsVolume_label(), "");
            checkForOSFiles(Arrays.asList(OSX_VOLUME_PATH), Bundle.DataSourceUsageAnalyzer_osxVolume_label(), Bundle.DataSourceUsageAnalyzer_osx_label());
            checkForOSFiles(Arrays.asList(ANDROID_VOLUME_PATH), Bundle.DataSourceUsageAnalyzer_androidVolume_label(), Bundle.DataSourceUsageAnalyzer_androidOs_label());
            checkForOSFiles(Arrays.asList(LINUX_RED_HAT_PATHS), Bundle.DataSourceUsageAnalyzer_redhatLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_redhatLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_NOVELL_SUSE_PATH), Bundle.DataSourceUsageAnalyzer_novellSUSEVolume_label(), Bundle.DataSourceUsageAnalyzer_novellSUSEOs_label());
            checkForOSFiles(Arrays.asList(LINUX_FEDORA_PATH), Bundle.DataSourceUsageAnalyzer_fedoraLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_fedoraLinuxOs_lable());
            checkForOSFiles(Arrays.asList(LINUX_SLACKWARE_PATHS), Bundle.DataSourceUsageAnalyzer_slackwareLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_slackwareLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_DEBIAN_PATHS), Bundle.DataSourceUsageAnalyzer_debianLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_debianLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_MANDRAKE_PATH), Bundle.DataSourceUsageAnalyzer_mandrakeLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_mandrakeLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_YELLOW_DOG_PATH), Bundle.DataSourceUsageAnalyzer_yellowDogLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_yellowDogLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_SUN_JDS_PATH), Bundle.DataSourceUsageAnalyzer_sunJDSLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_sunJDSLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_SOLARIS_SPARC_PATH), Bundle.DataSourceUsageAnalyzer_solarisSparcVolume_label(), Bundle.DataSourceUsageAnalyzer_solarisSparcOs_label());
            checkForOSFiles(Arrays.asList(LINUX_GENTOO_PATH), Bundle.DataSourceUsageAnalyzer_gentooLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_gentooLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_UNITED_LINUX_PATH), Bundle.DataSourceUsageAnalyzer_unitedLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_unitedLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_UBUNTU_PATH), Bundle.DataSourceUsageAnalyzer_ubuntuLinuxVolume_label(), Bundle.DataSourceUsageAnalyzer_ubuntuLinuxOs_label());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained a volume with operating system specific files", ex);
        }

    }

    /**
     * Check if any of the specified file paths exist, if they do create a Data
     * Source Usage if a description was specified and create and OS Info
     * artifact if a program name was specified.
     *
     * @param filesToCheckFor             - List of file paths to check for
     * @param dataSourceUsageDescription- empty if no Data Source Usage Artifact
     *                                    should be created
     * @param osInfoProgramName           - empty if no OS Info Artifact should
     *                                    be created
     */
    private void checkForOSFiles(List<String> filesToCheckFor, String dataSourceUsageDescription, String osInfoProgramName) throws TskCoreException {
        if (dataSourceUsageDescription.isEmpty() && osInfoProgramName.isEmpty()) {
            //shortcut out if it was called with no artifacts to create
            return;
        }
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> files = new ArrayList<>();
        for (String filePath : filesToCheckFor) {
            files.addAll(fileManager.findFiles(dataSource, FilenameUtils.getName(filePath), FilenameUtils.getPath(filePath)));
        }
        //if any files existed matching the specified file
        if (!files.isEmpty()) {
            if (!dataSourceUsageDescription.isEmpty()) {
                //if the data source usage description is not empty create a data source usage artifact if an Usage artifact does not already exist with the same description
                List<BlackboardArtifact> artifacts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource.getId());
                boolean createNewUsageArtifact = true;
                for (BlackboardArtifact artifact : artifacts) {
                    if (artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION)).getValueString().equals(dataSourceUsageDescription)) {
                        createNewUsageArtifact = false;
                        break;
                    }
                }
                if (createNewUsageArtifact) {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                            Bundle.DataSourceUsageAnalyzer_parentModuleName(),
                            dataSourceUsageDescription)); //NON-NLS
                    addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource, bbattributes);
                }
            }
            if (!osInfoProgramName.isEmpty()) {
                //check if OS INFO artifact already created on this file
                if (tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO, files.get(0).getId()).isEmpty()) {
                    //if the os info program name is not empty create an os info artifact on the first of the files found
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                            Bundle.DataSourceUsageAnalyzer_parentModuleName(),
                            osInfoProgramName)); //NON-NLS
                    addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO, files.get(0), bbattributes);
                }
            }
        }
    }
}
