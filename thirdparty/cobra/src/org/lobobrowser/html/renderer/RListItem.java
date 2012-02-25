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

import java.awt.*;

import org.lobobrowser.html.HtmlRendererContext;
import org.lobobrowser.html.UserAgentContext;
import org.lobobrowser.html.domimpl.NodeImpl;
import org.lobobrowser.html.style.ListStyle;
import org.lobobrowser.html.style.RenderState;
import org.w3c.dom.html2.*;

class RListItem extends BaseRListElement {
	private static final int BULLET_WIDTH = 5;
	private static final int BULLET_HEIGHT = 5;
	private static final int BULLET_RMARGIN = 5;
	private static final int BULLET_SPACE_WIDTH = 36;

	public RListItem(NodeImpl modelNode, int listNesting, UserAgentContext pcontext, HtmlRendererContext rcontext, FrameContext frameContext, RenderableContainer parentContainer, RCollection parent) {
		super(modelNode, listNesting, pcontext, rcontext, frameContext, parentContainer);
		//this.defaultMarginInsets = new java.awt.Insets(0, BULLET_SPACE_WIDTH, 0, 0);
	}
	
	public int getViewportListNesting(int blockNesting) {
		return blockNesting + 1;
	}

	public void invalidateLayoutLocal() {
		super.invalidateLayoutLocal();
		this.value = null;
	}

	private static final Integer UNSET = new Integer(Integer.MIN_VALUE);
	private Integer value = null;
	
	private Integer getValue() {
		Integer value = this.value;
		if(value == null) {
			HTMLElement node = (HTMLElement) this.modelNode;
			String valueText = node == null ? null : node.getAttribute("value");
			if(valueText == null) {
				value = UNSET;
			}
			else {
				try {
					value = Integer.valueOf(valueText);
				} catch(NumberFormatException nfe) {
					value = UNSET;
				}
			}
			this.value = value;
		}
		return value;
	}

	private int count; 
	
	public void doLayout(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight, FloatingBoundsSource floatBoundsSource, int defaultOverflowX, int defaultOverflowY, boolean sizeOnly) {
		super.doLayout(availWidth, availHeight, expandWidth, expandHeight, floatBoundsSource, defaultOverflowX, defaultOverflowY, sizeOnly);
		//Note: Count must be calculated even if layout is valid.
		RenderState renderState = this.modelNode.getRenderState();
		Integer value = this.getValue();
		if(value == UNSET) {
			this.count = renderState.incrementCount(DEFAULT_COUNTER_NAME, this.listNesting);
		}
		else {
			int newCount = value.intValue();
			this.count = newCount;
			renderState.resetCount(DEFAULT_COUNTER_NAME, this.listNesting, newCount + 1);
		}
	}

	public void paint(Graphics g) {
		super.paint(g);
		RenderState rs = this.modelNode.getRenderState();
		Insets marginInsets = this.marginInsets;
		RBlockViewport layout = this.bodyLayout;
		if(layout != null) {
			ListStyle listStyle = this.listStyle;
			int bulletType = listStyle == null ? ListStyle.TYPE_UNSET : listStyle.type;
			if(bulletType != ListStyle.TYPE_NONE) {
				if(bulletType == ListStyle.TYPE_UNSET) {
					RCollection parent = this.getOriginalOrCurrentParent();
					if(!(parent instanceof RList)) {
						parent = parent.getOriginalOrCurrentParent();
					}
					if(parent instanceof RList) {
						ListStyle parentListStyle = ((RList) parent).listStyle;
						bulletType = parentListStyle == null ? ListStyle.TYPE_DISC : parentListStyle.type;
					}
					else {
						bulletType = ListStyle.TYPE_DISC;
					}
				}
				// Paint bullets
				Color prevColor = g.getColor();
				g.setColor(rs.getColor());
				try {
					Insets insets = this.getInsets(this.hasHScrollBar, this.hasVScrollBar);
					Insets paddingInsets = this.paddingInsets;
					int baselineOffset = layout.getFirstBaselineOffset();
					int bulletRight = (marginInsets == null ? 0 : marginInsets.left) - BULLET_RMARGIN;
					int bulletBottom = insets.top + baselineOffset + (paddingInsets == null ? 0 : paddingInsets.top);
					int bulletTop = bulletBottom - BULLET_HEIGHT;
					int bulletLeft = bulletRight - BULLET_WIDTH;
					int bulletNumber = this.count;
					String numberText = null;
					switch(bulletType) {
					case ListStyle.TYPE_DECIMAL:
						numberText = bulletNumber + ".";
						break;
					case ListStyle.TYPE_LOWER_ALPHA:
						numberText = ((char) ('a' + bulletNumber)) + ".";
						break;
					case ListStyle.TYPE_UPPER_ALPHA:
						numberText = ((char) ('A' + bulletNumber)) + ".";
						break;
					case ListStyle.TYPE_DISC:
						g.fillOval(bulletLeft, bulletTop, BULLET_WIDTH, BULLET_HEIGHT);
						break;					
					case ListStyle.TYPE_CIRCLE:
						g.drawOval(bulletLeft, bulletTop, BULLET_WIDTH, BULLET_HEIGHT);
						break;
					case ListStyle.TYPE_SQUARE:
						g.fillRect(bulletLeft, bulletTop, BULLET_WIDTH, BULLET_HEIGHT);					
						break;
					}
					if(numberText != null) {
						FontMetrics fm = g.getFontMetrics();
						int numberLeft = bulletRight - fm.stringWidth(numberText);
						int numberY = bulletBottom;
						g.drawString(numberText, numberLeft, numberY);
					}
				} finally {
					g.setColor(prevColor);
				}
			}
		}
	}
}
