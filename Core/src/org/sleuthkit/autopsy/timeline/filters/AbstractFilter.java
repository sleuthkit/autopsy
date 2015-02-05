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

    private final SimpleBooleanProperty active = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty disabled = new SimpleBooleanProperty(false);

    @Override
    public SimpleBooleanProperty getActiveProperty() {
        return active;
    }

    @Override
    public SimpleBooleanProperty getDisabledProperty() {
        return disabled;
    }

    @Override
    public void setActive(Boolean act) {
        active.set(act);
    }

    @Override
    public boolean isActive() {
        return active.get();
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
        return "[" + (isActive() ? "x" : " ") + "]"; // NON-NLS
    }

  

}
