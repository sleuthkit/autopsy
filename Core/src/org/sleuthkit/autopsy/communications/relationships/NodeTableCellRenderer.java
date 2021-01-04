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
package org.sleuthkit.autopsy.communications.relationships;

import java.awt.Component;
import java.beans.FeatureDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * TableCellRenderer for NodeProperty with custom tooltip data.
 */
final class NodeTableCellRenderer extends DefaultTableCellRenderer {

    private static final long serialVersionUID = 1L;
    
    private static final Logger logger = Logger.getLogger(NodeTableCellRenderer.class.getName());

    @Override
    public Component getTableCellRendererComponent(JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        String descr = "";
        Object theRealValue = value;
        if (value instanceof NodeProperty) {
            descr = ((FeatureDescriptor) value).getShortDescription();
            try {
                theRealValue = ((Node.Property<?>) value).getValue();
            } catch (IllegalAccessException | InvocationTargetException ex) {
                logger.log(Level.WARNING, "Unable to get NodeProperty cell value.");
            }
        }

        super.getTableCellRendererComponent(table, theRealValue, isSelected, hasFocus, row, column);

        setToolTipText(descr);

        return this;

    }
}
