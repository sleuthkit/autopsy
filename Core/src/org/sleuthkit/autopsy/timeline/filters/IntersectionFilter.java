/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.openide.util.NbBundle;

/** Intersection(And) filter */
public class IntersectionFilter extends CompoundFilter {

    public IntersectionFilter(ObservableList<Filter> subFilters) {
        super(subFilters);
    }

    public IntersectionFilter() {
        super(FXCollections.<Filter>observableArrayList());
    }

    @Override
    public IntersectionFilter copyOf() {
        IntersectionFilter filter = new IntersectionFilter(FXCollections.observableArrayList(
                this.getSubFilters().stream()
                .map(Filter::copyOf)
                .collect(Collectors.toList())));
        filter.setActive(isActive());
        filter.setDisabled(isDisabled());
        return filter;
    }

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(this.getClass(),
                                   "IntersectionFilter.displayName.text",
                                   getSubFilters().stream()
                                                  .map(Filter::getDisplayName)
                                                  .collect(Collectors.joining(",", "[", "]")));
    }

    @Override
    public String getHTMLReportString() {
        return getSubFilters().stream().filter(Filter::isActive).map(Filter::getHTMLReportString).collect(Collectors.joining("</li><li>", "<ul><li>", "</li></ul>")); // NON-NLS
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IntersectionFilter other = (IntersectionFilter) obj;

        if (isActive() != other.isActive()) {
            return false;
        }

        for (int i = 0; i < getSubFilters().size(); i++) {
            if (getSubFilters().get(i).equals(other.getSubFilters().get(i)) == false) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        return hash;
    }
}
