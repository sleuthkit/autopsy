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

/**
 * This element is used for SMALL and BIG.
 */
public class HTMLFontSizeChangeElementImpl extends HTMLAbstractUIElement {
	private final int fontChange;
	
	public HTMLFontSizeChangeElementImpl(String name, int fontChange) {
		super(name);
		this.fontChange = fontChange;
	}

	protected RenderState createRenderState(RenderState prevRenderState) {
		return super.createRenderState(prevRenderState);
	}
	
    protected AbstractCSS2Properties createDefaultStyleSheet() {
        ModelNode parentModelNode = this.getParentModelNode();
        RenderState parentRS = parentModelNode == null ? null : parentModelNode.getRenderState();
        String fontSize = null;
        int prevFontSize = parentRS != null ? parentRS.getFont().getSize() : HtmlValues.DEFAULT_FONT_SIZE_INT;
        int newFontSize = prevFontSize + (this.fontChange * 2);
        fontSize = newFontSize + "px";
        ComputedCSS2Properties css = new ComputedCSS2Properties(this);
        css.internalSetLC("font-size", fontSize);
        return css;
    }

}
