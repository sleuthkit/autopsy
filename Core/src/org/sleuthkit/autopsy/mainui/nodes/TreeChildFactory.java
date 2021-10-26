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
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.CountsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Factory for populating tree with results.
 */
public class TreeChildFactory<T extends CountsRowDTO> extends ChildFactory<Long> {
    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());
    private SearchResultsDTO results;

    private final Map<Long, TreeCountNode<?>> typeNodeMap = new HashMap<>();
    
    public TreeChildFactory(SearchResultsDTO initialResults) {
        this.results = initialResults;
    }

    @Override
    protected boolean createKeys(List<Long> toPopulate) {
        SearchResultsDTO curResults = this.results;
        Set<Long> resultsRowIds = curResults.getItems().stream()
                .map(row -> row.getId())
                .collect(Collectors.toSet());
        
        // remove no longer present
        Set<Long> toBeRemoved = new HashSet<>(typeNodeMap.keySet());
        toBeRemoved.removeAll(resultsRowIds);
        for (Long presentId : toBeRemoved) {
            typeNodeMap.remove(presentId);
        }
        
        List<CountsRowDTO<?>> rowsToReturn = new ArrayList<>();
        for (CountsRowDTO<?> dto : curResults.getItems()) {
            // update cached that remain
            TreeCountNode<?> currentlyCached = typeNodeMap.get(dto.getId());
            if (currentlyCached != null) {
                currentlyCached.updateData(dto);
            } else {
                // add new items
                typeNodeMap.put(dto.getId(), new TreeCountNode<?>(dto));
            }
            
            idsToReturn.add(dto);
        }
        
        List<Long> idsToReturn = rowsToReturn.stream()
                .sorted((a,b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .map(row -> row.getId())
                .collect(Collectors.toList());
        
        toPopulate.addAll(idsToReturn);
        return true;
    }

    @Override
    protected Node createNodeForKey(T key) {
        return new TreeCountNode<>();
    }
    
    public void update(SearchResultsDTO newResults) {
        this.results = newResults;
        this.refresh(false);
    }

    public long getResultCount() {
        return results == null ? 0 : results.getTotalResultsCount();
    }
}
