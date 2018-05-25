/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.util.ArrayList;
import java.util.Date;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * RowSorter which makes columns whose type is Date to be sorted first in
 * Descending order then in Ascending order
 */
class AutoIngestRowSorter<M extends DefaultTableModel> extends TableRowSorter<M> {

    AutoIngestRowSorter(M tModel) {
        super(tModel);
    }

    @Override
    public void toggleSortOrder(int column) {
        SortOrder firstSort = SortOrder.ASCENDING;
        SortOrder secondSort = SortOrder.DESCENDING;
        
        if (this.getModel().getColumnClass(column).equals(Date.class) || this.getModel().getColumnClass(column).equals(Integer.class)) {
            firstSort = SortOrder.DESCENDING;
            secondSort = SortOrder.ASCENDING;
        }

        ArrayList<RowSorter.SortKey> sortKeys = new ArrayList<>(getSortKeys());
        if (sortKeys.isEmpty() || sortKeys.get(0).getSortOrder() == SortOrder.UNSORTED) {
            sortKeys.removeIf(key -> key.getColumn() == column);
            sortKeys.add(0, new RowSorter.SortKey(column, firstSort));
        } else if (sortKeys.get(0).getSortOrder() == firstSort) {
            sortKeys.removeIf(key -> key.getColumn() == column);
            sortKeys.add(0, new RowSorter.SortKey(column, secondSort));
        } else {
            sortKeys.removeIf(key -> key.getColumn() == column);
            sortKeys.add(0, new RowSorter.SortKey(column, SortOrder.UNSORTED));
        }
        setSortKeys(sortKeys);
    }
}
