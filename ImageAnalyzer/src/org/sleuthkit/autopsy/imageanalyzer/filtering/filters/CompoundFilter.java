/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 */
public abstract class CompoundFilter<T extends AbstractFilter> extends AbstractFilter {

    public ObservableList<T> subFilters;

    public CompoundFilter(ObservableList< T> subFilters) {
        this.subFilters = FXCollections.synchronizedObservableList(subFilters);
    }

    public void clear() {
        for (T filter : subFilters) {
            filter.clear();
        }
        active.set(true);
        subFilters.clear();
    }
}
