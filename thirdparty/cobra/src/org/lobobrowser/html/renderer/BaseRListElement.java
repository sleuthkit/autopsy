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
import org.lobobrowser.html.style.AbstractCSS2Properties;
import org.lobobrowser.html.style.HtmlValues;
import org.lobobrowser.html.style.ListStyle;

class BaseRListElement extends RBlock {
	protected static final String DEFAULT_COUNTER_NAME = "$cobra.counter";
	protected ListStyle listStyle = null;
	
	public BaseRListElement(NodeImpl modelNode, int listNesting, UserAgentContext pcontext, HtmlRendererContext rcontext, FrameContext frameContext, RenderableContainer parentContainer) {
		super(modelNode, listNesting, pcontext, rcontext, frameContext,	parentContainer);
	}
	
	protected void applyStyle(int availWidth, int availHeight) {
		this.listStyle = null;
		super.applyStyle(availWidth, availHeight);
		Object rootNode = this.modelNode;
		if(!(rootNode instanceof HTMLElementImpl)) {
			return;
		}
		HTMLElementImpl rootElement = (HTMLElementImpl) rootNode;
		AbstractCSS2Properties props = rootElement.getCurrentStyle();
		if(props == null) {
			return;
		}
		ListStyle listStyle = null;
		String listStyleText = props.getListStyle();
		if(listStyleText != null) {
			listStyle = HtmlValues.getListStyle(listStyleText);
		}
		String listStyleTypeText = props.getListStyleType();
		if(listStyleTypeText != null) {
			int listType = HtmlValues.getListStyleType(listStyleTypeText);
			if(listType != ListStyle.TYPE_UNSET) {
				if(listStyle == null) {
					listStyle = new ListStyle();
				}
				listStyle.type = listType;
			}
		}	
		if(listStyle == null || listStyle.type == ListStyle.TYPE_UNSET) 
		{
			String typeAttributeText = rootElement.getAttribute("type");
			if(typeAttributeText != null) {
				int newStyleType = HtmlValues.getListStyleTypeDeprecated(typeAttributeText);
				if(newStyleType != ListStyle.TYPE_UNSET) {
					if(listStyle == null) {
						listStyle = new ListStyle();
						this.listStyle = listStyle;
					}
					listStyle.type = newStyleType;					
				}
			}
		}
		this.listStyle = listStyle;
	}
	
	public String toString() {
		return "BaseRListElement[node=" + this.modelNode + "]";
	}
}
