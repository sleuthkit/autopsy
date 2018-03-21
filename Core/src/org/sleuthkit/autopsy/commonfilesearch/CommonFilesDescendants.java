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

import java.util.List;
import java.util.Map;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.CommonFileChildNode;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author bsweeney
 */
public class CommonFilesDescendants extends ChildFactory<AbstractFile> {

    private List<AbstractFile> descendants;
    private Map<Long, String> dataSourceMap;
    
    
    CommonFilesDescendants(List<AbstractFile> descendants, Map<Long, String> dataSourceMap){
        super();
        this.descendants = descendants;
    }
    
    @Override
    protected Node createNodeForKey(AbstractFile file){
        
        final String dataSource = this.dataSourceMap.get(file.getDataSourceObjectId());
        
        return new CommonFileChildNode(file, dataSource);
    }
    
    @Override
    protected boolean createKeys(List<AbstractFile> list) {
        list.addAll(this.descendants);
        return true;
    }
    
}
