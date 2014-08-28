/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.filtering;

import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.FilterRow;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.AtomicFilter;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.UnionFilter;
import org.sleuthkit.autopsy.coreutils.FXMLConstructor;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/**
 * FXML Controller class
 *
 */
public class FilterPane extends TitledPane {

    @FXML
    private ResourceBundle resources;
    @FXML
    private URL location;
    @FXML
    private VBox filtersBox;
    @FXML
    private CheckBox selectedBox;
    private UnionFilter<AtomicFilter> filter;
    private Map<AtomicFilter, FilterRow> filterRowMap = new TreeMap<>(AtomicFilter.ALPHABETIC_COMPARATOR);

    @FXML
    void initialize() {
        assert filtersBox != null : "fx:id=\"filtersBox\" was not injected: check your FXML file 'FilterPane.fxml'.";
        assert selectedBox != null : "fx:id=\"selectedBox\" was not injected: check your FXML file 'FilterPane.fxml'.";
    }

    public FilterPane() {
        FXMLConstructor.construct(this, "FilterPane.fxml");
    }

    private void rebuildChildren() {
        filtersBox.getChildren().clear();
        for (FilterRow af : filterRowMap.values()) {
            filtersBox.getChildren().add(af);
        }
    }

    void setFilter(UnionFilter<AtomicFilter> filter) {
//TODO : do this more reasonably
        this.filter = filter;
        this.setText(filter.getDisplayName());
        filterRowMap.clear();
        filtersBox.getChildren().clear();
        for (AtomicFilter af : filter.subFilters) {
            final FilterRow filterRow = af.getUI();
            filterRowMap.put(af, filterRow);
        }
        rebuildChildren();
        
        this.filter.subFilters.addListener(new ListChangeListener<AtomicFilter>() {
            @Override
            public void onChanged(ListChangeListener.Change<? extends AtomicFilter> change) {
                while (change.next()) {
                    for (AtomicFilter af : change.getAddedSubList()) {
                        FilterRow filterRow = af.getUI();
                        filterRowMap.put(af, filterRow);
                    }
                    for (AtomicFilter af : change.getRemoved()) {
                      filterRowMap.remove(af);
                    }
                }
                rebuildChildren();
            }
        });

        this.filter.active.bindBidirectional(selectedBox.selectedProperty());
    }
}
