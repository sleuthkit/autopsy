/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.experimental.autoingest;

import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import static javax.swing.SwingConstants.CENTER;
import org.openide.util.ImageUtilities;

/**
 * A JTable cell renderer that represents a CaseStatus object as a
 * center-aligned icon, and grays out the cell if the table is disabled.
 */
class CaseStatusIconCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;
    static final ImageIcon checkedIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/tick.png", false));
    static final ImageIcon warningIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/warning16.png", false));

    CaseStatusIconCellRenderer() {
        setHorizontalAlignment(CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if ((value instanceof CaseStatus)) {
            switch ((CaseStatus) value) {
                case CANCELLATIONS:
                case ERRORS:
                case INTERRUPTS:
                    setIcon(warningIcon);
                    setToolTipText(org.openide.util.NbBundle.getMessage(CaseStatusIconCellRenderer.class, "CaseStatusIconCellRenderer.tooltiptext.warning"));
                    break;
                case OK:
                default:
                    setIcon(checkedIcon);
                    setToolTipText(org.openide.util.NbBundle.getMessage(CaseStatusIconCellRenderer.class, "CaseStatusIconCellRenderer.tooltiptext.ok"));
                    break;
            }
        }
        grayCellIfTableNotEnabled(table, isSelected);

        return this;
    }
}
