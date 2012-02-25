package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.*;
import org.w3c.dom.html2.HTMLParagraphElement;

public class HTMLPElementImpl extends HTMLAbstractUIElement implements
		HTMLParagraphElement {
	public HTMLPElementImpl(String name) {
		super(name);
	}
	
	public String getAlign() {
		return this.getAttribute("align");
	}

	public void setAlign(String align) {
		this.setAttribute("align", align);
	}

	protected void appendInnerTextImpl(StringBuffer buffer) {
		int length = buffer.length();
		int lineBreaks;
		if(length == 0) {
			lineBreaks = 2;
		}
		else {
			int start = length - 4;
			if (start < 0) {
				start = 0;
			}
			lineBreaks = 0;
			for(int i = start; i < length; i++) {
				char ch = buffer.charAt(i);
				if(ch == '\n') {
					lineBreaks++;
				}
			}
		}
		for(int i = 0; i < 2 - lineBreaks; i++) {
			buffer.append("\r\n");
		}
		super.appendInnerTextImpl(buffer);
		buffer.append("\r\n\r\n");
	}

	protected RenderState createRenderState(RenderState prevRenderState) {
		return new ParagraphRenderState(prevRenderState, this);
	}
}
