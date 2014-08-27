/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * Union(or) filter
 */
abstract public class UnionFilter extends CompoundFilter{

    public UnionFilter(ObservableList<Filter> subFilters) {
        super(subFilters);
    }

    public UnionFilter() {
        super(FXCollections.<Filter>observableArrayList());
    }
    
   
}
