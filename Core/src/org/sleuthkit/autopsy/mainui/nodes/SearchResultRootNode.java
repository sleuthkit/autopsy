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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * A node whose children will be displayed in the results view and determines
 * children based on a SearchResultDTO.
 */
public class SearchResultRootNode extends AbstractNode {

    private final SearchResultChildFactory factory;

    public SearchResultRootNode(SearchResultsDTO initialResults) {
        this(initialResults, new SearchResultChildFactory(initialResults));
    }

    private SearchResultRootNode(SearchResultsDTO initialResults, SearchResultChildFactory factory) {
        super(Children.create(factory, true));
        this.factory = factory;

        setName(initialResults.getTypeId());
        setDisplayName(initialResults.getDisplayName());
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.name"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.displayName"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.desc"),
                getDisplayName()));

        sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.name"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.displayName"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.desc"),
                this.factory.getResultCount()));

        return sheet;
    }
    
    public void updateChildren(SearchResultsDTO updatedResults) {
        this.factory.update(updatedResults);
    }
}
