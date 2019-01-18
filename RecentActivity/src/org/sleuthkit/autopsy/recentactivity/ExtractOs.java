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

class ExtractOs extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractOs.class.getName());

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
        "ExtractOs.osx.label=Mac OS X",
        "ExtractOs.androidOs.label=Android",
        "ExtractOs.redhatLinuxOs.label=Linux (Redhat)",
        "ExtractOs.novellSUSEOs.label=Linux (Novell SUSE)",
        "ExtractOs.fedoraLinuxOs.label=Linux (Fedora)",
        "ExtractOs.slackwareLinuxOs.label=Linux (Slackware)",
        "ExtractOs.debianLinuxOs.label=Linux (Debian)",
        "ExtractOs.mandrakeLinuxOs.label=Linux (Mandrake)",
        "ExtractOs.yellowDogLinuxOs.label=Linux (Yellow Dog)",
        "ExtractOs.sunJDSLinuxOs.label=Linux (Sun JDS)",
        "ExtractOs.solarisSparcOs.label=Linux (Solaris/Sparc)",
        "ExtractOs.gentooLinuxOs.label=Linux (Gentoo)",
        "ExtractOs.unitedLinuxOs.label=Linux (United Linux)",
        "ExtractOs.ubuntuLinuxOs.label=Linux (Ubuntu)"})
    @Override
    void process(Content dataSource, IngestJobContext context) {
        this.dataSource = dataSource;
        try {
            checkForOSFiles(Arrays.asList(OSX_VOLUME_PATH), Bundle.ExtractOs_osx_label());
            checkForOSFiles(Arrays.asList(ANDROID_VOLUME_PATH), Bundle.ExtractOs_androidOs_label());
            checkForOSFiles(Arrays.asList(LINUX_RED_HAT_PATHS), Bundle.ExtractOs_redhatLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_NOVELL_SUSE_PATH), Bundle.ExtractOs_novellSUSEOs_label());
            checkForOSFiles(Arrays.asList(LINUX_FEDORA_PATH), Bundle.ExtractOs_fedoraLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_SLACKWARE_PATHS), Bundle.ExtractOs_slackwareLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_DEBIAN_PATHS), Bundle.ExtractOs_debianLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_MANDRAKE_PATH), Bundle.ExtractOs_mandrakeLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_YELLOW_DOG_PATH), Bundle.ExtractOs_yellowDogLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_SUN_JDS_PATH), Bundle.ExtractOs_sunJDSLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_SOLARIS_SPARC_PATH), Bundle.ExtractOs_solarisSparcOs_label());
            checkForOSFiles(Arrays.asList(LINUX_GENTOO_PATH), Bundle.ExtractOs_gentooLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_UNITED_LINUX_PATH), Bundle.ExtractOs_unitedLinuxOs_label());
            checkForOSFiles(Arrays.asList(LINUX_UBUNTU_PATH), Bundle.ExtractOs_ubuntuLinuxOs_label());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained a volume with operating system specific files", ex);
        }
    }

    /**
     * Check if any of the specified file paths exist if they do create an OS
     * Info artifact if a program name was specified.
     *
     * @param filesToCheckFor   - List of file paths to check for
     * @param osInfoProgramName - empty if no OS Info Artifact should be created
     */
    private void checkForOSFiles(List<String> filesToCheckFor, String osInfoProgramName) throws TskCoreException {
        if (osInfoProgramName.isEmpty()) {
            //shortcut out if it was called with no OS Program nameartifacts to create
            return;
        }
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> files = new ArrayList<>();
        for (String filePath : filesToCheckFor) {
            files.addAll(fileManager.findFiles(dataSource, FilenameUtils.getName(filePath), FilenameUtils.getPath(filePath)));
        }
        if (!files.isEmpty()) {
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
