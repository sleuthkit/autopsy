/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.annotations.Beta;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * An Autopsy Data Model item with an underlying DataArtifact Sleuth Kit Data
 * Model object.
 */
public class DataArtifactItem extends BlackboardArtifactItem<DataArtifact> {

    /**
     * Constructs an Autopsy Data Model item with an underlying DataArtifact
     * Sleuth Kit Data Model object.
     *
     * @param dataArtifact The DataArtifact object.
     * @param sourceContent The source content of the DataArtifact.
     */
    @Beta
    DataArtifactItem(DataArtifact dataArtifact, Content sourceContent) {
        super(dataArtifact, sourceContent);
    }

}
