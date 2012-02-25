/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The XAMJ Project

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
package org.lobobrowser.html;

import java.awt.event.MouseEvent;
import java.net.URL;

import org.w3c.dom.html2.HTMLCollection;
import org.w3c.dom.html2.HTMLElement;
import org.w3c.dom.html2.HTMLLinkElement;

/**
 * Abstract implementation of the {@link HtmlRendererContext} interface with
 * blank methods, provided for developer convenience.
 */
public abstract class AbstractHtmlRendererContext implements HtmlRendererContext {
	
	public void alert(String message) {
	}

	public void back() {
	}

	public void blur() {
	}

	public void close() {
	}

	public boolean confirm(String message) {
		return false;
	}

	public BrowserFrame createBrowserFrame() {
		return null;
	}

	public void focus() {
	}

	public String getDefaultStatus() {
		return null;
	}

	public HTMLCollection getFrames() {
		return null;
	}

	public HtmlObject getHtmlObject(HTMLElement element) {
		return null;
	}

	public String getName() {
		return null;
	}

	public HtmlRendererContext getOpener() {
		return null;
	}

	public HtmlRendererContext getParent() {
		return null;
	}

	public String getStatus() {
		return null;
	}

	public HtmlRendererContext getTop() {
		return null;
	}

	public UserAgentContext getUserAgentContext() {
		return null;
	}

	/**
	 * Returns false unless overridden.
	 */
	public boolean isClosed() {
		return false;
	}

	/**
	 * Returns true unless overridden.
	 */
	public boolean isImageLoadingEnabled() {
		return true;
	}

	/**
	 * Returns false unless overridden.
	 */
	public boolean isVisitedLink(HTMLLinkElement link) {
		return false;
	}

	public void linkClicked(HTMLElement linkNode, URL url, String target) {
	}

	public void navigate(URL url, String target) {
	}

	/**
	 * Returns true unless overridden.
	 */
	public boolean onContextMenu(HTMLElement element, MouseEvent event) {
		return true;
	}

	public void onMouseOut(HTMLElement element, MouseEvent event) {
	}

	public void onMouseOver(HTMLElement element, MouseEvent event) {
	}

	public HtmlRendererContext open(String absoluteUrl, String windowName,
			String windowFeatures, boolean replace) {
		return null;
	}

	public HtmlRendererContext open(URL url, String windowName,
			String windowFeatures, boolean replace) {
		return null;
	}

	public String prompt(String message, String inputDefault) {
		return null;
	}

	public void reload() {
	}

	public void scroll(int x, int y) {
	}

	public void setDefaultStatus(String value) {
	}

	public void setOpener(HtmlRendererContext opener) {
	}

	public void setStatus(String message) {
	}

	public void submitForm(String method, URL action, String target,
			String enctype, FormInput[] formInputs) {
	}

	/**
	 * Returns true unless overridden.
	 */
	public boolean onDoubleClick(HTMLElement element, MouseEvent event) {
		return true;
	}

	/**
	 * Returns true unless overridden.
	 */
	public boolean onMouseClick(HTMLElement element, MouseEvent event) {
		return true;
	}

	public void scrollBy(int x, int y) {
	}

	public void resizeBy(int byWidth, int byHeight) {		
	}

	public void resizeTo(int width, int height) {
	}

    public void forward() {
    }

    public String getCurrentURL() {
        return null;
    }

    public int getHistoryLength() {
        return 0;
    }

    public String getNextURL() {
        return null;
    }

    public String getPreviousURL() {
        return null;
    }

    public void goToHistoryURL(String url) {
    }

    public void moveInHistory(int offset) {
    }	
}
