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


import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Factory for populating tree with results.
 */
public class TreeChildFactory<T> extends ChildFactory<T> {
    private static final Logger logger = Logger.getLogger(TreeChildFactory.class.getName());
    private SearchResultsDTO results;

    public TreeChildFactory(SearchResultsDTO initialResults) {
        this.results = initialResults;
    }

    @Override
    protected boolean createKeys(List<T> toPopulate) {

    }

    @Override
    protected Node createNodeForKey(T key) {
        
    }

    public void update(SearchResultsDTO newResults) {
        this.results = newResults;
        this.refresh(false);
    }

    public long getResultCount() {
        return results == null ? 0 : results.getTotalResultsCount();
    }
}
