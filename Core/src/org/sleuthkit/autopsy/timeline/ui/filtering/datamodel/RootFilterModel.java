/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

import org.sleuthkit.datamodel.timeline.filters.CompoundFilter;
import org.sleuthkit.datamodel.timeline.filters.DataSourceFilter;
import org.sleuthkit.datamodel.timeline.filters.HashSetFilter;
import org.sleuthkit.datamodel.timeline.filters.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.filters.RootFilter;
import org.sleuthkit.datamodel.timeline.filters.TagNameFilter;
import org.sleuthkit.datamodel.timeline.filters.TagsFilter;
import org.sleuthkit.datamodel.timeline.filters.TextFilter;
import org.sleuthkit.datamodel.timeline.filters.TimelineFilter;
import org.sleuthkit.datamodel.timeline.filters.TypeFilter;

/**
 *
 */
public class RootFilterModel extends DefaultFilterModel<RootFilter> {

    private final CompoundFilterModel<TypeFilter> typeFilterModel;
    private final DefaultFilterModel<HideKnownFilter> knownFilterModel;
    private final DefaultFilterModel<TextFilter> textFilterModel;
    private final CompoundFilterModel<TagNameFilter> tagsFilterModel;
    private final CompoundFilterModel<HashSetFilter> hashHitsFilterModel;
    private final CompoundFilterModel<DataSourceFilter> dataSourcesFilterModel;

    public RootFilterModel(RootFilter delegate) {
        this(delegate,
                new CompoundFilterModel<>(delegate.getTypeFilter()),
                new DefaultFilterModel<>(delegate.getKnownFilter()),
                new DefaultFilterModel<>(delegate.getTextFilter()),
                new CompoundFilterModel<>(delegate.getTagsFilter()),
                new CompoundFilterModel<>(delegate.getHashHitsFilter()),
                new CompoundFilterModel<>(delegate.getDataSourcesFilter())
        );
    }

    private RootFilterModel(RootFilter delegate,
                            CompoundFilterModel<TypeFilter> typeFilterModel,
                            DefaultFilterModel<HideKnownFilter> knownFilterModel,
                            DefaultFilterModel<TextFilter> textFilterModel,
                            CompoundFilterModel<TagNameFilter> tagsFilterModel,
                            CompoundFilterModel<HashSetFilter> hashHitsFilterModel,
                            CompoundFilterModel<DataSourceFilter> dataSourcesFilterModel) {
        super(delegate);
        this.typeFilterModel = typeFilterModel;
        this.knownFilterModel = knownFilterModel;
        this.textFilterModel = textFilterModel;
        this.tagsFilterModel = tagsFilterModel;
        this.hashHitsFilterModel = hashHitsFilterModel;
        this.dataSourcesFilterModel = dataSourcesFilterModel;
    }

    @Override
    public RootFilterModel copyOf() {
        return new RootFilterModel(getFilter().copyOf(),
                getTypeFilterModel().copyOf(),
                getKnownFilterModel().copyOf(),
                getTextFilterModel().copyOf(),
                getTagsFilterModel().copyOf(),
                getHashHitsFilterModel().copyOf(),
                getDataSourcesFilterModel().copyOf());
    }

    public CompoundFilterModel<TypeFilter> getTypeFilterModel() {
        return typeFilterModel;
    }

    public DefaultFilterModel<HideKnownFilter> getKnownFilterModel() {
        return knownFilterModel;
    }

    public DefaultFilterModel<TextFilter> getTextFilterModel() {
        return textFilterModel;
    }

    public CompoundFilterModel<TagNameFilter> getTagsFilterModel() {
        return tagsFilterModel;
    }

    public CompoundFilterModel<HashSetFilter> getHashHitsFilterModel() {
        return hashHitsFilterModel;
    }

    public CompoundFilterModel<DataSourceFilter> getDataSourcesFilterModel() {
        return dataSourcesFilterModel;
    }

    public RootFilter getActiveSubFiltersRecursive() {

        TagsFilter newTagsFilter = new TagsFilter();
        CompoundFilter x = tagsFilterModel.getFilter().getClass().newInstance();
        tagsFilterModel.getSubFilterModels().filtered(FilterModel::isActive).forEach(newTagsFilter::addSubFilter);

        return new RootFilter(activeOrNull(knownFilterModel),
                activeOrNull(hashHitsFilterModel),
                activeOrNull(textFilterModel),
                activeOrNull(tagsFilterModel),
                activeOrNull(typeFilterModel),
                activeOrNull(dataSourcesFilterModel), null);
    }

    private <X extends TimelineFilter> X activeOrNull(FilterModel<? extends X> filterModel) {
        if (filterModel.isActive()) {
            if (filterModel instanceof CompoundFilterModel) {

                CompoundFilterModel<?> compFilterModel = (CompoundFilterModel<?>) filterModel;
                compFilterModel.copyOf();

            } else {
                return filterModel.getFilter();
            }
        } else {
            return null;
        }
    }

    public boolean hasActiveHashFilters() {
        return hashHitsFilterModel.isActive()
               && hashHitsFilterModel.getSubFilterModels().stream().anyMatch(FilterModel::isActive);
    }

    public boolean hasActiveTagsFilters() {
        return tagsFilterModel.isActive()
               && tagsFilterModel.getSubFilterModels().stream().anyMatch(FilterModel::isActive);
    }

}
