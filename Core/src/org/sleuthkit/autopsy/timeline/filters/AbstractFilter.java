/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.filters;

import javafx.beans.property.SimpleBooleanProperty;

/**
 * Base implementation of a {@link Filter}. Implements active property.
 *
 */
public abstract class AbstractFilter implements Filter {

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);

    @Override
    public SimpleBooleanProperty getActiveProperty() {
        return selected;
    }

    @Override
    public SimpleBooleanProperty getDisabledProperty() {
        return disabled;
    }

    @Override
    public void setSelected(Boolean act) {
        selected.set(act);
    }

    @Override
    public boolean isSelected() {
        return selected.get();
    }

    @Override
    public void setDisabled(Boolean act) {
        disabled.set(act);
    }

    @Override
    public boolean isDisabled() {
        return disabled.get();
    }

    @Override
    public String getStringCheckBox() {
        return "[" + (isSelected() ? "x" : " ") + "]"; // NON-NLS
    }

  

}
