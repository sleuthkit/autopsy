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
public class EventDispatch {
    private Collection listeners;
    
    public EventDispatch() {
    }
    
    public Collection createListenerCollection() {
        return new LinkedList();
    }
    
    public final void addListener(GenericEventListener listener) {
        synchronized(this) {
            if(this.listeners == null) {
                this.listeners = this.createListenerCollection();
            }
            this.listeners.add(listener);
        }
    }
    
    public final void removeListener(GenericEventListener listener) {
        synchronized(this) {
            if(this.listeners != null) {
                this.listeners.remove(listener);
            }
        }
    }
    
    public final void fireEvent(EventObject event) {
        GenericEventListener[] larray = null;
        synchronized(this) {
            if(this.listeners != null) {
                larray = (GenericEventListener[]) this.listeners.toArray(GenericEventListener.EMPTY_ARRAY);
            }
        }
        if(larray != null) {
            for(int i = 0; i < larray.length; i++) {
                // Call holding no locks
                larray[i].processEvent(event);
            }
        }
    }
}
