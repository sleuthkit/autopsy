/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.ui;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;

final class CheckboxListSelectionModel extends DefaultListSelectionModel {

    private final javax.swing.JList<? extends JCheckBox> list;

    CheckboxListSelectionModel(javax.swing.JList<? extends JCheckBox> list) {
        this.list = list;
    }

    private static final long serialVersionUID = 1L;

    @Override
    public void setSelectionInterval(int index0, int index1) {
        setAnchorSelectionIndex(index0);
        setLeadSelectionIndex(index1);
        for (int i = 0; i < list.getModel().getSize(); i++) {
            list.getModel().getElementAt(index1).setSelected(i >= index0 && i <= index1);
        }
    }

    @Override
    public void addSelectionInterval(int index0, int index1) {
        setAnchorSelectionIndex(index0);
        setLeadSelectionIndex(index1);
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (i >= index0 && i <= index1) {
                list.getModel().getElementAt(i).setSelected(true);
            }
        }
    }

    @Override
    public void removeSelectionInterval(int index0, int index1) {
        setAnchorSelectionIndex(index0);
        setLeadSelectionIndex(index1);
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (i >= index0 && i < index1) {
                list.getModel().getElementAt(i).setSelected(false);
            }
        }
    }

    @Override
    public int getMinSelectionIndex() {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i).isSelected()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getMaxSelectionIndex() {
        for (int i = list.getModel().getSize() - 1; i >= 0; i--) {
            if (list.getModel().getElementAt(i).isSelected()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isSelectedIndex(int index) {
        return list.getModel().getElementAt(index).isSelected();
    }

    @Override
    public void clearSelection() {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            list.getModel().getElementAt(i).setSelected(false);
        }
    }

    @Override
    public boolean isSelectionEmpty() {
        boolean isEmpty = true;
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (list.getModel().getElementAt(i).isSelected()) {
                isEmpty = false;
                break;
            }
        }
        return isEmpty;
    }

    @Override
    public void insertIndexInterval(int index, int length, boolean before) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (i >= index && i < index + length) {
                list.getModel().getElementAt(i).setSelected(true);
            }
        }

    }

    @Override
    public void removeIndexInterval(int index0, int index1) {
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (i >= index0 && i < index1) {
                list.getModel().getElementAt(i).setSelected(false);
            }
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone(); //To change body of generated methods, choose Tools | Templates.
    }

}
