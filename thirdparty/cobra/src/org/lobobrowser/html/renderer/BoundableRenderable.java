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
import java.awt.event.*;

import org.lobobrowser.html.domimpl.ModelNode;

/**
 * A renderer node with well-defined bounds. Most renderer nodes
 * implement this interface.
 */
public interface BoundableRenderable extends Renderable {
	public ModelNode getModelNode();
	public Rectangle getBounds();
	public Dimension getSize();
	public Point getOrigin();
	public Point getOriginRelativeTo(RCollection ancestor);
	
	/**
	 * Gets the parent where the renderable is rendered.
	 */
	public RCollection getParent();
	public void setOriginalParent(RCollection origParent);
	
	/**
	 * Gets the parent set with {@link #setOriginalParent(RCollection)}.
	 * It represents the parent where the renderable would have
	 * been originally rendered according to the DOM. This will
	 * be non-null only if {@link #getParent()} is not the parent
	 * where this renderable would have been originally rendered.
	 */
	public RCollection getOriginalParent();
	
	/**
	 * Returns {@link #getOriginalParent()} if not null. Otherwise
	 * it returns {@link #getParent()}.
	 */
	public RCollection getOriginalOrCurrentParent();
	public void setBounds(int x, int y, int with, int height);
	public void setOrigin(int x, int y);
	public void setX(int x);
	public void setY(int y);
	public int getX();
	public int getY();
	public int getHeight();
	public int getWidth();
	public void setHeight(int height);
	public void setWidth(int width);
	public RenderableSpot getLowestRenderableSpot(int x, int y);
	public Point getRenderablePoint(int guiX, int guiY);
	public void repaint();
	
	/**
	 * Returns false if the event is consumed. True to propagate further.
	 */
	public boolean onMousePressed(MouseEvent event, int x, int y);
	public boolean onMouseReleased(MouseEvent event, int x, int y);
	public boolean onMouseDisarmed(MouseEvent event);
	public boolean onMouseClick(MouseEvent event, int x, int y);
	public boolean onDoubleClick(MouseEvent event, int x, int y);
	public boolean onRightClick(MouseEvent event, int x, int y);
	public void onMouseMoved(MouseEvent event, int x, int y, boolean triggerEvent, ModelNode limit);
	public void onMouseOut(MouseEvent event, int x, int y, ModelNode limit);

	/**
	 * Returns true if the renderable is fully contained by its modelNode, but
	 * said modelNode does not fully contain an ancestor renderable.
	 */
	public boolean isContainedByNode();
	
	/**
	 * Asks the Renderable to paint the selection between two 
	 * points. Nothing will be done if the points are outside
	 * the Renderable.
	 * @param g
	 * @param inSelection
	 * @param startPoint
	 * @param endPoint
	 * @return True iff it's in selection when finished painting.
	 */
	public boolean paintSelection(Graphics g, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint);	
	
	/**
	 * Paints by either creating a new clipped graphics context corresponding
	 * to the bounds of the Renderable, or by translating the origin. 
	 * @param g Parent's Graphics context.
	 */
	public void paintTranslated(Graphics g);
	
	public boolean extractSelectionText(StringBuffer buffer, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint);
	public void repaint(int x, int y, int width, int height);
	public void relayout();
	public void setParent(RCollection parent);
	public java.awt.Point getGUIPoint(int clientX, int clientY);
	
	public int getOrdinal();
	public void setOrdinal(int ordinal);
	public int getZIndex();
	public void invalidateLayoutUpTree();
}
