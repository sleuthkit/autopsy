/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataListener;

/**
 * Encapsulates meta data needed to populate the data source selection drop down menu
 */ 
public class DataSourceComboBoxModel extends AbstractListModel<String> implements ComboBoxModel<String> {

    private static final long serialVersionUID = 1L;
    private final String[] dataSourceList;
    private String selection = null;

    /**
     * Use this to initialize the panel
     */
    DataSourceComboBoxModel() {
        this.dataSourceList = new String[0];
    }

    /**
     * Use this when we have data to display.
     *
     * @param theDataSoureList names of data sources for user to pick from
     */
    public DataSourceComboBoxModel(String... theDataSoureList) {
        dataSourceList = theDataSoureList.clone();
    }

    @Override
    public void setSelectedItem(Object anItem) {
        selection = (String) anItem;
    }

    @Override
    public Object getSelectedItem() {
        return selection;
    }

    @Override
    public int getSize() {
        return dataSourceList.length;
    }

    @Override
    public String getElementAt(int index) {
        return dataSourceList[index];
    }

    @Override
    public void addListDataListener(ListDataListener listener) {
        this.listenerList.add(ListDataListener.class, listener);
    }

    @Override
    public void removeListDataListener(ListDataListener listener) {
        this.listenerList.remove(ListDataListener.class, listener);
    }
}
