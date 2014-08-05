/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.imageanalyzer.filtering.filters;

import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;

/**
 *
 * @author jonathan
 */
public abstract class AbstractFilter<R> {

    public abstract void clear();
    public static final AtomicFilter.FilterComparison EQUALS = new AtomicFilter.EQUALS();
    public static final AtomicFilter.FilterComparison EQUALS_IGNORECASE = new AtomicFilter.EQUALS_IGNORECASE();
    public static final AtomicFilter.FilterComparison NOT_EQUALS = new AtomicFilter.NOT_EQUALS();
    public static final AtomicFilter.FilterComparison SUBSTRING = new AtomicFilter.SUBSTRING();
    public static final AtomicFilter.FilterComparison CONTAINED_IN = new AtomicFilter.CONTAINS();
    public final SimpleBooleanProperty active = new SimpleBooleanProperty(true);

    public abstract String getDisplayName();

    abstract public Boolean accept(DrawableFile df);

    public boolean isActive() {
        return active.get();
    }

    final protected static class CONTAINS extends AtomicFilter.FilterComparison<List<String>, String> {

        @Override
        public String getSqlOperator() {
            return " in ";
        }

        @Override
        public Boolean compare(List<String> attrVal, String filterVal) {
            return attrVal.contains(filterVal);
        }
    }

    final protected static class SUBSTRING extends AtomicFilter.FilterComparison<String, String> {

        @Override
        public Boolean compare(String attrVal, String filterVal) {
            return attrVal.toLowerCase().contains(filterVal.toLowerCase());
        }

        @Override
        public String getSqlOperator() {
            return " like ";
        }

        @Override
        public String toString() {
            return "in";
        }
    }

    final protected static class EQUALS_IGNORECASE extends AtomicFilter.FilterComparison<String, String> {

        @Override
        public Boolean compare(String attrVal, String filterVal) {
            return attrVal.equals(filterVal);
        }

        @Override
        public String getSqlOperator() {
            return " == ";
        }

        @Override
        public String toString() {
            return "=";
        }
    }

    final protected static class EQUALS<T> extends AtomicFilter.FilterComparison<T, T> {

        @Override
        public Boolean compare(T attrVal, T filterVal) {
            return attrVal.equals(filterVal);
        }

        @Override
        public String getSqlOperator() {
            return " == ";
        }

        @Override
        public String toString() {
            return "=";
        }
    }

    final protected static class NOT_EQUALS<T> extends AtomicFilter.FilterComparison<T, T> {

        @Override
        public Boolean compare(T attrVal, T filterVal) {
            return attrVal != filterVal;
        }

        @Override
        public String getSqlOperator() {
            return " != ";
        }

        @Override
        public String toString() {
            return "≠";
        }
    }

    /**
     *
     *
     */
    public static abstract class FilterComparison<A, F> {

        private FilterComparison() {
        }

        abstract public Boolean compare(A attrVal, F filterVal);

        abstract public String getSqlOperator();
    }
}
