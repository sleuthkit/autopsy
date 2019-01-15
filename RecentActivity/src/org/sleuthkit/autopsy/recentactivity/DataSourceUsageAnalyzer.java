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

    private Content dataSource;

    @Messages({
        "DataSourceAnalyzer.windowsVolume.label=Windows volume",
        "DataSourceUsageAnalyzer.osxVolume.label=OS Drive (OS X)",
        "DataSourceUsageAnalyzer.osx.label=Mac OS X"})
    @Override
    void process(Content dataSource, IngestJobContext context) {

        this.dataSource = dataSource;
        try {
            checkForOSFiles(Arrays.asList(WINDOWS_VOLUME_PATH), Bundle.DataSourceAnalyzer_windowsVolume_label(), "");
            checkForOSFiles(Arrays.asList(OSX_VOLUME_PATH), Bundle.DataSourceUsageAnalyzer_osxVolume_label(), Bundle.DataSourceUsageAnalyzer_osx_label());
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
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> files = new ArrayList<>();
        for (String filePath : filesToCheckFor) {
            files.addAll(fileManager.findFilesByParentPath(dataSource.getId(), filePath));
        }
        //create an artifact if any files with the windows/system32 specific path were found
        if (!files.isEmpty()) {
            if (!dataSourceUsageDescription.isEmpty()) {
                bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                        Bundle.DataSourceUsageAnalyzer_parentModuleName(),
                        dataSourceUsageDescription)); //NON-NLS
                addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource, bbattributes);
            }
            if (!osInfoProgramName.isEmpty()) {
                bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME,
                        Bundle.DataSourceUsageAnalyzer_parentModuleName(),
                        osInfoProgramName)); //NON-NLS
                addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_INFO, dataSource, bbattributes);
            }
        }
    }
}
