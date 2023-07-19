/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.concurrent.ExecutorService;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * A node whose children will be displayed in the results view and determines
 * children based on a SearchResultDTO.
 */
public class SearchResultRootNode extends AbstractNode {

    private final SearchResultChildFactory factory;
    
    // This param is can change, is not used as part of the search query and
    // therefore is not included in the equals and hashcode methods.
    private ChildNodeSelectionInfo childNodeSelectionInfo;

    public SearchResultRootNode(SearchResultsDTO initialResults, ExecutorService nodeThreadPool) {
        this(initialResults, new SearchResultChildFactory(initialResults, nodeThreadPool));
    }

    private SearchResultRootNode(SearchResultsDTO initialResults, SearchResultChildFactory factory) {
        super(Children.create(factory, true), initialResults.getDataSourceParent() != null ? Lookups.singleton(initialResults.getDataSourceParent()) : null);
        this.factory = factory;

        setName(initialResults.getTypeId());
        setDisplayName(initialResults.getDisplayName());
    }
    
    public ChildNodeSelectionInfo getNodeSelectionInfo() {
        return childNodeSelectionInfo;
    }
    
    public void setNodeSelectionInfo(ChildNodeSelectionInfo info) {
        childNodeSelectionInfo = info;
    }
    
    @Messages({
        "SearchResultRootNode_noDesc=No Description",
        "SearchResultRootNode_createSheet_type_name=Name",
        "SearchResultRootNode_createSheet_type_displayName=Name",
        "SearchResultRootNode_createSheet_childCount_name=Child Count",
        "SearchResultRootNode_createSheet_childCount_displayName=Child Count"
    })
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(
                Bundle.SearchResultRootNode_createSheet_type_name(),
                Bundle.SearchResultRootNode_createSheet_type_displayName(),
                Bundle.SearchResultRootNode_noDesc(),
                getDisplayName()));

        sheetSet.put(new NodeProperty<>(
                Bundle.SearchResultRootNode_createSheet_childCount_name(),
                Bundle.SearchResultRootNode_createSheet_childCount_displayName(),
                Bundle.SearchResultRootNode_noDesc(),
                this.factory.getResultCount()));

        return sheet;
    }

    /**
     * Updates the child factory with the backing search results data performing
     * a refresh of data without entirely resetting the node.
     *
     * @param updatedResults The search results.
     */
    public void updateChildren(SearchResultsDTO updatedResults) {
        this.factory.update(updatedResults);
    }
}
