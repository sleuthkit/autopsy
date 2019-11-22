/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * A JList that renders the list items as check boxes.
 */
final class CheckBoxJList<T extends CheckBoxJList.CheckboxListItem> extends JList<T> {

    private static final long serialVersionUID = 1L;

    /**
     * Simple interface that must be implement for an object to be displayed as
     * a checkbox in CheckBoxJList.
     *
     */
    interface CheckboxListItem {

        /**
         * Returns the checkbox state.
         *
         * @return True if the check box should be checked
         */
        boolean isChecked();

        /**
         * Set the state of the check box.
         *
         * @param checked
         */
        void setChecked(boolean checked);

        /**
         * Returns String to display as the check box label
         *
         * @return
         */
        String getDisplayName();
    }

    /**
     * Construct a new JCheckBoxList.
     */
    CheckBoxJList() {
        initalize();
    }

    /**
     * Do all of the UI initialization.
     */
    private void initalize() {
        setCellRenderer(new CellRenderer());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = locationToIndex(e.getPoint());
                if (index != -1) {
                    CheckBoxJList.CheckboxListItem element = getModel().getElementAt(index);
                    element.setChecked(!element.isChecked());
                    repaint();
                }
            }
        });
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    /**
     * A ListCellRenderer that renders list elements as check boxes.
     */
    class CellRenderer extends JCheckBox implements ListCellRenderer<CheckBoxJList.CheckboxListItem> {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends CheckBoxJList.CheckboxListItem> list, CheckBoxJList.CheckboxListItem value, int index,
                boolean isSelected, boolean cellHasFocus) {

            setBackground(list.getBackground());
            setSelected(value.isChecked());
            setText(value.getDisplayName());
            return this;
        }
    }
}
