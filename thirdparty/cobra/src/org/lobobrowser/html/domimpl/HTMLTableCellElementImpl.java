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
 * Created on Dec 4, 2005
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.*;
import org.w3c.dom.html2.HTMLTableCellElement;

public class HTMLTableCellElementImpl extends HTMLAbstractUIElement implements
		HTMLTableCellElement {
	public HTMLTableCellElementImpl(String name) {
		super(name);
	}

	public int getCellIndex() {
		// TODO Cell index in row
		return 0;
	}

	public String getAbbr() {
		return this.getAttribute("abbr");
	}

	public void setAbbr(String abbr) {
		this.setAttribute("abbr", abbr);
	}

	public String getAlign() {
		return this.getAttribute("align");
	}

	public void setAlign(String align) {
		this.setAttribute("align", align);
	}

	public String getAxis() {
		return this.getAttribute("axis");
	}

	public void setAxis(String axis) {
		this.setAttribute("axis", axis);
	}

	public String getBgColor() {
		return this.getAttribute("bgcolor");
	}

	public void setBgColor(String bgColor) {
		this.setAttribute("bgcolor", bgColor);
	}

	public String getCh() {
		return this.getAttribute("ch");
	}

	public void setCh(String ch) {
		this.setAttribute("ch", ch);
	}

	public String getChOff() {
		return this.getAttribute("choff");
	}

	public void setChOff(String chOff) {
		this.setAttribute("choff", chOff);
	}

	public int getColSpan() {
		String colSpanText = this.getAttribute("colspan");
		if(colSpanText == null) {
			return 1;
		}
		else {
			try {
				return Integer.parseInt(colSpanText);
			} catch(NumberFormatException nfe) {
				return 1;
			}
		}
	}

	public void setColSpan(int colSpan) {
		this.setAttribute("colspan", String.valueOf(colSpan));
	}

	public String getHeaders() {
		return this.getAttribute("headers");
	}

	public void setHeaders(String headers) {
		this.setAttribute("headers", headers);
	}

	public String getHeight() {
		return this.getAttribute("height");
	}

	public void setHeight(String height) {
		this.setAttribute("height", height);
	}
	
	public boolean getNoWrap() {
		return "nowrap".equalsIgnoreCase(this.getAttribute("nowrap"));
	}

	public void setNoWrap(boolean noWrap) {
		this.setAttribute("nowrap", noWrap ? "nowrap" : null);
	}

	public int getRowSpan() {
		String rowSpanText = this.getAttribute("rowspan");
		if(rowSpanText == null) {
			return 1;
		}
		else {
			try {
				return Integer.parseInt(rowSpanText);
			} catch(NumberFormatException nfe) {
				return 1;
			}
		}
	}

	public void setRowSpan(int rowSpan) {
		this.setAttribute("rowspan", String.valueOf(rowSpan));
	}

	public String getScope() {
		return this.getAttribute("scope");
	}

	public void setScope(String scope) {
		this.setAttribute("scope", scope);
	}

	public String getVAlign() {
		return this.getAttribute("valign");
	}

	public void setVAlign(String vAlign) {
		this.setAttribute("valign", vAlign);
	}

	public String getWidth() {
		return this.getAttribute("width");
	}

	public void setWidth(String width) {
		this.setAttribute("width", width);
	}

	protected RenderState createRenderState(RenderState prevRenderState) {
		return new TableCellRenderState(prevRenderState, this);
	}
}
