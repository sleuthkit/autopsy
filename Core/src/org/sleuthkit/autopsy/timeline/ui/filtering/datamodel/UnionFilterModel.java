/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import java.util.stream.Collectors;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;
import org.sleuthkit.datamodel.timeline.filters.UnionFilter;

public class UnionFilterModel<SubFilterType extends TimelineFilter> extends CompoundFilterModel<SubFilterType> {

    public UnionFilterModel(UnionFilter<SubFilterType> delegate) {
        super(delegate);
    }

    @Override
    public String getSQLWhere(TimelineManager manager) {
        String join = this.getSubFilterModels().stream()
                .filter(FilterModel::isActive)
                .map(filter -> filter.getSQLWhere(manager))
                .collect(Collectors.joining(" OR "));

        return join.isEmpty()
                ? manager.getTrueLiteral()
                : "(" + join + ")";
    }
}
