/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.util.List;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback.DataSourceProcessorResult;
import org.sleuthkit.datamodel.Content;

/**
 * Called on completion of the add image task.
 */
interface AddImageTaskCallback {

    /**
     * Called when the add image task is completed.
     * 
     * @param result   The result from the data source processor.
     * @param errList  The list of errors.
     * @param newDataSources  The list of new data sources.
     */
    void onCompleted(DataSourceProcessorResult result, List<String> errList, List<Content> newDataSources);
}
