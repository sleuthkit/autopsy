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
 * Created on Apr 17, 2005
 */
package org.lobobrowser.html.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.*;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.*;
import org.lobobrowser.html.style.RenderState;
import org.lobobrowser.util.Objects;

/**
 * @author J. H. S.
 */
class RUIControl extends BaseElementRenderable implements RElement {
    private static final int MAX_CACHE_SIZE = 10;
	public final UIControl widget;
	protected final ModelNode modelNode;
	private final FrameContext frameContext;
	
	public RUIControl(ModelNode me, UIControl widget, RenderableContainer container, FrameContext frameContext, UserAgentContext ucontext) {
		super(container, me, ucontext);
		this.modelNode = me;
		this.widget = widget;
		this.frameContext = frameContext;
		widget.setRUIControl(this);
	}
	
	public void focus() {
		super.focus();
		java.awt.Component c = this.widget.getComponent();
		c.requestFocus();
	}
	
	public final void invalidateLayoutLocal() {
		// Invalidate widget (some redundancy)
		super.invalidateLayoutLocal();
		this.widget.invalidate();
		// Invalidate cached values
		this.cachedLayout.clear();
		this.lastLayoutKey = null;
		this.lastLayoutValue = null;
	}
	
	public int getVAlign() {
		return this.widget.getVAlign();
	}

	public boolean hasBackground() {
		return this.backgroundColor != null || this.backgroundImage != null || this.lastBackgroundImageUri != null;
	}
	
	public final void paint(Graphics g) {
		RenderState rs = this.modelNode.getRenderState();
		if(rs != null && rs.getVisibility() != RenderState.VISIBILITY_VISIBLE) {
			// Just don't paint it.
			return;
		}
		// Prepaint borders, background images, etc.
		this.prePaint(g);		
		// We need to paint the GUI component.
		// For various reasons, we need to do that
		// instead of letting AWT do it.
		Insets insets = this.getInsets(false, false);
		g.translate(insets.left, insets.top);
		try {
			this.widget.paint(g);
		} finally {
			g.translate(-insets.left, -insets.top);
		}
	}
	
	public boolean onMouseClick(java.awt.event.MouseEvent event, int x, int y) {
		ModelNode me = this.modelNode;
		if(me != null) {
			return HtmlController.getInstance().onMouseClick(me, event, x, y);
		}
		else {
			return true;
		}
	}

	public boolean onDoubleClick(java.awt.event.MouseEvent event, int x, int y) {
		ModelNode me = this.modelNode;
		if(me != null) {
			return HtmlController.getInstance().onDoubleClick(me, event, x, y);
		}
		else {
			return true;
		}
	}

	public boolean onMousePressed(java.awt.event.MouseEvent event, int x, int y) {
		ModelNode me = this.modelNode;
		if(me != null) {
			return HtmlController.getInstance().onMouseDown(me, event, x, y);
		}
		else {
			return true;
		}
	}

	public boolean onMouseReleased(java.awt.event.MouseEvent event, int x, int y) {
		ModelNode me = this.modelNode;
		if(me != null) {
			return HtmlController.getInstance().onMouseUp(me, event, x, y);
		}
		else {
			return true;
		}
	}
	
