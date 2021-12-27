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

import java.util.Optional;
import java.util.stream.Stream;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.mainui.datamodel.CreditCardByFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * A node representing a single row when viewing credit cards by file.
 */
public class CreditCardByFileNode extends BaseNode<SearchResultsDTO, CreditCardByFileRowDTO> {

    private static Object[] getLookupItems(CreditCardByFileRowDTO rowData) {
        return Stream.of(rowData.getAssociatedArtifacts().stream(), Stream.of(rowData.getFile()))
                .flatMap(s -> s)
                .toArray();
    }
    
    public CreditCardByFileNode(SearchResultsDTO results, CreditCardByFileRowDTO rowData) {
            super(Children.LEAF, 
                Lookups.fixed(getLookupItems(rowData)),
                results, 
                rowData);
            
            setName(rowData.getFileName() + rowData.getId());
            setDisplayName(rowData.getFileName());
            setIconBaseWithExtension(NodeIconUtil.FILE.getPath());
    }
    
    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }
    
    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }
    
    @Override
    public boolean supportsTableExtractActions() {
        return true;
    }
    
}
