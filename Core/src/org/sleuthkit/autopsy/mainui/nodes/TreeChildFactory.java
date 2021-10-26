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


import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.Artifacts;
import org.sleuthkit.autopsy.mainui.datamodel.CountsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Factory for populating tree with results.
 */
public class TreeChildFactory<T> extends ChildFactory<CountsRowDTO<T>> {
    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());
    private SearchResultsDTO results;

    private final Map<Long, CountsRowDTO<T>> typeNodeMap = new HashMap<>();
    
    public TreeChildFactory(SearchResultsDTO initialResults) {
        this.results = initialResults;
    }

    @Override
    protected boolean createKeys(List<CountsRowDTO<T>> toPopulate) {
        
        
        List<CountsRowDTO<T>> rowsToAdd = typeNodeMap.values().stream()
                .sorted((a,b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .collect(Collectors.toList());
        
        toPopulate.addAll(rowsToAdd);
        return true;
        
                List<Artifacts.TypeNodeKey> allKeysSorted = types.stream()
                        // filter types by category and ensure they are not in the list of ignored types
                        .filter(tp -> category.equals(tp.getCategory()) && !IGNORED_TYPES.contains(tp))
                        .map(tp -> {
                            // if typeNodeMap already contains key, update the relevant node and return the node
                            if (typeNodeMap.containsKey(tp)) {
                                Artifacts.TypeNodeKey typeKey = typeNodeMap.get(tp);
                                typeKey.getNode().updateDisplayName();
                                return typeKey;
                            } else {
                                // if key is not in map, create the type key and add to map
                                Artifacts.TypeNodeKey newTypeKey = getTypeKey(tp, skCase, filteringDSObjId);
                                for (BlackboardArtifact.Type recordType : newTypeKey.getApplicableTypes()) {
                                    typeNodeMap.put(recordType, newTypeKey);
                                }
                                return newTypeKey;
                            }
                        })
                        // ensure record is returned
                        .filter(record -> record != null)
                        // there are potentially multiple types that apply to the same node (i.e. Interesting Files / Artifacts)
                        // ensure the keys are distinct
                        .distinct()
                        // sort by display name
                        .sorted((a, b) -> {
                            String aSafe = (a.getNode() == null || a.getNode().getDisplayName() == null) ? "" : a.getNode().getDisplayName();
                            String bSafe = (b.getNode() == null || b.getNode().getDisplayName() == null) ? "" : b.getNode().getDisplayName();
                            return aSafe.compareToIgnoreCase(bSafe);
                        })
                        .collect(Collectors.toList());

                list.addAll(allKeysSorted);
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
