/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.ui;

import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeIdModuleFactory;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.modules.pictureanalyzer.PictureAnalyzerIngestModuleFactory;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;

/**
 * Wrapper class to keep track of which modules were run on each data source.
 */
class DataSourceModulesWrapper {

    private boolean hashModuleRun = false;
    private boolean fileTypeModuleRun = false;
    private boolean exifModuleRun = false;
    private final String dataSourceName;

    /**
     * Create a new DataSourceModulesWrapper object
     *
     * @param dsName The name of the data source being kept track of.
     */
    DataSourceModulesWrapper(String dsName) {
        dataSourceName = dsName;
    }

    @Messages({"# {0} - dataSourceName",
        "DataSourceModuleWrapper.hashModule.text=Hash Lookup module was not run on data source: {0}\n",
        "# {0} - dataSourceName",
        "DataSourceModuleWrapper.fileTypeModule.text=File Type Identification module was not run on data source: {0}\n",
        "# {0} - dataSourceName",
        "DataSourceModuleWrapper.exifModule.text=Picture Analyzer module was not run on data source: {0}\n"
    })
    /**
     * Get the message which indicates which modules were not run on this data
     * source.
     */
    String getMessage() {
        String message = "";
        if (!hashModuleRun) {
            message += Bundle.DataSourceModuleWrapper_hashModule_text(dataSourceName);
        }
        if (!fileTypeModuleRun) {
            message += Bundle.DataSourceModuleWrapper_fileTypeModule_text(dataSourceName);
        }
        if (!exifModuleRun) {
            message += Bundle.DataSourceModuleWrapper_exifModule_text(dataSourceName);
        }
        return message;
    }

    /**
     * Update which modules were run for this data source based on the specified
     * ingest job.
     *
     * @param jobInfo The IngestJobInfo for the job which was run on this data
     *                source.
     */
    void updateModulesRun(IngestJobInfo jobInfo) {
        for (IngestModuleInfo moduleInfo : jobInfo.getIngestModuleInfo()) {
            if (hashModuleRun && fileTypeModuleRun && exifModuleRun) {
                return;
            }
            updateHashModuleStatus(moduleInfo);
            updateFileTypeStatus(moduleInfo);
            updateExifStatus(moduleInfo);
        }
    }

    /**
     * Update whether the Hash Lookup module was run for this data source.
     *
     * @param moduleInfo Information regarding a module which was run on this
     *                   data source.
     */
    private void updateHashModuleStatus(IngestModuleInfo moduleInfo) {
        if (!hashModuleRun && moduleInfo.getDisplayName().equals(HashLookupModuleFactory.getModuleName())) {
            hashModuleRun = true;
        }
    }

    /**
     * Update whether the File Type ID module was run for this data source.
     *
     * @param moduleInfo Information regarding a module which was run on this
     *                   data source.
     */
    private void updateFileTypeStatus(IngestModuleInfo moduleInfo) {
        if (!fileTypeModuleRun && moduleInfo.getDisplayName().equals(FileTypeIdModuleFactory.getModuleName())) {
            fileTypeModuleRun = true;
        }
    }

    /**
     * Update whether the Exif module was run for this data source.
     *
     * @param moduleInfo Information regarding a module which was run on this
     *                   data source.
     */
    private void updateExifStatus(IngestModuleInfo moduleInfo) {
        if (!exifModuleRun && moduleInfo.getDisplayName().equals(PictureAnalyzerIngestModuleFactory.getModuleName())) {
            exifModuleRun = true;
        }
    }
}
