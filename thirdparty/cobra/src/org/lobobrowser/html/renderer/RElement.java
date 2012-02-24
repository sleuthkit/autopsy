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

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.style.RenderState;

/**
 * A renderer node for elements such as blocks, lists, tables, inputs, images, etc.
 */
public interface RElement extends RCollection, UINode, BoundableRenderable {
	/**
	 * Lays out the subtree below the RElement.
	 * The RElement is expected to set its own dimensions, but not its origin.
	 * @param availWidth The available width from the parent's canvas.
	 * @param availHeight The available height from the parent's canvas.
	 * @param sizeOnly Whether the layout is for sizing determination only.
	 */
	public void layout(int availWidth, int availHeight, boolean sizeOnly);
	
	/**
	 * Vertical alignment for elements rendered in a line.
	 * Returns one of the constants defined in this class.
	 */
	public int getVAlign();
	
	public int getMarginTop();
    public int getMarginLeft();
    public int getMarginBottom();
    public int getMarginRight();
    
    public int getCollapsibleMarginTop();
    public int getCollapsibleMarginBottom();
	
	public static final int VALIGN_TOP = 0;
	public static final int VALIGN_MIDDLE = 1;
	public static final int VALIGN_BOTTOM = 2;
	public static final int VALIGN_ABSMIDDLE = 3;
	public static final int VALIGN_ABSBOTTOM = 4;
	public static final int VALIGN_BASELINE = 5;
}
