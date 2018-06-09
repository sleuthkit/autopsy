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

import java.util.Map;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepositoryFile;
import org.sleuthkit.autopsy.datamodel.CentralRepositoryFileInstanceNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Generates a DisplayableItmeNode using a CentralRepositoryFile.
 */
final public class CentralRepositoryCaseFileInstanceMetadata extends FileInstanceNodeGenerator {

    private CentralRepositoryFile crFile;
    
    CentralRepositoryCaseFileInstanceMetadata(CentralRepositoryFile crFile, Long abstractFileReference, Map<Long, AbstractFile> cachedFiles, String dataSource){
        super(abstractFileReference, cachedFiles, dataSource);
        //TODO should we actually just take an ID instead of the whole object
        //  like we've done previously, or is this ok?
        this.crFile = crFile;
        //this.setDataSource(crFile.getCorrelationDataSource().getName());
    }
    
    @Override
    public DisplayableItemNode generateNode() {
        return new CentralRepositoryFileInstanceNode(this.crFile, this.lookupOrCreateAbstractFile());
    }
}
