/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;

/**
 *
 * @author millm
 */
public interface FilterModel<FilterType extends TimelineFilter> {

    BooleanBinding activeProperty();

    DefaultFilterModel<FilterType> copyOf();

    ObservableBooleanValue disabledProperty();

    String getDisplayName();

    FilterType getFilter();

    String getSQLWhere(TimelineManager tm);

    boolean isActive();

    boolean isDisabled();

    boolean isSelected();

    SimpleBooleanProperty selectedProperty();

    void setDisabled(Boolean act);

    void setSelected(Boolean act);

}
