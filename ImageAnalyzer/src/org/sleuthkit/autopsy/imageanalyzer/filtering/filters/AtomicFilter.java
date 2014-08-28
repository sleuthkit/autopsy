/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.coreutils.FXMLConstructor;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import java.net.URL;
import java.util.Comparator;
import java.util.Objects;
import java.util.ResourceBundle;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;

/**
 *
 */
public class AtomicFilter<A, F> extends AbstractFilter {

    private DrawableAttribute<A> filterAttribute;

    public DrawableAttribute<A> getFilterAttribute() {
        return filterAttribute;
    }

    public F getFilterValue() {
        return filterValue.get();
    }

    public FilterComparison<A, F> getFilterComparisson() {
        return filterComparisson;
    }
    public SimpleObjectProperty<F> filterValue;
    private FilterComparison<A, F> filterComparisson;

    public AtomicFilter(DrawableAttribute filterAttribute, FilterComparison<A, F> filterComparisson, F filterValue) {
        this.filterAttribute = filterAttribute;
        this.filterValue = new SimpleObjectProperty<>(filterValue);
        this.filterComparisson = filterComparisson;
    }

    @Override
    public Boolean accept(DrawableFile df) {
//        Logger.getAnonymousLogger().log(Level.INFO, getDisplayName() + " : " + filterValue + " filtered " + df.getName() + " = " + compare);
        return isActive()
                ? filterComparisson.compare((A) df.getValueOfAttribute(filterAttribute), filterValue.get())
                : false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.filterAttribute);
        hash = 79 * hash + Objects.hashCode(this.filterValue);
        hash = 79 * hash + Objects.hashCode(this.filterComparisson);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AtomicFilter other = (AtomicFilter) obj;
        if (this.filterAttribute != other.filterAttribute) {
            return false;
        }
        if (!Objects.equals(this.filterValue, other.filterValue)) {
            return false;
        }
        if (!Objects.equals(this.filterComparisson, other.filterComparisson)) {
            return false;
        }
        return true;
    }

    @Override
    public String getDisplayName() {
        return filterValue.get().toString();
    }

    public FilterRow getUI() {
        return new AtomicFilterRow(this);
    }

    @Override
    public void clear() {
        active.set(true);
    }
    public static final Comparator<AtomicFilter> ALPHABETIC_COMPARATOR = new Comparator<AtomicFilter>() {
        @Override
        public int compare(AtomicFilter o1, AtomicFilter o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }
    };

    /**
     *
     * @author jonathan
     */
    public static class AtomicFilterRow<A, F> extends FilterRow<AtomicFilter<A, F>> {

        @FXML
        protected ResourceBundle resources;
        @FXML
        protected URL location;
        @FXML
        protected ChoiceBox comparisonBox;
        @FXML
        protected Label filterLabel;
        @FXML
        protected CheckBox selectedBox;
        protected final AtomicFilter<A, F> filter;

        private AtomicFilterRow(AtomicFilter filter) {
            super();
            this.filter = filter;
            FXMLConstructor.construct(this, "FilterRow.fxml");
        }

        public ReadOnlyBooleanProperty getSelectedProperty() {
            return selectedBox.selectedProperty();
        }

        public ReadOnlyObjectProperty<AtomicFilter.FilterComparison> getComparisonProperty() {
            return comparisonBox.getSelectionModel().selectedItemProperty();
        }

        @FXML
        void initialize() {
            assert comparisonBox != null : "fx:id=\"comparisonBox\" was not injected: check your FXML file 'FilterRow.fxml'.";
            assert filterLabel != null : "fx:id=\"filterLabel\" was not injected: check your FXML file 'FilterRow.fxml'.";
            assert selectedBox != null : "fx:id=\"selectedBox\" was not injected: check your FXML file 'FilterRow.fxml'.";
            comparisonBox.getItems().setAll(AtomicFilter.EQUALS, AtomicFilter.EQUALS_IGNORECASE);
            switch (filter.filterAttribute.attrName) {
                case MAKE:
                case MODEL:
                    comparisonBox.getSelectionModel().select(AtomicFilter.EQUALS_IGNORECASE);
                    break;
                default:
                    comparisonBox.getSelectionModel().select(AtomicFilter.EQUALS);
            }
            final F filterValue = filter.getFilterValue();

            if (filterValue == null || "".equals(filterValue)) {
                filterLabel.setText("unknown");
            } else {
                filterLabel.setText(filterValue.toString());
            }
            selectedBox.selectedProperty().bindBidirectional(filter.active);
        }

        @Override
        public AtomicFilter<A, F> getFilter() {
            return filter;
        }
    }
}
