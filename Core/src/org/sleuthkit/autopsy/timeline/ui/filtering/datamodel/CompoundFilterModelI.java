/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import javafx.collections.ObservableList;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

/**
 *
 */
public interface CompoundFilterModelI<SubFilterType extends TimelineFilter> {

    ObservableList<FilterModel<TimelineFilter>> getSubFilterModels();

}
