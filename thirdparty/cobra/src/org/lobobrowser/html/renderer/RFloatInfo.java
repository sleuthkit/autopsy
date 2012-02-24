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

import org.lobobrowser.html.domimpl.ModelNode;

final class RFloatInfo implements Renderable {
	private final ModelNode modelNode;
	private final BoundableRenderable element;
	private final boolean leftFloat;
	
	public RFloatInfo(ModelNode node, BoundableRenderable element, boolean leftFloat) {
		this.modelNode = node;
		this.element = element;
		this.leftFloat = leftFloat;
	}

	public boolean isLeftFloat() {
		return this.leftFloat;
	}
	
	public ModelNode getModelNode() {
		return this.modelNode;
	}

	public void paint(Graphics g) {
		// nop
	}

	public final BoundableRenderable getRenderable() {
		return element;
	}
}
