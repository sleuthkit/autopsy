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
 * Created on Jan 29, 2006
 */
package org.lobobrowser.html.test;

import java.awt.Component;
import java.awt.Insets;
import java.net.URL;

import org.lobobrowser.html.*;
import org.lobobrowser.html.gui.HtmlPanel;
import org.w3c.dom.Document;

/**
 * The <code>SimpleBrowserFrame</code> class implements
 * the {@link org.lobobrowser.html.BrowserFrame} interface. 
 * It represents a browser frame component.
 * @see HtmlRendererContext#createBrowserFrame()
 */
public class SimpleBrowserFrame extends HtmlPanel implements BrowserFrame {
	/** The {@link HtmlRendererContext} associated with the browser frame. */
	private final HtmlRendererContext rcontext;
	private final HtmlRendererContext parentRcontext;
	
	public SimpleBrowserFrame(HtmlRendererContext parentRcontext) {
		this.parentRcontext = parentRcontext;
		this.rcontext = this.createHtmlRendererContext(parentRcontext);
	}
	
	/**
	 * Creates the {@link HtmlRendererContext} associated with this browser
	 * frame. Override to use a specialized instance.
	 * @param parentRcontext The parent context.
	 */
	protected HtmlRendererContext createHtmlRendererContext(HtmlRendererContext parentRcontext) {
		return new SimpleHtmlRendererContext(this, parentRcontext);
	}
	
	public HtmlRendererContext getHtmlRendererContext() {
		return this.rcontext;
	}

	public Component getComponent() {
		return this;
	}

	public void loadURL(URL url) {
		this.rcontext.navigate(url, "_this");
	}

	public Document getContentDocument() {
		return (Document) this.getRootNode();
	}
	
	public HtmlRendererContext getParentHtmlRendererContext() {
		return this.parentRcontext;
	}

    public void setDefaultMarginInsets(Insets insets) {
        // Current implementation is the frame HtmlPanel.
        super.setDefaultMarginInsets(insets);
    }

    public void setDefaultOverflowX(int overflowX) {
        super.setDefaultOverflowX(overflowX);
    }

    public void setDefaultOverflowY(int overflowY) {
        super.setDefaultOverflowY(overflowY);
    }
}
