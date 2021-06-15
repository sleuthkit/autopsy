/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
import java.lang.reflect.InvocationTargetException;
import javax.swing.ImageIcon;
import javax.swing.JTable;
import static javax.swing.SwingConstants.CENTER;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.guiutils.GrayableCellRenderer;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A JTable and Outline view cell renderer that represents whether OCR is enabled for the job.
 */
class OcrIconCellRenderer extends GrayableCellRenderer {

    @Messages({
        "OcrIconCellRenderer.enabled.tooltiptext=This job has OCR enabled.",
        "OcrIconCellRenderer.disabled.tooltiptext=This job does not have OCR enabled."
    })
    private static final long serialVersionUID = 1L;
    static final ImageIcon checkedIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/tick.png", false));

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setHorizontalAlignment(CENTER);
        Object switchValue = null;
        if ((value instanceof NodeProperty)) {
            //The Outline view has properties in the cell, the value contained in the property is what we want
            try {
                switchValue = ((Node.Property) value).getValue();
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                //Unable to get the value from the NodeProperty no Icon will be displayed
            }
        } else {
            //JTables contain the value we want directly in the cell
            switchValue = value;
        }
        if (switchValue instanceof Boolean && (boolean) switchValue == true) {
            setIcon(checkedIcon);
            setToolTipText(org.openide.util.NbBundle.getMessage(OcrIconCellRenderer.class, "OcrIconCellRenderer.enabled.tooltiptext"));
        } else {
            setIcon(null);
            if (switchValue instanceof Boolean) {
                setToolTipText(org.openide.util.NbBundle.getMessage(OcrIconCellRenderer.class, "OcrIconCellRenderer.disabled.tooltiptext"));
            }
        }
        grayCellIfTableNotEnabled(table, isSelected);

        return this;
    }
}
