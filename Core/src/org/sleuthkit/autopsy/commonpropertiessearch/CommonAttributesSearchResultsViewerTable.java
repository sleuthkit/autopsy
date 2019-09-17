/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;

/**
 * <code>DataResultViewerTable</code> which overrides the default column header
 * width calculations. The <code>CommonAttributesSearchResultsViewerTable</code>
 * presents multiple tiers of data which are not always present and it may not
 * make sense to try to calculate the column widths for such tables by sampling
 * rows and looking for wide cells. Rather, we just pick some reasonable values.
 */
public class CommonAttributesSearchResultsViewerTable extends DataResultViewerTable {

    private static final Map<String, Integer> COLUMN_WIDTHS;
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CommonAttributesSearchResultsViewerTable.class.getName());

    private static final int DEFAULT_WIDTH = 100;

    static {
        Map<String, Integer> map = new HashMap<>();
        map.put(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.nameColLbl"), 260);
        map.put(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.score.name"), 20);
        map.put(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.comment.name"), 20);
        map.put(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.createSheet.count.name"), 20);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), 65);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), 300);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl(), 200);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_localPath(), 200);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_valueColLbl(), 200);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), 200);
        map.put(NbBundle.getMessage(AbstractAbstractFileNode.class, "AbstractAbstractFileNode.mimeType"), 130);

        COLUMN_WIDTHS = Collections.unmodifiableMap(map);
    }

    /**
     * Implements a DataResultViewerTable which constructs a tabular result
     * viewer that displays the children of the given root node using an
     * OutlineView. The explorer manager will be discovered at runtime.
     *
     * Adds a TreeExpansionsListener to the outlineView to receive tree
     * expansion events which dynamically loads children nodes when requested.
     */
    public CommonAttributesSearchResultsViewerTable() {
        super();
        addTreeExpansionListener(new InstanceCountNodeTreeExpansionListener());
    }

    @NbBundle.Messages({
        "CommonFilesSearchResultsViewerTable.noDescText= ",
        "CommonFilesSearchResultsViewerTable.instancesColLbl=Instances",
        "CommonFilesSearchResultsViewerTable.localPath=Parent Path in Current Case",
        "CommonFilesSearchResultsViewerTable.pathColLbl=Parent Path",
        "CommonFilesSearchResultsViewerTable.caseColLbl=Case",
        "CommonFilesSearchResultsViewerTable.valueColLbl=Value",
        "CommonFilesSearchResultsViewerTable.dataSourceColLbl=Data Source",
    })
    @Override
    protected void setColumnWidths() {
        TableColumnModel model = this.getColumnModel();

        Enumeration<TableColumn> columnsEnumerator = model.getColumns();
        while (columnsEnumerator.hasMoreElements()) {

            TableColumn column = columnsEnumerator.nextElement();

            final String headerValue = column.getHeaderValue().toString();

            final Integer defaultWidth = COLUMN_WIDTHS.get(headerValue);

            if (defaultWidth == null) {
                column.setPreferredWidth(DEFAULT_WIDTH);
                LOGGER.log(Level.WARNING, String.format("Tried to set width on a column not supported by the CommonAttributesSearchResultsViewerTable: %s", headerValue));
            } else {
                column.setPreferredWidth(defaultWidth);
            }
        }
    }
}
