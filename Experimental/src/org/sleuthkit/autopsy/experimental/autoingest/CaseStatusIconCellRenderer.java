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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import static javax.swing.SwingConstants.CENTER;
import org.openide.util.ImageUtilities;

/**
 * A JTable cell renderer that represents an auto ingest alert file exists flag
 * as a center-aligned icon, and grays out the cell if the table is disabled.
 */
class CaseStatusIconCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;
    static final ImageIcon checkedIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/tick.png", false));
    static final ImageIcon warningIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/warning16.png", false));

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setHorizontalAlignment(CENTER);
        if ((value instanceof Boolean)) {
            if (true == (Boolean) value) {
                setIcon(warningIcon);
                setToolTipText(org.openide.util.NbBundle.getMessage(CaseStatusIconCellRenderer.class, "CaseStatusIconCellRenderer.tooltiptext.warning"));
            } else {
                setIcon(checkedIcon);
                setToolTipText(org.openide.util.NbBundle.getMessage(CaseStatusIconCellRenderer.class, "CaseStatusIconCellRenderer.tooltiptext.ok"));
            }
        }
        grayCellIfTableNotEnabled(table, isSelected);

        return this;
    }
}
