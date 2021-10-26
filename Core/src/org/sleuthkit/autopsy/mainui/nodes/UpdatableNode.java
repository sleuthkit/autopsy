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
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.mainui.datamodel.RowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * Node that can update itself based on updated search results and row.
 */
public abstract class UpdatableNode extends AbstractNode {

    protected UpdatableNode(Children children) {
        super(children);
    }

    protected UpdatableNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    /**
     * Triggers an update in rendering (i.e. display name) based on data for
     * this row.
     *
     * @param results The results in which this row appears.
     * @param row     The backing row data.
     */
    public abstract void update(SearchResultsDTO results, RowDTO row);
}
