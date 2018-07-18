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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;

/**
 * <code>DataResultViewerTable</code> which overrides the default column
 * header width calculations.  The <code>CommonAttributesSearchResultsViewerTable</code>
 * presents multiple tiers of data which are not always present and it may not 
 * make sense to try to calculate the column widths for such tables by sampling 
 * rows and looking for wide cells.  Rather, we just pick some reasonable values.
 */
public class CommonAttributesSearchResultsViewerTable extends DataResultViewerTable {

    private static final Map<String, Integer> COLUMN_WIDTHS;
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CommonFilesSearchResultsViewerTable.class.getName());
    
    private static final int DEFAULT_WIDTH = 100;

    static {
        Map<String, Integer> map = new HashMap<>();
        map.put(Bundle.CommonFilesSearchResultsViewerTable_filesColLbl(), 260);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_instancesColLbl(), 65);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_pathColLbl(), 300);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_caseColLbl1(), 200);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_dataSourceColLbl(), 200);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_hashsetHitsColLbl(), 100);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_mimeTypeColLbl(), 130);
        map.put(Bundle.CommonFilesSearchResultsViewerTable_tagsColLbl1(), 300);;

        COLUMN_WIDTHS = Collections.unmodifiableMap(map);
    }

    @NbBundle.Messages({
        "CommonFilesSearchResultsViewerTable.noDescText= ",
        "CommonFilesSearchResultsViewerTable.filesColLbl=Files",
        "CommonFilesSearchResultsViewerTable.instancesColLbl=Instances",
        "CommonFilesSearchResultsViewerTable.pathColLbl=Parent Path",
        "CommonFilesSearchResultsViewerTable.hashsetHitsColLbl=Hash Set Hits",
        "CommonFilesSearchResultsViewerTable.caseColLbl1=Case",
        "CommonFilesSearchResultsViewerTable.dataSourceColLbl=Data Source",
        "CommonFilesSearchResultsViewerTable.mimeTypeColLbl=MIME Type",
        "CommonFilesSearchResultsViewerTable.tagsColLbl1=Tags"
    })
    @Override
    protected void setColumnWidths() {
        TableColumnModel model = this.getColumnModel();

        Enumeration<TableColumn> columnsEnumerator = model.getColumns();
        while (columnsEnumerator.hasMoreElements()) {

            TableColumn column = columnsEnumerator.nextElement();

            final String headerValue = column.getHeaderValue().toString();

            final Integer defaultWidth = COLUMN_WIDTHS.get(headerValue);
            
            if(defaultWidth == null){
                column.setPreferredWidth(DEFAULT_WIDTH);
                LOGGER.log(Level.SEVERE, String.format("Tried to set width on a column not supported by the CommonFilesSearchResultsViewerTable: %s", headerValue));
            } else {
                column.setPreferredWidth(defaultWidth);
            }
        }
    }
}
