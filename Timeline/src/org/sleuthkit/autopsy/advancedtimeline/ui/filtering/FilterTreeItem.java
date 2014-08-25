package org.sleuthkit.autopsy.advancedtimeline.ui.filtering;

import javafx.scene.control.TreeItem;
import org.sleuthkit.autopsy.advancedtimeline.filters.CompoundFilter;
import org.sleuthkit.autopsy.advancedtimeline.filters.Filter;

/** A TreeItem for a filter. */
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
    public FilterTreeItem(Filter f) {
        super(f);
        setExpanded(true);

        if (f instanceof CompoundFilter) {
            CompoundFilter cf = (CompoundFilter) f;

            for (Filter af : cf.getSubFilters()) {
                getChildren().add(new FilterTreeItem(af));
            }
        }
    }
}
