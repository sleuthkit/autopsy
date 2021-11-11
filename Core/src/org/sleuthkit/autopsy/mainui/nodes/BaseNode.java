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

import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.mainui.datamodel.BaseRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;

/**
 * A a simple starting point for nodes.
 */
abstract class BaseNode<S extends SearchResultsDTO, R extends BaseRowDTO> extends AbstractNode implements ActionContext {

    private final S results;
    private final R rowData;

    BaseNode(Children children, Lookup lookup, S results, R rowData) {
        super(children, lookup);
        this.results = results;
        this.rowData = rowData;
    }

    /**
     * Returns the SearchResultDTO object.
     *
     * @return
     */
    S getSearchResultsDTO() {
        return results;
    }

    /**
     * Returns the RowDTO for this node.
     *
     * @return A RowDTO object.
     */
    R getRowDTO() {
        return rowData;
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), results.getColumns(), rowData.getCellValues());
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }
}
