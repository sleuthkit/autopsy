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
package org.lobobrowser.html.renderer;

import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.util.Iterator;

import org.lobobrowser.html.domimpl.ModelNode;
import org.lobobrowser.util.*;

public class RRelative extends BaseRCollection {
	private final RElement child;
	private final int xoffset;
	private final int yoffset;
	
	public RRelative(RenderableContainer container, ModelNode modelNode, final RElement child, final int xoffset, final int yoffset) {
		super(container, modelNode);
		child.setOriginalParent(this);
		child.setParent(this);
		child.setOrigin(xoffset, yoffset);
		this.child = child;
		this.xoffset = xoffset;
		this.yoffset = yoffset;
	}

	public void assignDimension() {
		RElement child = this.child;
		this.width = child.getWidth();
		this.height = child.getHeight();
	}
	
	public FloatingInfo getExportableFloatingInfo() {
	    RElement child = this.child;
	    if(child instanceof RBlock) {
	        // There are no insets, and hence no shift.
	        return ((RBlock) child).getExportableFloatingInfo();
	    }
	    else {
	        return null;
	    }
	}
	
//	public void adjust() {
//		RElement child = this.child;
//		if(child instanceof RBlock) {
//			((RBlock) child).adjust();
//		}
//	}
//
//	public FloatingBounds getExportableFloatingBounds() {
//		RElement child = this.child;
//		if(!(child instanceof RBlock)) {
//			return null;
//		}
//		FloatingBounds blockBounds = ((RBlock) child).getExportableFloatingBounds();
//		if(blockBounds == null) {
//			return null;
//		}
//		return new ShiftedFloatingBounds(blockBounds, this.xoffset, -this.xoffset, this.yoffset);
//	}
//	
	public RElement getElement() {
		return this.child;
	}
	
	public int getXOffset() {
		return xoffset;
	}

	public int getYOffset() {
		return yoffset;
	}

	public Iterator getRenderables() {
		return CollectionUtilities.singletonIterator(this.child);
	}

	protected void invalidateLayoutLocal() {
		// nop
	}

	public RenderableSpot getLowestRenderableSpot(int x, int y) {
		return this.child.getLowestRenderableSpot(x - this.xoffset, y - this.yoffset);
	}

	public boolean isContainedByNode() {
		return true;
	}

	public boolean onDoubleClick(MouseEvent event, int x, int y) {
		return this.child.onDoubleClick(event, x - this.xoffset, y - this.yoffset);
	}

	public boolean onMouseClick(MouseEvent event, int x, int y) {
		return this.child.onMouseClick(event, x - this.xoffset, y - this.yoffset);
	}

	public boolean onMouseDisarmed(MouseEvent event) {
		return this.child.onMouseDisarmed(event);
	}

	public boolean onMousePressed(MouseEvent event, int x, int y) {
		return this.child.onMousePressed(event, x - this.xoffset, y - this.yoffset);
	}

	public boolean onMouseReleased(MouseEvent event, int x, int y) {
		return this.child.onMouseReleased(event, x - this.xoffset, y - this.yoffset);
	}

	public void paint(Graphics g) {
		this.child.paintTranslated(g);
	}
}
