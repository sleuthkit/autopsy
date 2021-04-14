/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * A panel for showing any object in a check box list.
 */
public final class CheckBoxListPanel<T> extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<ObjectCheckBox<T>> model = new DefaultListModel<>();
    private final CheckBoxJList<ObjectCheckBox<T>> checkboxList;

    /**
     * Creates new CheckboxFilterPanel
     */
    public CheckBoxListPanel() {
        initComponents();

        checkboxList = new CheckBoxJList<>();
        checkboxList.setModel(model);
        scrollPane.setViewportView(checkboxList);
    }

    /**
     * Add a new element to the check box list.
     *
     * @param displayName display name for the checkbox
     * @param icon
     * @param obj         Object that the checkbox represents
     */
    public void addElement(String displayName, Icon icon, T obj) {
        ObjectCheckBox<T> newCheckBox = new ObjectCheckBox<>(displayName, icon, true, obj);

        if (!model.contains(newCheckBox)) {
            model.addElement(newCheckBox);
        }
    }

    /**
     * Remove all objects from the checkbox list.
     */
    public void clearList() {
        model.removeAllElements();
    }

    public boolean isEmpty() {
        return model.isEmpty();
    }

    @Override
    public void setEnabled(boolean enabled) {
        checkboxList.setEnabled(enabled);
        checkButton.setEnabled(enabled);
        uncheckButton.setEnabled(enabled);
        checkboxList.setEnabled(enabled);
    }

    /**
     * Returns a list of all of the selected elements.
     *
     * @return List of selected elements.
     */
    public List<T> getSelectedElements() {
        List<T> selectedElements = new ArrayList<>();
        Enumeration<ObjectCheckBox<T>> elements = model.elements();

        while (elements.hasMoreElements()) {
            ObjectCheckBox<T> element = elements.nextElement();
            if (element.isChecked()) {
                selectedElements.add(element.getObject());
            }
        }

        return selectedElements;
    }

    /**
     * Sets the selected items within the checkbox list panel.
     *
     * @param selected The items that should be selected. If the checkbox data
     *                 is present in this list, it will be selected, otherwise
     *                 it will be deselected.
     */
    public void setSelectedElements(List<T> selected) {
        Set<T> toSelect = selected == null ? Collections.emptySet() : new HashSet<>(selected);
        for (int i = 0; i < model.size(); i++) {
            ObjectCheckBox<T> item = model.get(i);
            boolean shouldBeSelected = toSelect.contains(item.getObject());
            if (item.isChecked() != shouldBeSelected) {
                item.setChecked(shouldBeSelected);
                model.set(i, item);
            }
        }
        checkboxList.repaint();
        checkboxList.revalidate();
    }

    /**
     * Sets the selection state of the all the check boxes in the list.
     *
     * @param selected True to check the boxes, false to unchecked
     */
    public void setSetAllSelected(boolean selected) {
        Enumeration<ObjectCheckBox<T>> enumeration = model.elements();
        while (enumeration.hasMoreElements()) {
            ObjectCheckBox<T> element = enumeration.nextElement();
            element.setChecked(selected);
            checkboxList.repaint();
            checkboxList.revalidate();
        }
    }

    /**
     * Sets the panel title.
     *
     * @param title Panel title or null for no title.
     */
    public void setPanelTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Sets the panel title icon.
     *
     * @param icon Icon to set or null for no icon
     */
    public void setPanelTitleIcon(Icon icon) {
        titleLabel.setIcon(icon);
    }

    /**
     * Add a list selection listener to the checkbox list contained in this
     * panel.
     *
     * @param listener The list selection listener to add.
     */
    public void addListSelectionListener(ListSelectionListener listener) {
        checkboxList.addListSelectionListener(listener);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        titleLabel = new javax.swing.JLabel();
        uncheckButton = new javax.swing.JButton();
        checkButton = new javax.swing.JButton();
        scrollPane = new javax.swing.JScrollPane();

        setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(titleLabel, org.openide.util.NbBundle.getMessage(CheckBoxListPanel.class, "CheckBoxListPanel.titleLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        add(titleLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(uncheckButton, org.openide.util.NbBundle.getMessage(CheckBoxListPanel.class, "CheckBoxListPanel.uncheckButton.text")); // NOI18N
        uncheckButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                uncheckButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 9);
        add(uncheckButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(checkButton, org.openide.util.NbBundle.getMessage(CheckBoxListPanel.class, "CheckBoxListPanel.checkButton.text")); // NOI18N
        checkButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        add(checkButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 9, 0);
        add(scrollPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void uncheckButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_uncheckButtonActionPerformed
        setSetAllSelected(false);
    }//GEN-LAST:event_uncheckButtonActionPerformed

    private void checkButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkButtonActionPerformed
        setSetAllSelected(true);
    }//GEN-LAST:event_checkButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton checkButton;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JLabel titleLabel;
    private javax.swing.JButton uncheckButton;
    // End of variables declaration//GEN-END:variables

    /**
     * Wrapper around T that implements CheckboxListItem
     *
     * @param <T>
     */
    final class ObjectCheckBox<T> implements CheckBoxJList.CheckboxListItem {

        private static final long serialVersionUID = 1L;

        private final T object;
        private final String displayName;
        private final Icon icon;
        private boolean checked;

        /**
         * Constructs a new ObjectCheckBox
         *
         * @param displayName  String to show as the check box label
         * @param icon         Icon to show before the check box (may be null)
         * @param initialState Sets the initial state of the check box
         * @param object       Object that the check box represents.
         */
        ObjectCheckBox(String displayName, Icon icon, boolean initialState, T object) {
            this.displayName = displayName;
            this.icon = icon;
            this.object = object;
            this.checked = initialState;
        }

        T getObject() {
            return object;
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        @Override
        public void setChecked(boolean checked) {
            if (this.checked != checked) {
                this.checked = checked;
                //notify the list that an items checked status changed
                if (!isEmpty()) {
                    for (ListSelectionListener listener : checkboxList.getListSelectionListeners()) {
                        listener.valueChanged(new ListSelectionEvent(checkboxList, 0, model.getSize() - 1, false));
                    }
                }
            }
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public boolean hasIcon() {
            return icon != null;
        }

        @Override
        public Icon getIcon() {
            return icon;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ObjectCheckBox) {
                return object.equals(((ObjectCheckBox) obj).object);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 31 * hash + Objects.hashCode(this.object);
            return hash;
        }
    }

}
