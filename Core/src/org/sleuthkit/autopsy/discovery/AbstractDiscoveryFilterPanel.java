/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery;

import java.awt.event.ActionListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.event.ListSelectionListener;


abstract class AbstractDiscoveryFilterPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Setup the file filter settings.
     *
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged or that there are no items to select.
     */
    abstract void configurePanel(boolean selected, int[] indicesSelected);

    abstract JCheckBox getCheckbox();

    abstract JList<?> getList();

    abstract JLabel getAdditionalLabel();

    abstract String checkForError();

    /**
     * Add listeners to the checkbox/list set if listeners have not already been
     * added.
     *
     * @param listener
     * @param listListener
     */
    void addListeners(ActionListener actionListener, ListSelectionListener listListener) {
        if (getCheckbox() != null) {
            getCheckbox().addActionListener(actionListener);
        }
        if (getList() != null) {
            getList().addListSelectionListener(listListener);
        }
    }

    abstract FileSearchFiltering.FileFilter getFilter();

    void removeListeners() {
        if (getCheckbox() != null) {
            for (ActionListener listener : getCheckbox().getActionListeners()) {
                getCheckbox().removeActionListener(listener);
            }
        }
        if (getList() != null) {
            for (ListSelectionListener listener : getList().getListSelectionListeners()) {
                getList().removeListSelectionListener(listener);
            }
        }
    }

    boolean hasPanel() {
        return true;
    }

}
