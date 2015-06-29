/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.events;

import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
abstract public class TagEvent<T extends Tag> extends AutopsyEvent {

    abstract Long getTagID();

    abstract T getTagByID(long id) throws IllegalStateException, TskCoreException;

    public TagEvent(Object source, String propertyName, T oldValue, T newValue) {
        super(propertyName, oldValue, newValue);
    }

    /**
     * get the Tag that this event is for
     *
     * @return the Tag
     */
    public abstract T getTag();

}
