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
 * Created on Oct 23, 2005
 */
package org.lobobrowser.html.renderer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.*;

import org.lobobrowser.html.domimpl.*;

import java.util.logging.*;

abstract class BaseControl extends JComponent implements UIControl {
	private static final Logger logger = Logger.getLogger(BaseControl.class.getName());
	protected static final Dimension ZERO_DIMENSION = new Dimension(0, 0);
	protected final HTMLElementImpl controlElement;
	protected RUIControl ruicontrol;
	
	/**
	 * @param context
	 */
	public BaseControl(HTMLElementImpl modelNode) {
		this.controlElement = modelNode;
	}

	public Component getComponent() {
		return this;
	}
	
	public void setRUIControl(RUIControl ruicontrol) {
		this.ruicontrol = ruicontrol;
	}		
	
	public int getVAlign() {
		return RElement.VALIGN_BASELINE;
	}

	/**
	 * Method invoked when image changes size. 
	 * It's expected to be called outside the GUI thread.
	 */
	protected void invalidateAndRepaint() {
		RUIControl rc = this.ruicontrol;
		if(rc == null) {
			logger.severe("invalidateAndPaint(): RUIControl not set.");
			return;
		}
		if(rc.isValid()) {
			rc.relayout();
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.UIControl#getBackgroundColor()
	 */
	public Color getBackgroundColor() { 
		return this.getBackground();
	}
	
	public void reset(int availWidth, int availHeight) {
	}
}
