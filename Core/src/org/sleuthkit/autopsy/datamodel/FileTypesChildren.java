/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensionFilters.RootFilter;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 */
class FileTypesChildren extends ChildFactory<FileTypeExtensionFilters.SearchFilterInterface> {
    
    private SleuthkitCase skCase;
    private FileTypeExtensionFilters.RootFilter filter;

    /**
     * 
     * @param skCase
     * @param filter Is null for root node 
     */
    public FileTypesChildren(SleuthkitCase skCase, FileTypeExtensionFilters.RootFilter filter) {
        this.skCase = skCase;
        this.filter = filter;
    }

    @Override
    protected boolean createKeys(List<FileTypeExtensionFilters.SearchFilterInterface> list) {
        // root node
        if (filter == null) {
            list.addAll(Arrays.asList(RootFilter.values()));
        }
        // document and executable has another level of nodes
        else if (filter.equals(RootFilter.TSK_DOCUMENT_FILTER) ){
            list.addAll(Arrays.asList(FileTypeExtensionFilters.DocumentFilter.values()));
        }
        else if (filter.equals(RootFilter.TSK_EXECUTABLE_FILTER) ){
            list.addAll(Arrays.asList(FileTypeExtensionFilters.ExecutableFilter.values()));
        }
        return true;
    }
    
    @Override
    protected Node createNodeForKey(FileTypeExtensionFilters.SearchFilterInterface key){
        // make new nodes for the sub-nodes
        if(key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER.getName())){
            return new FileTypesNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_DOCUMENT_FILTER);
        }
        else if(key.getName().equals(FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER.getName())){
            return new FileTypesNode(skCase, FileTypeExtensionFilters.RootFilter.TSK_EXECUTABLE_FILTER);
        }
        else {
            return new FileTypeNode(key, skCase);
        }
    }
}
