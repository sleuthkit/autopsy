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
 * Intersection(And) filter
 */
public abstract class IntersectionFilter<T extends AbstractFilter> extends CompoundFilter<T> {

    public IntersectionFilter(ObservableList< T> subFilters) {
        super(subFilters);
    }

    public IntersectionFilter() {
        super(FXCollections.<T>observableArrayList());
    }

    @Override
    public Boolean accept(DrawableFile df) {
        if (isActive()) {
            for (T f : subFilters) {
                if (f.isActive() && f.accept(df) == false) {
                    return false;
                }
            }
            return true;
        } else {
            return true;
        }
    }
}
