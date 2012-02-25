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
package org.lobobrowser.html.renderer;

import org.lobobrowser.html.HtmlRendererContext;
import org.lobobrowser.html.UserAgentContext;
import org.lobobrowser.html.domimpl.HTMLElementImpl;
import org.lobobrowser.html.domimpl.NodeImpl;
import org.lobobrowser.html.style.ListStyle;
import org.lobobrowser.html.style.RenderState;

class RList extends BaseRListElement {
	public RList(NodeImpl modelNode, int listNesting, UserAgentContext pcontext, HtmlRendererContext rcontext, FrameContext frameContext, RenderableContainer parentContainer, RCollection parent) {
		super(modelNode, listNesting, pcontext, rcontext, frameContext,	parentContainer);
		//this.defaultMarginInsets = new java.awt.Insets(5, 0, 5, 0);
	}

	protected void applyStyle(int availWidth, int availHeight) {
		super.applyStyle(availWidth, availHeight);
		ListStyle listStyle = this.listStyle;
		if(listStyle == null || listStyle.type == ListStyle.TYPE_UNSET) {
			Object rootNode = this.modelNode;
			if(!(rootNode instanceof HTMLElementImpl)) {
				return;
			}
			HTMLElementImpl rootElement = (HTMLElementImpl) rootNode;
			if(listStyle == null) {
				listStyle = new ListStyle();
				this.listStyle = listStyle;
			}
			if("ul".equalsIgnoreCase(rootElement.getTagName())) {
				int listNesting = this.listNesting;
				if(listNesting == 0) {
					listStyle.type = ListStyle.TYPE_DISC;
				}
				else if(listNesting == 1) {
					listStyle.type = ListStyle.TYPE_CIRCLE;
				}
				else {
					listStyle.type = ListStyle.TYPE_SQUARE;
				}
			}
			else {
				listStyle.type = ListStyle.TYPE_DECIMAL;
			}
		}	
	}

	public void doLayout(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight, FloatingBoundsSource floatBoundsSource, int defaultOverflowX, int defaultOverflowY, boolean sizeOnly) {
		RenderState renderState = this.modelNode.getRenderState();
		int counterStart = 1;
		Object rootNode = this.modelNode;
		if(!(rootNode instanceof HTMLElementImpl)) {
			return;
		}
		HTMLElementImpl rootElement = (HTMLElementImpl) rootNode;
		String startText = rootElement.getAttribute("start");
		if(startText != null) {
			try {
				counterStart = Integer.parseInt(startText);
			} catch(NumberFormatException nfe) {
				// ignore
			}
		}
		renderState.resetCount(DEFAULT_COUNTER_NAME, this.listNesting, counterStart);
		super.doLayout(availWidth, availHeight, expandWidth, expandHeight, floatBoundsSource, defaultOverflowX, defaultOverflowY, sizeOnly);
	}
}	