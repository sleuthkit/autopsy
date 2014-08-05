/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * Union(or) filter
 */
abstract public class UnionFilter<T extends AbstractFilter> extends CompoundFilter<T> {

    public UnionFilter(ObservableList<T> subFilters) {
        super(subFilters);
    }

    public UnionFilter() {
        super(FXCollections.<T>observableArrayList());
    }

    @Override
    public Boolean accept(DrawableFile df) {
        if (isActive()) {
            for (T f : subFilters) {
                if (f.isActive() && f.accept(df) == true) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }

    }
}
