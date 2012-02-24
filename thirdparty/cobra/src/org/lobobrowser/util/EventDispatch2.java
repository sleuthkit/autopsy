/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
*/
/*
 * Created on Mar 19, 2005
 */
package org.lobobrowser.util;

import java.util.*;

/**
 * @author J. H. S.
 */
public abstract class EventDispatch2 {
    private Collection listeners;
    private static final EventListener[] EMPTY_ARRAY = new EventListener[0];
    
    public EventDispatch2() {
    }
    
    public Collection createListenerCollection() {
        return new ArrayList();
    }
    
    public final void addListener(EventListener listener) {
        synchronized(this) {
            if(this.listeners == null) {
                this.listeners = this.createListenerCollection();
            }
            this.listeners.add(listener);
        }
    }
    
    public final void removeListener(EventListener listener) {
        synchronized(this) {
            if(this.listeners != null) {
                this.listeners.remove(listener);
            }
        }
    }
    
    public final boolean fireEvent(EventObject event) {
        EventListener[] larray;
        synchronized(this) {
        	Collection listeners = this.listeners;
            if(listeners == null || listeners.size() == 0) {
            	return false;
            }
            larray = (EventListener[]) this.listeners.toArray(EMPTY_ARRAY);
        }
        int length = larray.length;
        for(int i = 0; i < length; i++) {
        	// Call holding no locks
        	this.dispatchEvent(larray[i], event);
        }
        return true;
    }
    
    protected abstract void dispatchEvent(EventListener listener, EventObject event);
}
