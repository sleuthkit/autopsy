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
 * Created on Oct 23, 2005
 */
package org.lobobrowser.html.domimpl;

import java.net.MalformedURLException;

import org.lobobrowser.html.style.*;

/**
 * A generic node interface. The idea is that implementors could
 * be W3C nodes or not.
 */
public interface ModelNode {
	//There shouldn't be any references to GUI components here.
	//Events are processed by controller in renderer package.
	
	public java.net.URL getFullURL(String spec) throws MalformedURLException;
	public void warn(String message, Throwable err);
	public boolean isEqualOrDescendentOf(ModelNode otherNode);
	public ModelNode getParentModelNode();
	public RenderState getRenderState();
	
	/**
	 * Sets a document item. A radio button, for example,
	 * can use this to set button group state.
	 * @param name
	 * @param value
	 */
	public void setDocumentItem(String name, Object value);
	public Object getDocumentItem(String name);	
}
