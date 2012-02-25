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
 * Created on Jan 28, 2006
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.BrowserFrame;
import org.lobobrowser.html.js.*;
import org.w3c.dom.Document;
import org.w3c.dom.html2.HTMLFrameElement;

public class HTMLFrameElementImpl extends HTMLElementImpl implements
		HTMLFrameElement, FrameNode {
	private volatile BrowserFrame browserFrame;
	
	public HTMLFrameElementImpl(String name, boolean noStyleSheet) {
		super(name, noStyleSheet);
	}

	public HTMLFrameElementImpl(String name) {
		super(name);
	}

	public void setBrowserFrame(BrowserFrame frame) {
		this.browserFrame = frame;
	}
	
	public BrowserFrame getBrowserFrame() {
		return this.browserFrame;
	}

	public String getFrameBorder() {
		return this.getAttribute("frameBorder");
	}

	public void setFrameBorder(String frameBorder) {
		this.setAttribute("frameBorder", frameBorder);
	}

	public String getLongDesc() {
		return this.getAttribute("longdesc");
	}

	public void setLongDesc(String longDesc) {
		this.setAttribute("longdesc", longDesc);
	}

	public String getMarginHeight() {
		return this.getAttribute("marginHeight");
	}

	public void setMarginHeight(String marginHeight) {
		this.setAttribute("marginHeight", marginHeight);
	}

	public String getMarginWidth() {
		return this.getAttribute("marginWidth");
	}

	public void setMarginWidth(String marginWidth) {
		this.setAttribute("marginWidth", marginWidth);
	}

	public String getName() {
		return this.getAttribute("name");
	}

	public void setName(String name) {
		this.setAttribute("name", name);
	}

	private boolean noResize;
	
	public boolean getNoResize() {
		return this.noResize;
	}

	public void setNoResize(boolean noResize) {
		this.noResize = noResize;
	}

	public String getScrolling() {
		return this.getAttribute("scrolling");
	}

	public void setScrolling(String scrolling) {
		this.setAttribute("scrolling", scrolling);
	}

	public String getSrc() {
		return this.getAttribute("src");
	}

	public void setSrc(String src) {
		this.setAttribute("src", src);
	}

	public Document getContentDocument() {
		BrowserFrame frame = this.browserFrame;
		if(frame == null) {
			// Not loaded yet
			return null;
		}
		return frame.getContentDocument();
	}
	
	public Window getContentWindow() {
		BrowserFrame frame = this.browserFrame;
		if(frame == null) {
			// Not loaded yet
			return null;
		}
		return Window.getWindow(frame.getHtmlRendererContext());
	}

}
