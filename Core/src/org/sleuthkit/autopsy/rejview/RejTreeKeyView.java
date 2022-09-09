/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
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
package org.sleuthkit.autopsy.rejview;

import com.williballenthin.rejistry.RegistryParseException;
import com.williballenthin.rejistry.RegistryValue;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * JPanel for displaying the RejTreeKeyView
 */
public final class RejTreeKeyView extends RejTreeNodeView {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(RejTreeKeyView.class.getName());

    @Messages({"RejTreeKeyView.failedToParse.keyName=FAILED TO PARSE KEY NAME",
        "RejTreeKeyView.columns.name=Name",
        "RejTreeKeyView.columns.type=Type",
        "RejTreeKeyView.columns.value=Value",
        "RejTreeKeyView.metadataBorder.title=Metadata",
        "RejTreeKeyView.valuesBorder.title=Values",
        "RejTreeKeyView.template.name=Name:",
        "RejTreeKeyView.template.numberOfSubkeys=Number of subkeys:",
        "RejTreeKeyView.template.numberOfValues=Number of values:",
        "RejTreeKeyView.template.dateTime=Modification Time:"})
    public RejTreeKeyView(RejTreeKeyNode node) {
        super(new BorderLayout());

        /*
         * param 1 Name
         * param 2 Number of subkeys
         * param 3 Number of values
         * param 4 Date/time
         */
        String metadataTemplate = "<html><i>"
                + Bundle.RejTreeKeyView_template_name()
                + "</i><b>  %1$s</b><br/><i>"
                + Bundle.RejTreeKeyView_template_numberOfSubkeys()
                + "</i>  %2$d<br/><i>"
                + Bundle.RejTreeKeyView_template_numberOfValues()
                + "</i>  %3$d<br/><i>"
                + Bundle.RejTreeKeyView_template_dateTime()
                + "</i>  %4$s</br></html>";
        String keyName;
        int numSubkeys;
        int numValues;
        String dateTime;

        try {
            keyName = node.getKey().getName();
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.WARNING, "Failed to parse key name", ex);
            keyName = Bundle.RejTreeKeyView_failedToParse_keyName();
        }

        try {
            numSubkeys = node.getKey().getSubkeyList().size();
        } catch (RegistryParseException ex) {
            logger.log(Level.WARNING, "Failed to get subkeylist from key", ex);
            numSubkeys = -1;
        }

        try {
            numValues = node.getKey().getValueList().size();
        } catch (RegistryParseException ex) {
            logger.log(Level.WARNING, "Failed to get value list from key", ex);
            numValues = -1;
        }

        Date date = new java.util.Date(node.getKey().getTimestamp().getTimeInMillis()); 
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z"); 
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+0")); 
        dateTime = sdf.format(date);

        JLabel metadataLabel = new JLabel(String.format(metadataTemplate, keyName, numSubkeys, numValues, dateTime), JLabel.LEFT);
        metadataLabel.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeKeyView_metadataBorder_title()));
        metadataLabel.setVerticalAlignment(SwingConstants.TOP);

        String[] columnNames = {Bundle.RejTreeKeyView_columns_name(), Bundle.RejTreeKeyView_columns_type(), Bundle.RejTreeKeyView_columns_value()};
        Object[][] data = new Object[numValues][3];

        try {
            Iterator<RegistryValue> valit = node.getKey().getValueList().iterator();
            int i = 0;
            while (valit.hasNext()) {
                RegistryValue val = valit.next();
                if (val.getName().length() == 0) {
                    data[i][0] = RejTreeValueNode.DEFAULT_VALUE_NAME;
                } else {
                    data[i][0] = val.getName();
                }
                data[i][1] = val.getValueType().toString();
                data[i][2] = RegeditExeValueFormatter.format(val.getValue());
                i++;
            }
        } catch (RegistryParseException | UnsupportedEncodingException ex) {
            logger.log(Level.WARNING, "Error while getting RegistryValues.", ex);
            //some data may have already been added but not necessarily all of it
        }

        JTable table = new JTable(data, columnNames);
        table.setAutoCreateRowSorter(true);
        table.setCellSelectionEnabled(false);
        table.setRowSelectionAllowed(true);
        table.setIntercellSpacing(new Dimension(10, 1));

        // inspiration for packing the columns from:
        //   http://jroller.com/santhosh/entry/packing_jtable_columns
        if (table.getColumnCount() > 0) {
            int width[] = new int[table.getColumnCount()];
            int total = 0;
            for (int j = 0; j < width.length; j++) {
                TableColumn column = table.getColumnModel().getColumn(j);
                int w = (int) table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table, column.getIdentifier(), false, false, -1, j).getPreferredSize().getWidth();

                if (table.getRowCount() > 0) {
                    for (int i = 0; i < table.getRowCount(); i++) {
                        int pw = (int) table.getCellRenderer(i, j).getTableCellRendererComponent(table, table.getValueAt(i, j), false, false, i, j).getPreferredSize().getWidth();
                        w = Math.max(w, pw);
                    }
                }
                width[j] += w + table.getIntercellSpacing().width;
                total += w + table.getIntercellSpacing().width;
            }
            width[width.length - 1] += table.getVisibleRect().width - total;
            TableColumnModel columnModel = table.getColumnModel();
            for (int j = 0; j < width.length; j++) {
                TableColumn column = columnModel.getColumn(j);
                table.getTableHeader().setResizingColumn(column);
                column.setWidth(width[j]);
            }
        }

        JScrollPane valuesPane = new JScrollPane(table);
        valuesPane.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeKeyView_valuesBorder_title()));

        this.add(metadataLabel, BorderLayout.NORTH);
        this.add(valuesPane, BorderLayout.CENTER);
    }
}
