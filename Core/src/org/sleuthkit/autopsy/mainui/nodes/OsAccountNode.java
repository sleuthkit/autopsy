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
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.OsAccountRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * A node representing a row for an OsAccount in the results table.
 */
public class OsAccountNode extends BaseNode<SearchResultsDTO, OsAccountRowDTO>{
    
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/os-account.png";
    
    public OsAccountNode(SearchResultsDTO results, OsAccountRowDTO rowData) {
        super(Children.LEAF, 
                Lookups.fixed(rowData.getContent(), new TskContentItem<>(rowData.getContent())),
                results, 
                rowData);
        String name = rowData.getContent().getName();
        setName(name);
        setDisplayName(name);
        setShortDescription(name);
        setIconBaseWithExtension(ICON_PATH);
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
