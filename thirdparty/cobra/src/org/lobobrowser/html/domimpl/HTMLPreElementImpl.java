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
 * Created on Feb 12, 2006
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.*;
import org.w3c.dom.html2.HTMLPreElement;

public class HTMLPreElementImpl extends HTMLAbstractUIElement implements
		HTMLPreElement {
	public HTMLPreElementImpl(String name) {
		super(name);
	}

	public int getWidth() {
		String widthText = this.getAttribute("width");
		if(widthText == null) {
			return 0;
		}
		try {
			return Integer.parseInt(widthText);
		} catch(NumberFormatException nfe) {
			return 0;
		}
	}

	public void setWidth(int width) {
		this.setAttribute("width", String.valueOf(width));
	}

	protected RenderState createRenderState(RenderState prevRenderState) {
		return new PreRenderState(prevRenderState, this);
	}
}
