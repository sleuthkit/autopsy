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
import java.awt.event.MouseEvent;
import java.util.*;

import org.lobobrowser.html.*;
import org.lobobrowser.util.*;
import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.style.RenderState;

import org.w3c.dom.css.*;

class RTable extends BaseElementRenderable {
    private static final int MAX_CACHE_SIZE = 10;
    private final Map cachedLayout = new HashMap(5);
	private final TableMatrix tableMatrix;
	private SortedSet positionedRenderables;
	private int otherOrdinal;
	private LayoutKey lastLayoutKey = null;
	private LayoutValue lastLayoutValue = null;
	
	public RTable(HTMLElementImpl modelNode, UserAgentContext pcontext, HtmlRendererContext rcontext, FrameContext frameContext, RenderableContainer container) {
		super(container, modelNode, pcontext);
		this.tableMatrix = new TableMatrix(modelNode, pcontext, rcontext, frameContext, this, this);
	}

	public int getVAlign() {
		// Not used
		return VALIGN_BASELINE;
	}

	public void paint(Graphics g) {
		RenderState rs = this.modelNode.getRenderState();
		if(rs != null && rs.getVisibility() != RenderState.VISIBILITY_VISIBLE) {
			// Just don't paint it.
			return;
		}
		try {
			this.prePaint(g);
			Dimension size = this.getSize();
			//TODO: No scrollbars
			TableMatrix tm = this.tableMatrix;
			tm.paint(g, size);
			Collection prs = this.positionedRenderables;
			if(prs != null) {
				Iterator i = prs.iterator();
				while(i.hasNext()) {
					PositionedRenderable pr = (PositionedRenderable) i.next();
					BoundableRenderable r = pr.renderable;					
					r.paintTranslated(g);
				}
			}
		} finally {
			// Must always call super implementation
			super.paint(g);
		}
	}
		
	public void doLayout(int availWidth, int availHeight, boolean sizeOnly) {
	    Map cachedLayout = this.cachedLayout;
	    RenderState rs = this.modelNode.getRenderState();
	    int whitespace = rs == null ? RenderState.WS_NORMAL : rs.getWhiteSpace();
	    Font font = rs == null ? null : rs.getFont();
	    LayoutKey layoutKey = new LayoutKey(availWidth, availHeight, whitespace, font);
	    LayoutValue layoutValue;
	    if(sizeOnly) {
	        layoutValue = (LayoutValue) cachedLayout.get(layoutKey);
	    }
	    else {
	        if(Objects.equals(layoutKey, this.lastLayoutKey)) {
	            layoutValue = this.lastLayoutValue;
	        }
	        else {
	            layoutValue = null;
	        }
	    }
	    if(layoutValue == null) {
			Collection prs = this.positionedRenderables;
			if(prs != null) {
				prs.clear();
			}
			this.otherOrdinal = 0;
			this.clearGUIComponents();
			this.clearDelayedPairs();
			this.applyStyle(availWidth, availHeight);
			TableMatrix tm = this.tableMatrix;
			Insets insets = this.getInsets(false, false);
			tm.reset(insets, availWidth, availHeight);
			//TODO: No scrollbars
			tm.build(availWidth, availHeight, sizeOnly);
			tm.doLayout(insets);
			
			// Import applicable delayed pairs.
			// Only needs to be done if layout was
			// forced. Otherwise, they should've
			// been imported already.
			Collection pairs = this.delayedPairs;
			if(pairs != null) {
				Iterator i = pairs.iterator();
				while(i.hasNext()) {
					DelayedPair pair = (DelayedPair) i.next();
					if(pair.targetParent == this) {
						this.importDelayedPair(pair);
					}
				}
			}
			layoutValue = new LayoutValue(tm.getTableWidth(), tm.getTableHeight());
			if(sizeOnly) {
			    if(cachedLayout.size() > MAX_CACHE_SIZE) {
			        // Unlikely, but we should ensure it's bounded.
			        cachedLayout.clear();
			    }
			    cachedLayout.put(layoutKey, layoutValue);
			    this.lastLayoutKey = null;
			    this.lastLayoutValue = null;
			}
			else {
			    this.lastLayoutKey = layoutKey;
			    this.lastLayoutValue = layoutValue;
			}
	    }
	    this.width = layoutValue.width;
	    this.height = layoutValue.height;
		this.sendGUIComponentsToParent();
		this.sendDelayedPairsToParent();
	}

