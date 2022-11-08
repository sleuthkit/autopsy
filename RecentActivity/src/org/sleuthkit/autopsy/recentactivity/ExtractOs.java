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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Create OS INFO artifacts for the Operating Systems believed to be present on
 * the data source.
 */
@Messages({"ExtractOs.displayName=OS Info Analyzer",
    "ExtractOS_progressMessage=Checking for OS"})
class ExtractOs extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractOs.class.getName());

    private static final String WINDOWS_VOLUME_PATH = "/windows/system32";
    private static final String OSX_VOLUME_PATH = "/System/Library/CoreServices/SystemVersion.plist";
    private static final String ANDROID_VOLUME_PATH = "/data/com.android.providers.settings/databases/settings.db";
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
    private final IngestJobContext context;

    ExtractOs(IngestJobContext context) {
        super(Bundle.ExtractOs_displayName(), context);
        this.context = context;
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        this.dataSource = dataSource;
        try {
            progressBar.progress(Bundle.ExtractOS_progressMessage());
            for (OS_TYPE value : OS_TYPE.values()) {
                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                checkForOSFiles(value);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained a volume with operating system specific files", ex);
        }
    }

    /**
     * Check if any of the specified file paths exist if they do create an OS
     * Info artifact if a program name was specified.
     *
     * @param osType - the enumeration of OS_TYPE which represents the operating
     *               system being checked for
     */
    private void checkForOSFiles(OS_TYPE osType) throws TskCoreException {
        if (osType.getOsInfoLabel().isEmpty()) {
            //shortcut out if it was called with out a specified program name so no OS INFO artifacts are created
            return;
        }
        AbstractFile file = getFirstFileFound(osType.getFilePaths());

        if (file != null && tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO, file.getId()).isEmpty()) {
            //if the os info program name is not empty create an os info artifact on the first of the files found
            Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                    getRAModuleName(),
                    osType.getOsInfoLabel())); //NON-NLS
            postArtifact(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_OS_INFO, file, bbattributes));
        }
    }

    /**
     * Get the first file found which matches one of the specified paths. Return
     * null if no file is found.
     *
     * @param pathsToSearchFor the list of strings which represent the paths to
     *                         search
     *
     * @return the first AbstractFile found which matched a specified path to
     *         search for
     */
    private AbstractFile getFirstFileFound(List<String> pathsToSearchFor) throws TskCoreException {
        for (String filePath : pathsToSearchFor) {
            List<AbstractFile> files = currentCase.getSleuthkitCase().getFileManager().findFilesExactNameExactPath(dataSource, FilenameUtils.getName(filePath), FilenameUtils.getPath(filePath));
            if (!files.isEmpty()) {
                return files.get(0);
            }
        }
        return null;
    }

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
        "ExtractOs.ubuntuLinuxOs.label=Linux (Ubuntu)",
        "ExtractOs.windowsVolume.label=OS Drive (Windows)",
        "ExtractOs.osxVolume.label=OS Drive (OS X)",
        "ExtractOs.androidVolume.label=OS Drive (Android)",
        "ExtractOs.redhatLinuxVolume.label=OS Drive (Linux Redhat)",
        "ExtractOs.novellSUSEVolume.label=OS Drive (Linux Novell SUSE)",
        "ExtractOs.fedoraLinuxVolume.label=OS Drive (Linux Fedora)",
        "ExtractOs.slackwareLinuxVolume.label=OS Drive (Linux Slackware)",
        "ExtractOs.debianLinuxVolume.label=OS Drive (Linux Debian)",
        "ExtractOs.mandrakeLinuxVolume.label=OS Drive (Linux Mandrake)",
        "ExtractOs.yellowDogLinuxVolume.label=OS Drive (Linux Yellow Dog)",
        "ExtractOs.sunJDSLinuxVolume.label=OS Drive (Linux Sun JDS)",
        "ExtractOs.solarisSparcVolume.label=OS Drive (Linux Solaris/Sparc)",
        "ExtractOs.gentooLinuxVolume.label=OS Drive (Linux Gentoo)",
        "ExtractOs.unitedLinuxVolume.label=OS Drive (Linux United Linux)",
        "ExtractOs.ubuntuLinuxVolume.label=OS Drive (Linux Ubuntu)"})
    /**
     * Enum used for coupling the TSK_OS_INFO artifacts created in ExtractOs and
     * the TSK_DATA_SOURCE_USAGE artifacts created in DataSourceUsageAnalyzer
     */
    enum OS_TYPE {
        WINDOWS("", Bundle.ExtractOs_windowsVolume_label(), Arrays.asList(WINDOWS_VOLUME_PATH)), //windows doesn't get OS_INFO artifacts created for it here  
        MAC_OS_X(Bundle.ExtractOs_osx_label(), Bundle.ExtractOs_osxVolume_label(), Arrays.asList(OSX_VOLUME_PATH)),
        ANDROID(Bundle.ExtractOs_androidOs_label(), Bundle.ExtractOs_androidVolume_label(), Arrays.asList(ANDROID_VOLUME_PATH)),
        LINUX_REDHAT(Bundle.ExtractOs_redhatLinuxOs_label(), Bundle.ExtractOs_redhatLinuxVolume_label(), Arrays.asList(LINUX_RED_HAT_PATHS)),
        LINUX_NOVELL_SUSE(Bundle.ExtractOs_novellSUSEOs_label(), Bundle.ExtractOs_novellSUSEVolume_label(), Arrays.asList(LINUX_NOVELL_SUSE_PATH)),
        LINUX_FEDORA(Bundle.ExtractOs_fedoraLinuxOs_label(), Bundle.ExtractOs_fedoraLinuxVolume_label(), Arrays.asList(LINUX_FEDORA_PATH)),
        LINUX_SLACKWARE(Bundle.ExtractOs_slackwareLinuxOs_label(), Bundle.ExtractOs_slackwareLinuxVolume_label(), Arrays.asList(LINUX_SLACKWARE_PATHS)),
        LINUX_DEBIAN(Bundle.ExtractOs_debianLinuxOs_label(), Bundle.ExtractOs_debianLinuxVolume_label(), Arrays.asList(LINUX_DEBIAN_PATHS)),
        LINUX_MANDRAKE(Bundle.ExtractOs_mandrakeLinuxOs_label(), Bundle.ExtractOs_mandrakeLinuxVolume_label(), Arrays.asList(LINUX_MANDRAKE_PATH)),
        LINUX_YELLOW_DOG(Bundle.ExtractOs_yellowDogLinuxOs_label(), Bundle.ExtractOs_yellowDogLinuxVolume_label(), Arrays.asList(LINUX_YELLOW_DOG_PATH)),
        LINUX_SUN_JDS(Bundle.ExtractOs_sunJDSLinuxOs_label(), Bundle.ExtractOs_sunJDSLinuxVolume_label(), Arrays.asList(LINUX_SUN_JDS_PATH)),
        LINUX_SOLARIS_SPARC(Bundle.ExtractOs_solarisSparcOs_label(), Bundle.ExtractOs_solarisSparcVolume_label(), Arrays.asList(LINUX_SOLARIS_SPARC_PATH)),
        LINUX_GENTOO(Bundle.ExtractOs_gentooLinuxOs_label(), Bundle.ExtractOs_gentooLinuxVolume_label(), Arrays.asList(LINUX_GENTOO_PATH)),
        LINUX_UNITED_LINUX(Bundle.ExtractOs_unitedLinuxOs_label(), Bundle.ExtractOs_unitedLinuxVolume_label(), Arrays.asList(LINUX_UNITED_LINUX_PATH)),
        LINUX_UBUNTU(Bundle.ExtractOs_ubuntuLinuxOs_label(), Bundle.ExtractOs_ubuntuLinuxVolume_label(), Arrays.asList(LINUX_UBUNTU_PATH));

        private final String osInfoLabel;
        private final String dsUsageLabel;
        private final List<String> filePaths;

        /**
         * Constructs a value for an OS_TYPE enum
         *
         * @param osInfoText   - the program name to use for TSK_OS_INFO
         *                     artifacts
         * @param dsUsageText  - the description to use for
         *                     TSK_DATA_SOURCE_USAGE artifacts
         * @param filePathList - the list of file paths to create these
         *                     artifacts for
         */
        private OS_TYPE(String osInfoText, String dsUsageText, List<String> filePathList) {
            this.osInfoLabel = osInfoText;
            this.dsUsageLabel = dsUsageText;
            this.filePaths = filePathList;
        }

        /**
         * Get the string to use for the PROG_NAME attribute of TSK_OS_INFO
         * artifacts.
         *
         * @return osInfoLabel
         */
        String getOsInfoLabel() {
            return osInfoLabel;
        }

        /**
         * Get the string to use for the DESCRIPTION attribute of
         * TSK_DATA_SOURCE_USAGE artifacts.
         *
         * @return dsUsageLabel
         */
        String getDsUsageLabel() {
            return dsUsageLabel;
        }

        /**
         * Get the list of string representations of file paths which should
         * identify that this OS_TYPE is present in the data source.
         *
         * @return filePaths
         */
        List<String> getFilePaths() {
            return Collections.unmodifiableList(filePaths);
        }

        /**
         * Given the Description text of a TSK_DATA_SOURCE_USAGE artifact
         * determine what type OS_TYPE this is
         *
         * @param dsUsageLabel description text of the TSK_DATA_SOURCE_USAGE
         *                     artifact
         *
         * @return the OS_TYPE which matches the specified dsUsageLabel, null if
         *         no types match
         */
        static public OS_TYPE fromDsUsageLabel(String dsUsageLabel) {
            for (OS_TYPE value : OS_TYPE.values()) {
                if (value.getDsUsageLabel().equals(dsUsageLabel)) {
                    return value;
                }
            }
            return null;
        }

        /**
         * Given the Program Name text of a TSK_OS_INFO artifact determine what
         * type OS_TYPE this is
         *
         * @param osInfoLabel program name text of the TSK_OS_INFO artifact
         *
         * @return the OS_TYPE which matches the specified osInfoLabel, null if
         *         no types match
         */
        static public OS_TYPE fromOsInfoLabel(String osInfoLabel) {
            for (OS_TYPE value : OS_TYPE.values()) {
                if (value.getOsInfoLabel().equals(osInfoLabel)) {
                    return value;
                }
            }
            return null;
        }
    }
}
