/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

import static javax.swing.SwingConstants.CENTER;

/**
 * A JTable cell renderer that center-aligns cell content and grays out the cell
 * if the table is disabled.
 */
class CenteredGrayableCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;

    public CenteredGrayableCellRenderer() {
        setHorizontalAlignment(CENTER);
    }
}