	public void invalidateLayoutLocal() {
		super.invalidateLayoutLocal();
		this.cachedLayout.clear();
		this.lastLayoutKey = null;
		this.lastLayoutValue = null;
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#getRenderablePoint(int, int)
	 */
	public RenderableSpot getLowestRenderableSpot(int x, int y) {
		Collection prs = this.positionedRenderables;
		if(prs != null) {
			Iterator i = prs.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				BoundableRenderable r = pr.renderable;
				int childX = x - r.getX();
				int childY = y - r.getY();
				RenderableSpot rs = r.getLowestRenderableSpot(childX, childY);
				if(rs != null) {
					return rs;
				}
			}
		}
		RenderableSpot rs = this.tableMatrix.getLowestRenderableSpot(x, y);
		if(rs != null) {
			return rs;
		}
		return new RenderableSpot(this, x, y);
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseClick(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMouseClick(MouseEvent event, int x, int y) {
		Collection prs = this.positionedRenderables;
		if(prs != null) {
			Iterator i = prs.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				BoundableRenderable r = pr.renderable;
				Rectangle bounds = r.getBounds();
				if(bounds.contains(x, y)) {
					int childX = x - r.getX();
					int childY = y - r.getY();
					if(!r.onMouseClick(event, childX, childY)) {
						return false;
					}
				}
			}
		}
		return this.tableMatrix.onMouseClick(event, x, y);
	}

	public boolean onDoubleClick(MouseEvent event, int x, int y) {
		Collection prs = this.positionedRenderables;
		if(prs != null) {
			Iterator i = prs.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				BoundableRenderable r = pr.renderable;
				Rectangle bounds = r.getBounds();
				if(bounds.contains(x, y)) {
					int childX = x - r.getX();
					int childY = y - r.getY();
					if(!r.onDoubleClick(event, childX, childY)) {
						return false;
					}
				}
			}
		}
		return this.tableMatrix.onDoubleClick(event, x, y);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseDisarmed(java.awt.event.MouseEvent)
	 */
	public boolean onMouseDisarmed(MouseEvent event) {
		return this.tableMatrix.onMouseDisarmed(event);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMousePressed(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMousePressed(MouseEvent event, int x, int y) {
		Collection prs = this.positionedRenderables;
		if(prs != null) {
			Iterator i = prs.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				BoundableRenderable r = pr.renderable;
				Rectangle bounds = r.getBounds();
				if(bounds.contains(x, y)) {
					int childX = x - r.getX();
					int childY = y - r.getY();
					if(!r.onMousePressed(event, childX, childY)) {
						return false;
					}
				}
			}
		}
		return this.tableMatrix.onMousePressed(event, x, y);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseReleased(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMouseReleased(MouseEvent event, int x, int y) {
		Collection prs = this.positionedRenderables;
		if(prs != null) {
			Iterator i = prs.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				BoundableRenderable r = pr.renderable;
				Rectangle bounds = r.getBounds();
				if(bounds.contains(x, y)) {
					int childX = x - r.getX();
					int childY = y - r.getY();
					if(!r.onMouseReleased(event, childX, childY)) {
						return false;
					}
				}
			}
		}
		return this.tableMatrix.onMouseReleased(event, x, y);
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RCollection#getRenderables()
	 */
	public Iterator getRenderables() {
		Collection prs = this.positionedRenderables;
		if(prs != null) {
			Collection c = new java.util.LinkedList();
			Iterator i = prs.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				BoundableRenderable r = pr.renderable;
				c.add(r);
			}
			Iterator i2 = this.tableMatrix.getRenderables();
			while(i2.hasNext()) {
				c.add(i2.next());
			}
			return c.iterator();
		}
		else {
			return this.tableMatrix.getRenderables();
		}
	}

	public void repaint(ModelNode modelNode) {
		//NOP
	}
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RenderableContainer#getBackground()
	 */
	public Color getPaintedBackgroundColor() {
		return this.container.getPaintedBackgroundColor();
	}
	
	private final void addPositionedRenderable(BoundableRenderable renderable, boolean verticalAlignable, boolean isFloat) {
		// Expected to be called only in GUI thread.
		SortedSet others = this.positionedRenderables;
		if(others == null) {
			others = new TreeSet(new ZIndexComparator());
			this.positionedRenderables = others;
		}
		others.add(new PositionedRenderable(renderable, verticalAlignable, this.otherOrdinal++, isFloat));
		renderable.setParent(this);
		if(renderable instanceof RUIControl) {
			this.container.addComponent(((RUIControl) renderable).widget.getComponent());
		}
	}

	private void importDelayedPair(DelayedPair pair) {
		BoundableRenderable r = pair.child;
		r.setOrigin(pair.x, pair.y);
		this.addPositionedRenderable(r, false, false);
	}
		
	public String toString() {
		return "RTable[this=" + System.identityHashCode(this) + ",node=" + this.modelNode + "]";
	}
	
	private static class LayoutKey {
	    public final int availWidth;
	    public final int availHeight;
	    public final int whitespace;
	    public final Font font;

	    public LayoutKey(int availWidth, int availHeight, int whitespace, Font font) {
	        super();
	        this.availWidth = availWidth;
	        this.availHeight = availHeight;
	        this.whitespace = whitespace;
	        this.font = font;
	    }

	    public boolean equals(Object obj) {
	        if(obj == this) {
	            return true;
	        }
	        if(!(obj instanceof LayoutKey)) {
	            return false;
	        }
	        LayoutKey other = (LayoutKey) obj;
	        return other.availWidth == this.availWidth &&
	        other.availHeight == this.availHeight &&
	        other.whitespace == this.whitespace &&
	        Objects.equals(other.font, this.font);
	    }
	    
	    public int hashCode() {
	        Font font = this.font;
	        return (this.availWidth * 1000 + this.availHeight) ^ (font == null ? 0 : font.hashCode()); 
	    }
	}

	private static class LayoutValue {
	    public final int width;
	    public final int height;

	    public LayoutValue(int width, int height) {
	        this.width = width;
	        this.height = height;
	    }
	}

}
