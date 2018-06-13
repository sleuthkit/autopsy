/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.io.File;
import java.util.Map;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepositoryFile;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.SleuthkitCaseFileInstanceNode;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Encapsulates data required to instantiate a <code>FileInstanceNode</code>.
 */
final public class SleuthkitCaseFileInstanceMetadata extends FileInstanceNodeGenerator {
    
    /**
     * Create meta data required to find an abstract file and build a FileInstanceNode.
     * @param objectId id of abstract file to find
     * @param dataSourceName name of datasource where the object is found
     */
    SleuthkitCaseFileInstanceMetadata (Long abstractFileReference, Map<Long, AbstractFile> cachedFiles, String dataSource, String caseName) {
        super(abstractFileReference, cachedFiles, dataSource, caseName);
    }

    @Override
    public DisplayableItemNode generateNode() {
        return new SleuthkitCaseFileInstanceNode(this.lookupOrCreateAbstractFile(), this.getCaseName(), this.getDataSource());
    }
}
