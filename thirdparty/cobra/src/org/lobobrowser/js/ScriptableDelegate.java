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
package org.lobobrowser.js;

import org.mozilla.javascript.*;

/**
 * Java classes used in Javascript should implement this
 * interface. While all classes can be mapped to
 * JavaScript, implementing this interface ensures that
 * the Java object proxy is not garbage collected as long
 * as the Java object is not garbage collected.
 */
public interface ScriptableDelegate {
	public void setScriptable(Scriptable scriptable);
	public Scriptable getScriptable();
}	
