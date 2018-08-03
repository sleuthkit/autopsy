/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.guiutils;

import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import static javax.swing.SwingConstants.CENTER;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A JTable and outline view cell renderer that represents a status as a
 * center-aligned icon, and grays out the cell if the table is disabled. The
 * statuses represented are OK, WARNING, and ERROR.
 */
public class StatusIconCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;
    static final ImageIcon OK_ICON = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/tick.png", false));
    static final ImageIcon WARNING_ICON = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/warning16.png", false));
    static final ImageIcon ERROR_ICON = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/cross-script.png", false));

    @Messages({
        "StatusIconCellRenderer.tooltiptext.ok=OK",
        "StatusIconCellRenderer.tooltiptext.warning=A warning occurred",
        "StatusIconCellRenderer.tooltiptext.error=An error occurred"
    })
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setHorizontalAlignment(CENTER);
        Object switchValue = null;
        if ((value instanceof NodeProperty)) {
            //The Outline view has properties in the cell, the value contained in the property is what we want
            try {
                switchValue = ((Node.Property) value).getValue();
            } catch (IllegalAccessException | InvocationTargetException ex) {
                //Unable to get the value from the NodeProperty no Icon will be displayed
            }
        } else {
            //JTables contain the value we want directly in the cell
            switchValue = value;
        }
        if ((switchValue instanceof Status)) {
            switch ((Status) switchValue) {
                case OK:
                    setIcon(OK_ICON);
                    setToolTipText(org.openide.util.NbBundle.getMessage(StatusIconCellRenderer.class, "StatusIconCellRenderer.tooltiptext.ok"));
                    break;
                case WARNING:
                    setIcon(WARNING_ICON);
                    setToolTipText(org.openide.util.NbBundle.getMessage(StatusIconCellRenderer.class, "StatusIconCellRenderer.tooltiptext.warning"));
                    break;
                case ERROR:
                    setIcon(ERROR_ICON);
                    setToolTipText(org.openide.util.NbBundle.getMessage(StatusIconCellRenderer.class, "StatusIconCellRenderer.tooltiptext.error"));
                    break;
            }
        } else {
            setIcon(null);
            setText("");
        }
        grayCellIfTableNotEnabled(table, isSelected);

        return this;
    }

    public enum Status {
        OK,
        WARNING,
        ERROR
    }
}
