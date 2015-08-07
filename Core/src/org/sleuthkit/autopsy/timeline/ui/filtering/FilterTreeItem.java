package org.sleuthkit.autopsy.timeline.ui.filtering;

import javafx.beans.Observable;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.timeline.filters.CompoundFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;

/**
 * A TreeItem for a filter.
 */
public class FilterTreeItem extends TreeItem<Filter> {

    /**
     * recursively construct a tree of treeitems to parallel the filter tree of
     * the given filter
     *
     *
     * @param f the filter for this item. if f has sub-filters, tree items will
     *          be made for them added added to the children of this
     *          FilterTreeItem
     */
    public FilterTreeItem(Filter f, ObservableMap<String, Boolean> expansionMap) {
        super(f);

        expansionMap.addListener((MapChangeListener.Change<? extends String, ? extends Boolean> change) -> {
            if (change.getKey() == f.getDisplayName()) {
                setExpanded(expansionMap.get(change.getKey()));
            }
        });

        if (expansionMap.get(f.getDisplayName()) != null) {
            setExpanded(expansionMap.get(f.getDisplayName()));
        }

        expandedProperty().addListener((Observable observable) -> {
            expansionMap.put(f.getDisplayName(), isExpanded());
        });

        if (f instanceof CompoundFilter) {
            CompoundFilter cf = (CompoundFilter) f;

            for (Filter af : cf.getSubFilters()) {
                getChildren().add(new FilterTreeItem(af, expansionMap));
            }
        }
    }
}