	public boolean onMouseDisarmed(java.awt.event.MouseEvent event) {
		ModelNode me = this.modelNode;
		if(me != null) {
			return HtmlController.getInstance().onMouseDisarmed(me, event);
		}
		else {
			return true;
		}
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#invalidateState(org.xamjwg.html.renderer.RenderableContext)
	 */
	public void invalidateRenderStyle() {
		//NOP - No RenderStyle below this node.
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.domimpl.ContainingBlockContext#repaint(org.xamjwg.html.renderer.RenderableContext)
	 */
	public void repaint(ModelNode modelNode) {
		Object widget = this.widget;
		if(widget instanceof UINode) {
			((UINode) widget).repaint(modelNode);
		}
		else {
			this.repaint();
		}
	}
	
	public void updateWidgetBounds(int guiX, int guiY) {
		// Overrides
		super.updateWidgetBounds(guiX, guiY);
		Insets insets = this.getInsets(false, false);
		this.widget.setBounds(guiX + insets.left, guiY + insets.top, this.width - insets.left - insets.right, this.height - insets.top - insets.bottom);
	}
	
	public Color getBlockBackgroundColor() {
		return this.widget.getBackgroundColor();
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#paintSelection(java.awt.Graphics, boolean, org.xamjwg.html.renderer.RenderablePoint, org.xamjwg.html.renderer.RenderablePoint)
	 */
	public boolean paintSelection(Graphics g, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
		inSelection = super.paintSelection(g, inSelection, startPoint, endPoint);
		if(inSelection) {
			Color over = new Color(0, 0, 255, 50);
			if(over != null) {
				Color oldColor = g.getColor();
				try {
					g.setColor(over);
					g.fillRect(0, 0, this.width, this.height);
				} finally {
					g.setColor(oldColor);
				}				
			}
		}
		return inSelection;
	}

	public boolean extractSelectionText(StringBuffer buffer, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
		// No text here
		return inSelection;
	}
	
	public RenderableSpot getLowestRenderableSpot(int x, int y) {
		// Nothing draggable - return self
		return new RenderableSpot(this, x, y);
	}

	private int declaredWidth = -1;
	private int declaredHeight = -1;
	private LayoutKey lastLayoutKey = null;
	private LayoutValue lastLayoutValue = null;
	private final Map cachedLayout = new HashMap(5);	
	
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
            if(Objects.equals(this.lastLayoutKey, layoutKey)) {
                layoutValue = this.lastLayoutValue;
            }
            else {
                layoutValue = null;
            }
        }
        if(layoutValue == null) {
			this.applyStyle(availWidth, availHeight);			
			RenderState renderState = this.modelNode.getRenderState();
	        Insets paddingInsets = this.paddingInsets;
	        if (paddingInsets == null) {
	            paddingInsets = RBlockViewport.ZERO_INSETS;
	        }
	        Insets borderInsets = this.borderInsets;
	        if(borderInsets == null) {
	            borderInsets = RBlockViewport.ZERO_INSETS;
	        }
	        Insets marginInsets = this.marginInsets;
	        if(marginInsets == null) {
	            marginInsets = RBlockViewport.ZERO_INSETS;
	        }
            int actualAvailWidth = availWidth - paddingInsets.left - paddingInsets.right - borderInsets.left - borderInsets.right - marginInsets.left - marginInsets.right;
            int actualAvailHeight = availHeight - paddingInsets.top - paddingInsets.bottom - borderInsets.top - borderInsets.bottom - marginInsets.top - marginInsets.bottom;            
			Integer dw = this.getDeclaredWidth(renderState, actualAvailWidth);
			Integer dh = this.getDeclaredHeight(renderState, actualAvailHeight);
			int declaredWidth = dw == null ? -1 : dw.intValue();
			int declaredHeight = dh == null ? -1 : dh.intValue();
			this.declaredWidth = declaredWidth;
			this.declaredHeight = declaredHeight;
			
			UIControl widget = this.widget;
			widget.reset(availWidth, availHeight);
			Insets insets = this.getInsets(false, false);
			int finalWidth = declaredWidth == -1 ? -1 : declaredWidth - insets.left - insets.right;
			int finalHeight = declaredHeight == -1 ? -1 : declaredHeight - insets.top - insets.bottom;
			if(finalWidth == -1 || finalHeight == -1) {
				Dimension size = widget.getPreferredSize();
				if(finalWidth == -1) {
					finalWidth = size.width + insets.left + insets.right;
				}
				if(finalHeight == -1) {
					finalHeight = size.height + insets.top + insets.bottom;
				}
			}
			layoutValue = new LayoutValue(finalWidth, finalHeight);
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
	}

	/** 
	 * May be called by controls when
	 * they wish to modifiy their preferred
	 * size (e.g. an image after it's loaded). 
	 * This method must be called
	 * in the GUI thread.
	 */
	public final void preferredSizeInvalidated() {
		int dw = RUIControl.this.declaredWidth;
		int dh = RUIControl.this.declaredHeight;
		if(dw == -1 || dh == -1) {
			this.frameContext.delayedRelayout((NodeImpl) this.modelNode);
		}
		else {
			RUIControl.this.repaint();
		}
	}
	
	public Iterator getRenderables() {
		// No children for GUI controls
		return null;
	}

	public Color getPaintedBackgroundColor() {
		return this.container.getPaintedBackgroundColor();
	}
	
	public Color getForegroundColor() {
		RenderState rs = this.modelNode.getRenderState();
		return rs == null ? null : rs.getColor();
	}
	
	private static class LayoutKey {
	    public final int availWidth;
	    public final int availHeight;
	    public final int whitespace;
	    public final Font font;

	    public LayoutKey(int availWidth, int availHeight, int whitespace, Font font) {
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
