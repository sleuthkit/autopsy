/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.events;

import java.beans.PropertyChangeEvent;

/**
 * This is a place holder class to be overwritten by the version in the
 * Collaborative branch.
 */
abstract class AutopsyEvent extends PropertyChangeEvent {

    AutopsyEvent(Object source, String propertyName, Object oldValue, Object newValue) {
        super(source, propertyName, oldValue, newValue);
    }
}
