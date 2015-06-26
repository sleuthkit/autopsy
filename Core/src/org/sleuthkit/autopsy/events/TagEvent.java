/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.events;

import org.sleuthkit.datamodel.Tag;

/**
 *
 */
abstract public class TagEvent<T extends Tag> extends AutopsyEvent {

    public TagEvent(Object source, String propertyName, Object oldValue, Object newValue) {
        super(source, propertyName, oldValue, newValue);
    }

    /**
     * get the Tag that this event is for
     *
     * @return the Tag
     */
    public abstract T getTag();

}
