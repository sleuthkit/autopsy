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
 * Created on Nov 19, 2005
 */
package org.lobobrowser.html.renderer;

import java.awt.*;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.style.RenderState;

class HrControl extends BaseControl {
	public HrControl(HTMLElementImpl modelNode) {
		super(modelNode);
	}
	
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Dimension size = this.getSize();
		int offset = 8;
		int x = offset;
		int y = size.height / 2 - 1;
		int width = size.width - offset * 2;
		g.setColor(Color.black);
		g.drawRect(x, y, width, 2);
	}

	public boolean paintSelection(Graphics g, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
		return inSelection;
	}		

	private int availWidth;

	public void reset(int availWidth, int availHeight) {
		this.availWidth = availWidth;
	}
	
	public Dimension getPreferredSize() {
		RenderState rs = this.controlElement.getRenderState();
		FontMetrics fm = rs.getFontMetrics();
		return new Dimension(this.availWidth, fm.getHeight());
	}	
}
