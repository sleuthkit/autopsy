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
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.*;
import org.lobobrowser.util.gui.ColorFactory;
import org.w3c.dom.html2.HTMLBaseFontElement;

public class HTMLBaseFontElementImpl extends HTMLAbstractUIElement implements
		HTMLBaseFontElement {
	public HTMLBaseFontElementImpl(String name) {
		super(name);
	}

	public String getColor() {
		return this.getAttribute("color");
	}

	public String getFace() {
		return this.getAttribute("face");
	}

	public void setColor(String color) {
		this.setAttribute("color", color);
	}

	public void setFace(String face) {
		this.setAttribute("face", face);
	}

	public int getSize() {
		try {
			return Integer.parseInt(this.getAttribute("size"));
		} catch(Exception thrown) {
			this.warn("getSize(): Unable to parse size attribute in " + this + ".", thrown);
			return 0;
		}
	}

	public void setSize(int size) {
		this.setAttribute("size", String.valueOf(size));
	}
	
	protected RenderState createRenderState(RenderState prevRenderState) {
		String size = this.getAttribute("size");
		if(size != null) {
			int fontNumber = HtmlValues.getFontNumberOldStyle(size, prevRenderState);
			float fontSize = HtmlValues.getFontSize(fontNumber);
			prevRenderState = new BaseFontRenderState(prevRenderState, fontNumber);
		}
		return super.createRenderState(prevRenderState);
	}

    protected AbstractCSS2Properties createDefaultStyleSheet() {
        String fontFamily = this.getAttribute("face");
        String color = this.getAttribute("color");
        String size = this.getAttribute("size");
        ModelNode parentModelNode = this.getParentModelNode();
        RenderState parentRS = parentModelNode == null ? null : parentModelNode.getRenderState();
        String fontSize = null;
        if(parentRS != null) {
            int fontNumber = HtmlValues.getFontNumberOldStyle(size, parentRS);
            fontSize = HtmlValues.getFontSizeSpec(fontNumber);
        }        
        ComputedCSS2Properties css = new ComputedCSS2Properties(this);
        if(fontSize != null) {
            css.internalSetLC("font-size", fontSize);
        }
        if(fontFamily != null) {
            css.internalSetLC("font-family", fontFamily);
        }
        if(color != null) {
            css.internalSetLC("color", color);
        }
        return css;
    }

	    
}
