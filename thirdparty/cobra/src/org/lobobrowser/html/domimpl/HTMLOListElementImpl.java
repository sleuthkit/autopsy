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
import org.w3c.dom.html2.HTMLOListElement;

public class HTMLOListElementImpl extends HTMLAbstractUIElement implements
		HTMLOListElement {
	public HTMLOListElementImpl(String name) {
		super(name);
	}

	public boolean getCompact() {
		String compactText = this.getAttribute("compact");
		return "compact".equalsIgnoreCase(compactText);
	}

	public void setCompact(boolean compact) {
		this.setAttribute("compact", compact ? "compact" : null);
	}

	public int getStart() {
		String startText = this.getAttribute("start");
		if(startText == null) {
			return 1;
		}
		try {
			return Integer.parseInt(startText);
		} catch(NumberFormatException nfe) {
			return 1;
		}
	}

	public void setStart(int start) {
		this.setAttribute("start", String.valueOf(start));
	}

	public String getType() {
		return this.getAttribute("type");
	}

	public void setType(String type) {
		this.setAttribute("type", type);
	}

	protected RenderState createRenderState(RenderState prevRenderState) {
		return new ListRenderState(prevRenderState, this);
	}
}
