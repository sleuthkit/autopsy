/*
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Custom Cell renderer to display a SQLite column cell as readable Epoch date/time 
 *
 */
class EpochTimeCellRenderer extends DefaultTableCellRenderer {
    
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FileViewer.class.getName());

    private static final String FORMAT_STRING = "yyyy/MM/dd HH:mm:ss"; //NON-NLS
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(FORMAT_STRING);

    private final boolean renderAsEpoch;
    
    EpochTimeCellRenderer(boolean renderAsEpoch) {
        this.renderAsEpoch = renderAsEpoch;
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        
        // Set the forceground/background so its obvious when the cell is selected.
        if (isSelected) {
            super.setForeground(table.getSelectionForeground());
            super.setBackground(table.getSelectionBackground());
        } else {
            super.setForeground( table.getForeground());
            super.setBackground( table.getBackground());
        }
        
        if (value == null) {
            setText("");
        }
        else {
            String textStr = "";
            try {
                // get the col property value
                if (value instanceof Node.Property<?>) {
                    Node.Property<?> nodeProp = (Node.Property)value;
                    textStr = nodeProp.getValue().toString();
                }
                
                if (renderAsEpoch) {
                    long epochTime = Long.parseUnsignedLong(textStr);
                    if (epochTime > 0 ) {
                        Font font = getFont();
                        setFont(font.deriveFont(font.getStyle() | Font.ITALIC));
                        setText(DATE_FORMAT.format(new Date(epochTime)));
                    }
                    else {
                         setText(textStr);
                    }
                }
                else { // Display raw data
                    setText(textStr);
                }
            }
            catch (NumberFormatException e) {
                setText(textStr); 
                LOGGER.log(Level.INFO, "Error converting column value to number.", e); //NON-NLS
            } catch (IllegalAccessException | InvocationTargetException ex) {
                setText("");
                LOGGER.log(Level.SEVERE, "Error in getting column value.", ex); //NON-NLS
            }
        }
       
        return this;
    }
    
    boolean isRenderingAsEpoch() {
        return this.renderAsEpoch;
    }
    
}
