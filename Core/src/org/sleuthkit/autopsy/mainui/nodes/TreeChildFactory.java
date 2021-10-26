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


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.mainui.datamodel.RowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Factory for populating tree with results.
 */
public class TreeChildFactory extends ChildFactory<RowDTO> {
    private final Map<RowDTO, UpdatableNode> typeNodeMap = new HashMap<>();
    private SearchResultsDTO results;
    
    public TreeChildFactory(SearchResultsDTO initialResults) {
        this.results = initialResults;
    }

    @Override
    protected boolean createKeys(List<RowDTO> toPopulate) {
        SearchResultsDTO curResults = this.results;
        Set<RowDTO> resultRows = new HashSet<>(curResults.getItems());
        
        // remove no longer present
        Set<RowDTO> toBeRemoved = new HashSet<>(typeNodeMap.keySet());
        toBeRemoved.removeAll(resultRows);
        for (RowDTO presentId : toBeRemoved) {
            typeNodeMap.remove(presentId);
        }
        
        List<RowDTO> rowsToReturn = new ArrayList<>();
        for (RowDTO dto : curResults.getItems()) {
            // update cached that remain
            UpdatableNode currentlyCached = typeNodeMap.get(dto.getId());
            if (currentlyCached != null) {
                currentlyCached.update(curResults, dto);
            } else {
                // add new items
                typeNodeMap.put(dto, createNewNode(curResults, dto));
            }
            
            rowsToReturn.add(dto);
        }

        toPopulate.addAll(rowsToReturn);
        return true;
    }
    
    protected UpdatableNode createNewNode(SearchResultsDTO searchResults, RowDTO rowData) {
        
    }

    @Override
    protected Node createNodeForKey(RowDTO key) {
        return typeNodeMap.get(key);
    }

    public void update(SearchResultsDTO newResults) {
        this.results = newResults;
        this.refresh(false);
    }
}
