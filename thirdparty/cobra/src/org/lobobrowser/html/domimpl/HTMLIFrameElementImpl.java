package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.*;
import org.lobobrowser.html.js.Window;
import org.lobobrowser.html.style.*;
import org.lobobrowser.util.gui.ColorFactory;
import org.w3c.dom.Document;
import org.w3c.dom.html2.HTMLIFrameElement;

public class HTMLIFrameElementImpl extends HTMLAbstractUIElement implements
		HTMLIFrameElement, FrameNode {
	private volatile BrowserFrame browserFrame;
	
	public HTMLIFrameElementImpl(String name) {
		super(name);
	}
	
	public void setBrowserFrame(BrowserFrame frame) {
		this.browserFrame = frame;
		if(frame != null) {
			String src = this.getAttribute("src");
			if(src != null) {
				try {
					frame.loadURL(this.getFullURL(src));
				} catch(java.net.MalformedURLException mfu) {
					this.warn("setBrowserFrame(): Unable to navigate to src.", mfu);
				}
			}
		}
	}
	
	public BrowserFrame getBrowserFrame() {
		return this.browserFrame;
	}

	public String getAlign() {
		return this.getAttribute("align");
	}

	public Document getContentDocument() {
		//TODO: Domain-based security
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

	public String getFrameBorder() {
		return this.getAttribute("frameborder");
	}

	public String getHeight() {
		return this.getAttribute("height");
	}

	public String getLongDesc() {
		return this.getAttribute("longdesc");
	}

	public String getMarginHeight() {
		return this.getAttribute("marginheight");
	}

	public String getMarginWidth() {
		return this.getAttribute("marginwidth");
	}

	public String getName() {
		return this.getAttribute("name");
	}

	public String getScrolling() {
		return this.getAttribute("scrolling");
	}

	public String getSrc() {
		return this.getAttribute("src");
	}

	public String getWidth() {
		return this.getAttribute("width");
	}

	public void setAlign(String align) {
		this.setAttribute("align", align);
	}

	public void setFrameBorder(String frameBorder) {
		this.setAttribute("frameborder", frameBorder);
	}

	public void setHeight(String height) {
		this.setAttribute("height", height);
	}

	public void setLongDesc(String longDesc) {
		this.setAttribute("longdesc", longDesc);
	}

	public void setMarginHeight(String marginHeight) {
		this.setAttribute("marginHeight", marginHeight);
	}

	public void setMarginWidth(String marginWidth) {
		this.setAttribute("marginWidth", marginWidth);
	}

	public void setName(String name) {
		this.setAttribute("name", name);
	}

	public void setScrolling(String scrolling) {
		this.setAttribute("scrolling", scrolling);
	}

	public void setSrc(String src) {
		this.setAttribute("src", src);
	}

	public void setWidth(String width) {
		this.setAttribute("width", width);
	}

	protected void assignAttributeField(String normalName, String value) {
		if("src".equals(normalName)) {
			BrowserFrame frame = this.browserFrame;
			if(frame != null) {
				try {
					frame.loadURL(this.getFullURL(value));
				} catch(java.net.MalformedURLException mfu) {
					this.warn("assignAttributeField(): Unable to navigate to src.", mfu);
				}
			}
		}
		else {
			super.assignAttributeField(normalName, value);
		}			
	}
	
	protected RenderState createRenderState(RenderState prevRenderState) {
	    return new IFrameRenderState(prevRenderState, this);
	}
}
