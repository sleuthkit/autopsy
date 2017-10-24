/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
import org.openide.util.NbBundle.Messages;

/**
 * A JTable cell renderer that represents whether the priority value of a job
 * has ever been increased, tick if prioritized nothing if not.
 */
class PrioritizedIconCellRenderer extends GrayableCellRenderer {

    
    @Messages({
        "PrioritizedIconCellRenderer.prioritized.tooltiptext=This job has been prioritized. The most recently prioritized job should be processed next.",
        "PrioritizedIconCellRenderer.notPrioritized.tooltiptext=This job has not been prioritized."
    })
    private static final long serialVersionUID = 1L;
    static final ImageIcon checkedIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/tick.png", false));

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setHorizontalAlignment(CENTER);
        if ((value instanceof Integer)) {
            if ((int) value == 0) {
                setIcon(null);
                setToolTipText(org.openide.util.NbBundle.getMessage(CaseStatusIconCellRenderer.class, "PrioritizedIconCellRenderer.notPrioritized.tooltiptext"));
            } else {
                setIcon(checkedIcon);
                setToolTipText(org.openide.util.NbBundle.getMessage(CaseStatusIconCellRenderer.class, "PrioritizedIconCellRenderer.prioritized.tooltiptext"));
            }
        }
        grayCellIfTableNotEnabled(table, isSelected);

        return this;
    }
}
