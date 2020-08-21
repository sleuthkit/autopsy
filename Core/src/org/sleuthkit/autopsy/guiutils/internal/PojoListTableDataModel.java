/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.guiutils.internal;

import java.util.List;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * An interface to be used with the DataResultJTable that specifies a TableModel
 * and TableColumnModel to be used with a JTable based on a list of object type
 * T.
 */
public interface PojoListTableDataModel<T> extends TableModel {

    /**
     * @return The TableColumnModel to be used with the jtable.
     */
    TableColumnModel getTableColumnModel();

    /**
     * @return The list of objects supporting the rows to be displayed in the
     *         table.
     */
    List<T> getDataRows();

    /**
     * Sets the list of objects to be displayed in the table.
     * @param dataRows The datarows to be displayed.
     */
    void setDataRows(List<T> dataRows);
}
