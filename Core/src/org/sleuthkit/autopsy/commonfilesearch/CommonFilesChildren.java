/*
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

import java.util.List;
import org.openide.nodes.ChildFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.CommonFileNode;

/**
 * Makes nodes for common files search results.
 */
final class CommonFilesChildren extends ChildFactory<AbstractFile>  {

    private CommonFilesMetaData metaData;

    CommonFilesChildren(CommonFilesMetaData theMetaData) {
        super();  
        this.metaData = theMetaData;
    }

    protected void removeNotify() {
        metaData = null;
    }
    
    @Override
    protected Node createNodeForKey(AbstractFile t) {
        
        final String md5Hash = t.getMd5Hash();
        
        int instanceCount = metaData.getInstanceMap().get(md5Hash);
        String dataSources = metaData.getDataSourceMap().get(md5Hash);     

        return new CommonFileNode(t, instanceCount, dataSources);

    }

    @Override
    protected boolean createKeys(List<AbstractFile> toPopulate) {
       toPopulate.addAll(metaData.getFilesList());
       return true;  
    }

}
