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
 * Created on Dec 3, 2005
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.*;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.html2.HTMLCollection;
import org.w3c.dom.html2.HTMLElement;
import org.w3c.dom.html2.HTMLTableCaptionElement;
import org.w3c.dom.html2.HTMLTableElement;
import org.w3c.dom.html2.HTMLTableSectionElement;
import java.util.*;

public class HTMLTableElementImpl extends HTMLAbstractUIElement implements
		HTMLTableElement {
	
	public HTMLTableElementImpl() {
		super("TABLE");
	}

	public HTMLTableElementImpl(String name) {
		super(name);
	}

	private HTMLTableCaptionElement caption;
	
	public HTMLTableCaptionElement getCaption() {
		return this.caption;
	}

	public void setCaption(HTMLTableCaptionElement caption) throws DOMException {
		this.caption = caption;
	}

	private HTMLTableSectionElement thead;
	
	public HTMLTableSectionElement getTHead() {
		return this.thead;
	}

	public void setTHead(HTMLTableSectionElement tHead) throws DOMException {
		this.thead = tHead;
	}

	private HTMLTableSectionElement tfoot;
	
	public HTMLTableSectionElement getTFoot() {
		return this.tfoot;
	}

	public void setTFoot(HTMLTableSectionElement tFoot) throws DOMException {
		this.tfoot = tFoot;
	}

	public HTMLCollection getRows() {
		return new DescendentHTMLCollection(this, new ElementFilter("TR"), this.treeLock, false);
	}

	public HTMLCollection getTBodies() {
		return new DescendentHTMLCollection(this, new ElementFilter("TBODY"), this.treeLock, false);
	}

	public String getAlign() {
		return this.getAttribute("align");
	}

	public void setAlign(String align) {
		this.setAttribute("align", align);
	}

	public String getBgColor() {
		return this.getAttribute("bgcolor");
	}

	public void setBgColor(String bgColor) {
		this.setAttribute("bgcolor", bgColor);
	}

	public String getBorder() {
		return this.getAttribute("border");
	}

	public void setBorder(String border) {
		this.setAttribute("border", border);
	}

	public String getCellPadding() {
		return this.getAttribute("cellpadding");
	}

	public void setCellPadding(String cellPadding) {
		this.setAttribute("cellpadding", cellPadding);
	}

	public String getCellSpacing() {
		return this.getAttribute("cellspacing");
	}

	public void setCellSpacing(String cellSpacing) {
		this.setAttribute("cellspacing", cellSpacing);
	}

	public String getFrame() {
		return this.getAttribute("frame");
	}

	public void setFrame(String frame) {
		this.setAttribute("frame", frame);
	}

	public String getRules() {
		return this.getAttribute("rules");
	}

	public void setRules(String rules) {
		this.setAttribute("rules", rules);
	}

	public String getSummary() {
		return this.getAttribute("summary");
	}

	public void setSummary(String summary) {
		this.setAttribute("summary", summary);
	}

	public String getWidth() {
		return this.getAttribute("width");
	}

	public void setWidth(String width) {
		this.setAttribute("width", width);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#getHeightLength()
	 */
	public HtmlLength getHeightLength(int availHeight) {
		try {
			AbstractCSS2Properties props = this.getCurrentStyle();
			String heightText = props == null ? null : props.getHeight();
			if(heightText == null) {
				return new HtmlLength(this.getAttribute("height"));
			} 
			else {
				return new HtmlLength(HtmlValues.getPixelSize(heightText, this.getRenderState(), 0, availHeight));
			}
		} catch(Exception err) {
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContext#getWidthLength()
	 */
	public HtmlLength getWidthLength(int availWidth) {
		try {
			AbstractCSS2Properties props = this.getCurrentStyle();
			String widthText = props == null ? null : props.getWidth();
			if(widthText == null) {
				return new HtmlLength(this.getAttribute("width"));
			} 
			else {
				return new HtmlLength(HtmlValues.getPixelSize(widthText, this.getRenderState(), 0, availWidth));
			}
		} catch(Exception err) {
			return null;
		}
	}

	public HTMLElement createTHead() {
		org.w3c.dom.Document doc = this.document;
		return doc == null ? null : (HTMLElement) doc.createElement("thead");
	}

	public void deleteTHead() {
		this.removeChildren(new ElementFilter("THEAD"));
	}

	public HTMLElement createTFoot() {
		org.w3c.dom.Document doc = this.document;
		return doc == null ? null : (HTMLElement) doc.createElement("tfoot");
	}

	public void deleteTFoot() {
		this.removeChildren(new ElementFilter("TFOOT"));
	}

	public HTMLElement createCaption() {
		org.w3c.dom.Document doc = this.document;
		return doc == null ? null : (HTMLElement) doc.createElement("caption");
	}

	public void deleteCaption() {
		this.removeChildren(new ElementFilter("CAPTION"));
	}

	/**
	 * Inserts a row at the index given. If <code>index</code> is <code>-1</code>,
	 * the row is appended as the last row.
	 */
	public HTMLElement insertRow(int index) throws DOMException {
		org.w3c.dom.Document doc = this.document;
		if(doc == null) {
			throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Orphan element");
		}
		HTMLElement rowElement = (HTMLElement) doc.createElement("TR");
		synchronized(this.treeLock) {
			if(index == -1) {
				this.appendChild(rowElement);
				return rowElement;
			}
			ArrayList nl = this.nodeList;
			if(nl != null) {
				int size = nl.size();
				int trcount = 0;
				for(int i = 0; i < size; i++) {
					Node node = (Node) nl.get(i);
					if("TR".equalsIgnoreCase(node.getNodeName())) {
						if(trcount == index) {
							this.insertAt(rowElement, i);
							return rowElement;
						}
						trcount++;
					}
				}
			}
			else {
				this.appendChild(rowElement);
				return rowElement;
			}
		}
		throw new DOMException(DOMException.INDEX_SIZE_ERR, "Index out of range");
	}

	public void deleteRow(int index) throws DOMException {
		synchronized(this.treeLock) {
			ArrayList nl = this.nodeList;
			if(nl != null) {
				int size = nl.size();
				int trcount = 0;
				for(int i = 0; i < size; i++) {
					Node node = (Node) nl.get(i);
					if("TR".equalsIgnoreCase(node.getNodeName())) {
						if(trcount == index) {
							this.removeChildAt(i);
							return;
						}
						trcount++;
					}
				}
			}
		}
		throw new DOMException(DOMException.INDEX_SIZE_ERR, "Index out of range");
	}

	protected RenderState createRenderState(RenderState prevRenderState) {
		return new TableRenderState(prevRenderState, this);
	}
}
