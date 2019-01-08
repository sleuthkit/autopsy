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

@Messages({"DataSourceUsageAnalyzer.parentModuleName=Recent Activity"})
public class DataSourceUsageAnalyzer extends Extract {

    private static final Logger logger = Logger.getLogger(Firefox.class.getName());
    private Content dataSource;

    @Override
    void process(Content dataSource, IngestJobContext context) {
        
        this.dataSource = dataSource;
        try {
            checkForWindowsVolume();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to check if datasource contained Windows volume.", ex);
        }

    }

    /**
     * Check if the data source contains files which would indicate a windows
     * volume is present in it, and create an artifact for that volume if detected.
     *
     * @throws TskCoreException
     */
    private void checkForWindowsVolume() throws TskCoreException {
        Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
        FileManager fileManager = currentCase.getServices().getFileManager();
        List<AbstractFile> files = fileManager.findFilesByParentPath(dataSource.getId(), "/windows/system32");
        //create an artifact if any files with the windows/system32 path were found
        if (!files.isEmpty()) {
            bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATA_SOURCE_USE_DETAILS,
                    Bundle.DataSourceUsageAnalyzer_parentModuleName(),
                    "Windows volume")); //NON-NLS
            addArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_DATA_SOURCE_USAGE, dataSource, bbattributes);
        }
    }

}
