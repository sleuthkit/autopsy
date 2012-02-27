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

import java.awt.Color;
import java.awt.Component;

/**
 * A RenderableContainer is either usually a parent
 * block or the root GUI component. It's is a Renderable
 * or GUI component whose layout may be invalidated.
 */
public interface RenderableContainer {
	//public Insets getInsets();
	public Component addComponent(Component component);
	//public void remove(Component component);
	public void invalidateLayoutUpTree();
	public void repaint(int x, int y, int width, int height);
	public void relayout();
	public void updateAllWidgetBounds();
	public Color getPaintedBackgroundColor();
	public java.awt.Point getGUIPoint(int x, int y);
	public void focus();
	public void addDelayedPair(DelayedPair pair);
	public java.util.Collection getDelayedPairs();
	public RenderableContainer getParentContainer();
	public void clearDelayedPairs();
}
