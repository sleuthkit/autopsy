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
 * Created on Apr 16, 2005
 */
package org.lobobrowser.html.renderer;

import java.util.*;
import java.util.logging.*;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import java.awt.image.ImageObserver;

import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.html.style.*;
import org.lobobrowser.util.*;
import org.w3c.dom.Node;

/**
 * Represents a HTML block in a rendered document, typically
 * a DIV. The root renderer node is of this type as well.
 * <p>
 * Immediately below an <code>RBlock</code> you will find a node of
 * type {@link RBlockViewport}.
 */
public class RBlock extends BaseElementRenderable implements
		RenderableContainer, ImageObserver {
	protected static final Logger logger = Logger.getLogger(RBlock.class.getName());
	private static final boolean loggableInfo = logger.isLoggable(Level.INFO);
	private static final int MAX_CACHE_SIZE = 10;
	
	protected final FrameContext frameContext;
	protected final int listNesting;
	protected final HtmlRendererContext rendererContext;
	protected final RBlockViewport bodyLayout;
	protected final Map cachedLayout = new Hashtable(5);
	
	protected RenderableSpot startSelection;
	protected RenderableSpot endSelection;
	protected JScrollBar vScrollBar;
	protected JScrollBar hScrollBar;
	protected boolean hasHScrollBar = false;
	protected boolean hasVScrollBar = false;

	// Validation-dependent variables...
	// private Dimension layoutSize = null;
	
    protected int defaultOverflowX = RenderState.OVERFLOW_NONE;
    protected int defaultOverflowY = RenderState.OVERFLOW_NONE;
    
    private LayoutValue lastLayoutValue = null;
    private LayoutKey lastLayoutKey = null;

	public RBlock(NodeImpl modelNode, int listNesting,
			UserAgentContext pcontext, HtmlRendererContext rcontext,
			FrameContext frameContext, RenderableContainer parentContainer) {
		super(parentContainer, modelNode, pcontext);
		this.listNesting = listNesting;
		this.frameContext = frameContext;
		this.rendererContext = rcontext;
		RBlockViewport bl = new RBlockViewport(modelNode, this, this
				.getViewportListNesting(listNesting), pcontext, rcontext,
				frameContext, this);
		this.bodyLayout = bl;
		bl.setOriginalParent(this);
		// Initialize origin of RBlockViewport to be as far top-left as
		// possible.
		// This will be corrected on first layout.
		bl.setX(Short.MAX_VALUE);
		bl.setY(Short.MAX_VALUE);
	}
	
	/**
	 * Gets the width the vertical scrollbar has when shown.
	 */
	public int getVScrollBarWidth() {
		return SCROLL_BAR_THICKNESS;
	}

	public void finalize() throws Throwable {
		super.finalize();
	}

	public int getVAlign() {
		// Not used
		return VALIGN_BASELINE;
	}

	public void ensureVisible(Point point) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			boolean hscroll = this.hasHScrollBar;
			boolean vscroll = this.hasVScrollBar;
			int origX = bodyLayout.x;
			int origY = bodyLayout.y;
			Insets insets = this.getInsets(hscroll, vscroll);
			if (hscroll) {
				if (point.x < insets.left) {
					bodyLayout.x += (insets.left - point.x);
				} else if (point.x > this.width - insets.right) {
					bodyLayout.x -= (point.x - this.width + insets.right);
				}
			}
			if (vscroll) {
				if (point.y < insets.top) {
					bodyLayout.y += (insets.top - point.y);
				} else if (point.y > this.height - insets.bottom) {
					bodyLayout.y -= (point.y - this.height + insets.bottom);
				}
			}
			if (hscroll || vscroll) {
				this.correctViewportOrigin(insets, this.width, this.height);
				if (origX != bodyLayout.x || origY != bodyLayout.y) {
					this.resetScrollBars(null);
					// TODO: This could be paintImmediately.
					this.repaint();
				}
			}
		}
	}

	private JScrollBar getHScrollBar() {
		JScrollBar sb = this.hScrollBar;
		if (sb == null) {
			// Should never go back to null
			sb = new JScrollBar(JScrollBar.HORIZONTAL);
			sb.addAdjustmentListener(new LocalAdjustmentListener(
					JScrollBar.HORIZONTAL));
			this.hScrollBar = sb;
		}
		return sb;
	}

	private JScrollBar getVScrollBar() {
		JScrollBar sb = this.vScrollBar;
		if (sb == null) {
			// Should never go back to null
			sb = new JScrollBar(JScrollBar.VERTICAL);
			sb.addAdjustmentListener(new LocalAdjustmentListener(
					JScrollBar.VERTICAL));
			this.vScrollBar = sb;
		}
		return sb;
	}

//	public final boolean couldBeScrollable() {
//		int overflow = this.getOverflow();
//		return overflow != OVERFLOW_NONE
//				&& (overflow == OVERFLOW_SCROLL
//						|| overflow == OVERFLOW_VERTICAL || overflow == OVERFLOW_AUTO);
//	}
//
	public final boolean isOverflowVisibleX() {
		int overflow = this.overflowX;
		return overflow == RenderState.OVERFLOW_NONE || overflow == RenderState.OVERFLOW_VISIBLE;
	}

    public final boolean isOverflowVisibleY() {
        int overflow = this.overflowY;
        return overflow == RenderState.OVERFLOW_NONE || overflow == RenderState.OVERFLOW_VISIBLE;
    }

	public int getFirstLineHeight() {
		return this.bodyLayout.getFirstLineHeight();
	}

	public int getFirstBaselineOffset() {
		return this.bodyLayout.getFirstBaselineOffset();
	}

	public void setSelectionEnd(RenderableSpot rpoint) {
		this.endSelection = rpoint;
	}

	public void setSelectionStart(RenderableSpot rpoint) {
		this.startSelection = rpoint;
	}

	public int getViewportListNesting(int blockNesting) {
		return blockNesting;
	}

	public void paint(Graphics g) {
		RenderState rs = this.modelNode.getRenderState();
		if (rs != null && rs.getVisibility() != RenderState.VISIBILITY_VISIBLE) {
			// Just don't paint it.
			return;
		}
		boolean linfo = loggableInfo;
		long time1 = linfo ? System.currentTimeMillis() : 0;
		this.prePaint(g);
		long time2 = linfo ? System.currentTimeMillis() : 0;
		long time3 = 0;
		try {
			Insets insets = this.getInsets(this.hasHScrollBar,
					this.hasVScrollBar);
			RBlockViewport bodyLayout = this.bodyLayout;
			if (bodyLayout != null) {
				int overflowX = this.overflowX;
				int overflowY = this.overflowY;
				if ((overflowX == RenderState.OVERFLOW_NONE || overflowX == RenderState.OVERFLOW_VISIBLE) && (overflowY == RenderState.OVERFLOW_NONE || overflowY == RenderState.OVERFLOW_VISIBLE)) {
					// Simply translate.
					int bx = bodyLayout.x;
					int by = bodyLayout.y;
					g.translate(bx, by);
					try {
						bodyLayout.paint(g);
					} finally {
						g.translate(-bx, -by);
					}
				} else {
					// Clip when there potential scrolling or hidden overflow
					// was requested.
					Graphics newG = g.create(insets.left, insets.top,
							this.width - insets.left - insets.right,
							this.height - insets.top - insets.bottom);
					try {
						// Second, translate
						newG.translate(bodyLayout.x - insets.left, bodyLayout.y
								- insets.top);
						// Third, paint in clipped + translated region.
						bodyLayout.paint(newG);
					} finally {
						newG.dispose();
					}
				}

				if (linfo) {
					time3 = System.currentTimeMillis();
				}
			} else {
				// nop
			}

			// Paint FrameContext selection.
			// This is only done by root RBlock.

			RenderableSpot start = this.startSelection;
			RenderableSpot end = this.endSelection;
			boolean inSelection = false;
			if (start != null && end != null && !start.equals(end)) {
				this.paintSelection(g, inSelection, start, end);
			}
			// Must paint scrollbars too.
			JScrollBar hsb = this.hScrollBar;
			if (hsb != null) {
				Graphics sbg = g.create(insets.left, this.height
						- insets.bottom, this.width - insets.left
						- insets.right, SCROLL_BAR_THICKNESS);
				try {
					hsb.paint(sbg);
				} finally {
					sbg.dispose();
				}
			}
			JScrollBar vsb = this.vScrollBar;
			if (vsb != null) {
				Graphics sbg = g.create(this.width - insets.right, insets.top,
						SCROLL_BAR_THICKNESS, this.height - insets.top
								- insets.bottom);
				try {
					vsb.paint(sbg);
				} finally {
					sbg.dispose();
				}
			}

		} finally {
			// Must always call super implementation
			super.paint(g);
		}
		if (linfo) {
			long time4 = System.currentTimeMillis();
			if (time4 - time1 > 100) {
				logger.info("paint(): Elapsed: " + (time4 - time1)
						+ " ms. Prepaint: " + (time2 - time1)
						+ " ms. Viewport: " + (time3 - time2) + " ms. RBlock: "
						+ this + ".");
			}
		}
	}

	public final void layout(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight,
			int defaultOverflowX, int defaultOverflowY, boolean sizeOnly) {
		this.layout(availWidth, availHeight, expandWidth, expandHeight, null, defaultOverflowX, defaultOverflowY, sizeOnly);
	}

	public final void layout(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight,
			FloatingBoundsSource floatBoundsSource, boolean sizeOnly) {
		this.layout(availWidth, availHeight, expandWidth, expandHeight, floatBoundsSource, 
				this.defaultOverflowX, this.defaultOverflowY, sizeOnly);
	}

	public final void layout(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight,
			FloatingBoundsSource floatBoundsSource, int defaultOverflowX, int defaultOverflowY, boolean sizeOnly) {
		try {
			this.doLayout(availWidth, availHeight, expandWidth, expandHeight, floatBoundsSource,
					defaultOverflowX, defaultOverflowY, sizeOnly);
		} finally {
			this.layoutUpTreeCanBeInvalidated = true;
			this.layoutDeepCanBeInvalidated = true;
			// this.renderStyleCanBeInvalidated = true;
		}
	}
	
	public final void doLayout(int availWidth, int availHeight, boolean sizeOnly) {
	    // This is an override of an abstract method.
		this.doLayout(availWidth, availHeight, true, false, null, this.defaultOverflowX, this.defaultOverflowY, sizeOnly);
	}

	/**
	 * Lays out and sets dimensions only if RBlock is invalid (or never before
	 * layed out), if the parameters passed differ from the last layout, or if
	 * the current font differs from the font for the last layout.
	 * 
	 * @param availWidth
	 * @param availHeight
	 */
	public void doLayout(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight,
			FloatingBoundsSource floatBoundsSource, int defaultOverflowX, int defaultOverflowY, boolean sizeOnly) {
		// Expected to be invoked in the GUI thread.
		RenderState renderState = this.modelNode.getRenderState();
		Font font = renderState == null ? null : renderState.getFont();
		int whiteSpace = renderState == null ? RenderState.WS_NORMAL : renderState.getWhiteSpace();
		LayoutKey key = new LayoutKey(availWidth, availHeight, expandWidth, expandHeight, floatBoundsSource, defaultOverflowX, defaultOverflowY, whiteSpace, font);
		Map cachedLayout = this.cachedLayout;
		LayoutValue value;
		if(sizeOnly) {
		    value = (LayoutValue) cachedLayout.get(key);
		}
		else {
		    if(Objects.equals(key, this.lastLayoutKey)) {
		        value = this.lastLayoutValue;
		    }
		    else {
		        value = null;
		    }
		}
		if(value == null) {
			value = this.forceLayout(renderState, availWidth, availHeight, 
			        expandWidth, expandHeight,
					floatBoundsSource, defaultOverflowX, defaultOverflowY, sizeOnly);
			if(sizeOnly) {
	            this.lastLayoutKey = null;
	            this.lastLayoutValue = null;
			    if(cachedLayout.size() > MAX_CACHE_SIZE) {
			        // Unlikely, but we should keep it bounded.
			        cachedLayout.clear();
			    }
			    cachedLayout.put(key, value);
			}
			else {
                this.lastLayoutKey = key;
                this.lastLayoutValue = value;			    
			}
		}
		this.width = value.width;
		this.height = value.height;
		this.hasHScrollBar = value.hasHScrollBar;
		this.hasVScrollBar = value.hasVScrollBar;

		// Even if we didn't do layout, the parent is
		// expected to have removed its GUI components.
		this.sendGUIComponentsToParent();

		// Even if we didn't do layout, the parent is
		// expected to have removed its delayed pairs.
		this.sendDelayedPairsToParent();
	}

	private final boolean correctViewportOrigin(Insets insets, int blockWidth,
			int blockHeight) {
		RBlockViewport bodyLayout = this.bodyLayout;
		int viewPortX = bodyLayout.x;
		int viewPortY = bodyLayout.y;
		boolean corrected = false;
		if (viewPortX > insets.left) {
			bodyLayout.x = insets.left;
			corrected = true;
		} else if (viewPortX < blockWidth - insets.right - bodyLayout.width) {
			bodyLayout.x = Math.min(insets.left, blockWidth - insets.right
					- bodyLayout.width);
			corrected = true;
		}
		if (viewPortY > insets.top) {
			bodyLayout.y = insets.top;
			corrected = true;
		} else if (viewPortY < blockHeight - insets.bottom - bodyLayout.height) {
			bodyLayout.y = Math.min(insets.top, blockHeight - insets.bottom
					- bodyLayout.height);
			corrected = true;
		}
		return corrected;
	}

	/**
	 * Lays out the block without checking for prior dimensions.
	 * 
	 * @param declaredWidth The declared width of the block.
	 * @param declaredHeight The declared height of the block.
	 * @param tentativeWidth  Presumed width of the whole block (with margins).
	 * @param tentativeHeight 
	 * @return
	 */
	private final LayoutValue forceLayout(RenderState renderState, int availWidth, int availHeight,
			boolean expandWidth, boolean expandHeight, FloatingBoundsSource blockFloatBoundsSource, int defaultOverflowX, int defaultOverflowY, boolean sizeOnly) {
		// Expected to be invoked in the GUI thread.
		// TODO: Not necessary to do full layout if only expandWidth or
		// expandHeight change (specifically in tables).	    
		RenderState rs = renderState;
		if (rs == null) {
			rs = new BlockRenderState(null);
		}
		
//		// Clear adjust() cache.
//		this.cachedAdjust.clear();
		
		// We reprocess the rendering state. 
		// Probably doesn't need to be done in its entirety every time.
		this.applyStyle(availWidth, availHeight);		

		RBlockViewport bodyLayout = this.bodyLayout;
		NodeImpl node = (NodeImpl) this.modelNode;
		if (node == null || bodyLayout == null) {
		    Insets insets = this.getInsets(false, false);
		    return new LayoutValue(insets.left + insets.right, insets.bottom + insets.top, false, false);
		}

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
        int paddingTotalWidth = paddingInsets.left + paddingInsets.right;
        int paddingTotalHeight = paddingInsets.top + paddingInsets.bottom;

        int overflowX = this.overflowX;
        if (overflowX == RenderState.OVERFLOW_NONE) {
            overflowX = defaultOverflowX;
        }
        int overflowY = this.overflowY;
        if (overflowY == RenderState.OVERFLOW_NONE) {
            overflowY = defaultOverflowY;
        }        
        boolean vauto = overflowY == RenderState.OVERFLOW_AUTO;
        boolean hscroll = overflowX == RenderState.OVERFLOW_SCROLL;
        boolean hauto = overflowX == RenderState.OVERFLOW_AUTO;
        boolean vscroll = overflowY == RenderState.OVERFLOW_SCROLL;

        Insets insets = this.getInsets(hscroll, vscroll);
        int insetsTotalWidth = insets.left + insets.right;
        int insetsTotalHeight = insets.top + insets.bottom;
		int actualAvailWidth = availWidth - paddingTotalWidth - insetsTotalWidth; 
        int actualAvailHeight = availWidth - paddingTotalHeight - insetsTotalHeight;
		Integer dw = this.getDeclaredWidth(renderState, actualAvailWidth);
		Integer dh = this.getDeclaredHeight(renderState, actualAvailHeight);
		int declaredWidth = -1;
		int declaredHeight = -1;
		if (dw != null) {
		    declaredWidth = dw.intValue();
		}
		if (dh != null) {
		    declaredHeight = dh.intValue();
		}

		// Remove all GUI components previously added by descendents
		// The RBlockViewport.layout() method is expected to add all of them
		// back.
		this.clearGUIComponents();

		
        int tentativeWidth;
        int tentativeHeight;

        // Step # 1: If there's no declared width and no width
        // expansion has been requested, do a preliminary layout
        // assuming that the scrollable region has width=0 and
        // there's no wrapping.
        tentativeWidth = declaredWidth == -1 ? availWidth : declaredWidth + insetsTotalWidth + paddingTotalWidth; 
        tentativeHeight = declaredHeight == -1 ? availHeight : declaredHeight + insetsTotalHeight + paddingTotalHeight;                  
        if(declaredWidth == -1 && !expandWidth && availWidth > insetsTotalWidth + paddingTotalWidth) {
            RenderThreadState state = RenderThreadState.getState();
            boolean prevOverrideNoWrap = state.overrideNoWrap;
            if(!prevOverrideNoWrap) {
                state.overrideNoWrap = true;
                try {
                    int desiredViewportWidth = paddingTotalWidth;
                    int desiredViewportHeight = paddingTotalHeight;
                    bodyLayout.layout(desiredViewportWidth, desiredViewportHeight,
                            paddingInsets, -1, null, true);
                    // If we find that the viewport is not as wide as we
                    // presumed, then we'll use that as a new tentative width.
                    if(bodyLayout.width + insetsTotalWidth < tentativeWidth) {
                        tentativeWidth = bodyLayout.width + insetsTotalWidth;
                        tentativeHeight = bodyLayout.height + insetsTotalHeight;
                    }
                } finally {
                    state.overrideNoWrap = false;
                }
            }
        }

        // Step # 2: Do a layout with the tentativeWidth (adjusted if Step # 1 was done),
        // but in case overflow-y is "auto", then we check for possible overflow.
        FloatingBounds viewportFloatBounds = null;
        FloatingBounds blockFloatBounds = null;
        if (blockFloatBoundsSource != null) {
            blockFloatBounds = blockFloatBoundsSource.getChildBlockFloatingBounds(tentativeWidth);
            viewportFloatBounds = new ShiftedFloatingBounds(
                    blockFloatBounds, -insets.left, -insets.right,
                    -insets.top);
        }
        int desiredViewportWidth = tentativeWidth - insetsTotalWidth;
        int desiredViewportHeight = tentativeHeight - insets.top - insets.bottom;
        int maxY = vauto ? (declaredHeight == -1 ? -1 : declaredHeight + paddingInsets.top) : -1;
        boolean needToAddVScroll = false;
        try {
            bodyLayout.layout(desiredViewportWidth, desiredViewportHeight,
                    paddingInsets, maxY, viewportFloatBounds, sizeOnly);
        } catch(SizeExceededException see) {
            // Getting this exception means that we need to add a vertical scrollbar.
            // Wee need to relayout and adjust insets and widths for scrollbar.
            vscroll = true;
            insets = this.getInsets(hscroll, vscroll);
            insetsTotalWidth = insets.left + insets.right;
            actualAvailWidth = availWidth - paddingTotalWidth - insetsTotalWidth; 
            dw = this.getDeclaredWidth(renderState, actualAvailWidth);
            declaredWidth = dw == null ? -1 : dw.intValue();
            desiredViewportWidth = tentativeWidth - insetsTotalWidth;
            if (blockFloatBounds != null) {
                viewportFloatBounds = new ShiftedFloatingBounds(
                        blockFloatBounds, -insets.left, -insets.right,
                        -insets.top);
            }
            bodyLayout.layout(desiredViewportWidth, desiredViewportHeight,
                    paddingInsets, -1, viewportFloatBounds, sizeOnly);
        }
                
        int bodyWidth = bodyLayout.width;
        int bodyHeight = bodyLayout.height;
        int prelimBlockWidth = bodyWidth + insetsTotalWidth;
        int prelimBlockHeight = bodyHeight + insetsTotalHeight;
        int adjDeclaredWidth = declaredWidth == -1 ? -1 : declaredWidth + insets.left + insets.right + paddingInsets.left + paddingInsets.right; 
        int adjDeclaredHeight = declaredHeight == -1 ? -1 : declaredHeight + insets.top + insets.bottom + paddingInsets.top + paddingInsets.bottom; 

        // Adjust insets and other dimensions base on overflow-y=auto.
        if (hauto && ((adjDeclaredWidth != -1 && prelimBlockWidth > adjDeclaredWidth) || (prelimBlockWidth > tentativeWidth))) {
            hscroll = true;
            insets = this.getInsets(hscroll, vscroll);
            insetsTotalHeight = insets.top + insets.bottom;
            prelimBlockHeight = bodyHeight + insetsTotalHeight;
        }

        boolean visibleX = overflowX == RenderState.OVERFLOW_VISIBLE || overflowX == RenderState.OVERFLOW_NONE;
        boolean visibleY = overflowY == RenderState.OVERFLOW_VISIBLE || overflowY == RenderState.OVERFLOW_NONE;
        int resultingWidth;
        int resultingHeight;
        if (adjDeclaredWidth == -1) {
            resultingWidth = expandWidth ? Math.max(prelimBlockWidth, tentativeWidth) : prelimBlockWidth;
            if (hscroll && resultingWidth > tentativeWidth) {
                resultingWidth = Math.max(tentativeWidth, SCROLL_BAR_THICKNESS);
            }
        } else {
            resultingWidth = visibleX ? Math.max(prelimBlockWidth,
                    adjDeclaredWidth) : adjDeclaredWidth;
        }
        if(!sizeOnly) {
            // Align horizontally now. This may change canvas height.
            int alignmentXPercent = rs.getAlignXPercent();
            if (alignmentXPercent > 0) {
                // TODO: OPTIMIZATION: alignment should not be done in table cell
                // sizing determination.
                int canvasWidth = Math.max(bodyLayout.width, resultingWidth
                        - insets.left - insets.right);
                // Alignment is done afterwards because canvas dimensions might have
                // changed.
                bodyLayout.alignX(alignmentXPercent, canvasWidth, paddingInsets);
            }
        }
        if (adjDeclaredHeight == -1) {
            resultingHeight = expandHeight ? Math.max(prelimBlockHeight, tentativeHeight) : prelimBlockHeight;
            if (vscroll && resultingHeight > tentativeHeight) {
                resultingHeight = Math.max(tentativeHeight,
                        SCROLL_BAR_THICKNESS);
            }
        } else {
            resultingHeight = visibleY ? Math.max(prelimBlockHeight,
                    adjDeclaredHeight) : adjDeclaredHeight;
        }
        if(!sizeOnly) {
            // Align vertically now
            int alignmentYPercent = rs.getAlignYPercent();
            if (alignmentYPercent > 0) {
                // TODO: OPTIMIZATION: alignment should not be done in table cell
                // sizing determination.
                int canvasHeight = Math.max(bodyLayout.height, resultingHeight
                        - insets.top - insets.bottom);
                // Alignment is done afterwards because canvas dimensions might have
                // changed.
                bodyLayout.alignY(alignmentYPercent, canvasHeight, paddingInsets);
            }
        }
        if (vscroll) {
            JScrollBar sb = this.getVScrollBar();
            this.addComponent(sb);
            // Bounds set by updateWidgetBounds
        }
        if (hscroll) {
            JScrollBar sb = this.getHScrollBar();
            this.addComponent(sb);
            // Bounds set by updateWidgetBounds
        }

        if (hscroll || vscroll) {
            // In this case, viewport origin should not be reset.
            // We don't want to cause the document to scroll back
            // up while rendering.
            this.correctViewportOrigin(insets, resultingWidth, resultingHeight);
            // Now reset the scrollbar state. Depends 
            // on block width and height.
            this.width = resultingWidth;
            this.height = resultingHeight;
            this.resetScrollBars(rs);
        } else {
            bodyLayout.x = insets.left;
            bodyLayout.y = insets.top;
        }

		return new LayoutValue(resultingWidth, resultingHeight, hscroll, vscroll);
	}

//	/**
//	 * Adjustment step which must be done after layout. This will expand blocks
//	 * that need to be expanded and relayout blocks with relative sizes
//	 * accordingly.
//	 * 
//	 * @param availWidth
//	 * @param availHeight
//	 * @param expandWidth
//	 * @param expandHeight
//	 */
//	public void adjust(int availWidth, int availHeight, boolean expandWidth,
//			boolean expandHeight, FloatingBoundsSource floatBoundsSource, boolean useDeclaredSize) {
//        RenderState renderState = this.modelNode.getRenderState();
//        Font font = renderState == null ? null : renderState.getFont();
//        int whiteSpace = renderState == null ? RenderState.WS_NORMAL : renderState.getWhiteSpace();
//        int tentativeWidth;
//        if(useDeclaredSize && floatBoundsSource != null) {
//            Integer declaredWidth = this.getDeclaredWidth(renderState, availWidth);
//            Insets insets = this.getInsets(this.hasHScrollBar, this.hasVScrollBar); 
//            Insets paddingInsets = this.paddingInsets;
//            int hinsets = insets.left + insets.right + (paddingInsets == null ? 0 : paddingInsets.left + paddingInsets.right); 
//            tentativeWidth = declaredWidth == null ? availWidth : declaredWidth.intValue() + hinsets;
//        }
//        else {
//            // Assuming that we don't care about this if
//            // floatBoundsSource == null.
//            tentativeWidth = availWidth;
//        }
//        FloatingBounds blockFloatBounds = floatBoundsSource == null ? null : floatBoundsSource.getChildBlockFloatingBounds(tentativeWidth);
//	    LayoutKey layoutKey = new LayoutKey(availWidth, availHeight, blockFloatBounds, this.defaultOverflowX, this.defaultOverflowY, whiteSpace, font, expandWidth, expandHeight, useDeclaredSize);
//	    LayoutValue layoutValue = (LayoutValue) this.cachedAdjust.get(layoutKey);
//		// Expected to be invoked in the GUI thread.
//		if (layoutValue == null) {
//			layoutValue = this.forceAdjust(renderState, availWidth, availHeight,
//					expandWidth, expandHeight, blockFloatBounds, this.defaultOverflowX, this.defaultOverflowY, useDeclaredSize);
//			this.cachedAdjust.put(layoutKey, layoutValue);			
//		}
//
//		// We send GUI components up in adjust() in case new ones were added.
//		this.sendGUIComponentsToParent();
//		// No sending delayed pairs here.
//		this.width = layoutValue.width;
//		this.height = layoutValue.height;
//		this.hasHScrollBar = layoutValue.hasHScrollBar;
//		this.hasVScrollBar = layoutValue.hasVScrollBar;
//	}
//
//	/**
//	 * This adjustment step needs to be performed after layout. In this case,
//	 * the dimensions previously obtained in the layout are assumed to be the
//	 * desired dimensions of the block.
//	 * <p>
//	 * When we first layout a block, we don't know its final width and height. 
//	 * It could be wider or narrower than originally assumed.
//	 * Consider elements embedded in the block that have widths and heights
//	 * specified by a percentage. 
//	 */
//	public void adjust() {
//		// Expected to be invoked in the GUI thread.
//	    this.adjust(this.width, this.height, true, true, null, false);
//	}
//
//	/**
//	 * 
//	 * @param renderState
//	 * @param tentativeWidth
//	 *            The tentative or max width that will be tried.
//	 * @param tentativeHeight
//	 *            The tentative or max height that will be tried.
//	 * @param adjDeclaredWidth
//	 *            The declared width plus margins.
//	 * @param adjDeclaredHeight
//	 *            The declared height plus margins.
//	 * @param floatBounds
//	 *            Float bounds that need to be passed to the viewport.
//	 * @param defaultOverflow
//	 */
//	private final LayoutValue forceAdjust(RenderState renderState, int availWidth, int availHeight, 
//			boolean expandWidth, boolean expandHeight,
//			FloatingBounds blockFloatBounds, int defaultOverflowX, int defaultOverflowY, boolean useDeclaredSize) {
//		// Expected to be invoked in the GUI thread.
//		RenderState rs = renderState;
//		if (rs == null) {
//			rs = new BlockRenderState(null);
//		}
//		RBlockViewport bodyLayout = this.bodyLayout;
//		NodeImpl node = (NodeImpl) this.modelNode;
//		if (node == null || bodyLayout == null) {
//			Insets insets = this.getInsets(false, false);
//			return new LayoutValue(insets.left + insets.right, insets.bottom + insets.top, false, false);
//		}
//
//		// No clearing of GUI components here
//
//        int overflowX = this.overflowX;
//        if (overflowX == RenderState.OVERFLOW_NONE) {
//            overflowX = defaultOverflowX;
//        }
//        int overflowY = this.overflowY;
//        if (overflowY == RenderState.OVERFLOW_NONE) {
//            overflowY = defaultOverflowY;
//        }
//        boolean autoY = overflowY == RenderState.OVERFLOW_AUTO;
//        boolean hscroll = overflowX == RenderState.OVERFLOW_SCROLL;
//        boolean hauto = overflowX == RenderState.OVERFLOW_AUTO;
//        boolean vscroll = overflowY == RenderState.OVERFLOW_SCROLL;
//		Insets paddingInsets = this.paddingInsets;
//		if (paddingInsets == null) {
//			paddingInsets = RBlockViewport.ZERO_INSETS;
//		}
//        Insets borderInsets = this.borderInsets;
//        if(borderInsets == null) {
//            borderInsets = RBlockViewport.ZERO_INSETS;
//        }
//        Insets marginInsets = this.marginInsets;
//        if(marginInsets == null) {
//            marginInsets = RBlockViewport.ZERO_INSETS;
//        }
//        
//        // Calculate presumed size of block.
//        int tentativeWidth;
//        int tentativeHeight;
//        int declaredWidth = -1;
//        int declaredHeight = -1;
//        if(useDeclaredSize) {
//            Integer dw = this.getDeclaredWidth(renderState, availWidth);
//            Integer dh = this.getDeclaredHeight(renderState, availHeight);
//            if (dw != null) {
//                declaredWidth = dw.intValue();
//            }
//            if (dh != null) {
//                declaredHeight = dh.intValue();
//            }
//        }
//        if(declaredWidth == -1) {
//            tentativeWidth = availWidth;            
//        }
//        else {
//            tentativeWidth = declaredWidth + paddingInsets.left + paddingInsets.right + borderInsets.left + borderInsets.right + marginInsets.left + marginInsets.right; 
//        }
//        if(declaredHeight == -1) {
//            tentativeHeight = availHeight;
//        }
//        else {
//            tentativeHeight = declaredHeight + paddingInsets.top + paddingInsets.bottom + borderInsets.top + borderInsets.bottom + marginInsets.top + marginInsets.bottom;
//        }
//		Insets insets = null;
//		for (int tries = (autoY ? 0 : 1); tries < 2; tries++) {
//			try {
//				insets = this.getInsets(hscroll, vscroll);
//				int desiredViewportWidth = tentativeWidth - insets.left
//						- insets.right;
//				int desiredViewportHeight = tentativeHeight - insets.top
//						- insets.bottom;
//				FloatingBounds viewportFloatBounds = null;
//				if (blockFloatBounds != null) {
//					viewportFloatBounds = new ShiftedFloatingBounds(
//							blockFloatBounds, -insets.left, -insets.right,
//							-insets.top);
//				}
//				bodyLayout.adjust(desiredViewportWidth, desiredViewportHeight,
//						paddingInsets, viewportFloatBounds);
//				break;
//			} catch (SizeExceededException hee) {
//				if (tries != 0) {
//					throw new IllegalStateException("tries=" + tries + ",autoY="
//							+ autoY);
//				}
//				vscroll = true;
//			}
//		}
//		// Dimension size = bodyLayout.getSize();
//		// Dimension rblockSize = new Dimension(size.width + insets.left +
//		// insets.right, size.height + insets.top + insets.bottom);
//		int rblockWidth = bodyLayout.width + insets.left + insets.right;
//        int adjDeclaredWidth = declaredWidth == -1 ? -1 : declaredWidth + insets.left + insets.right + paddingInsets.left + paddingInsets.right; 
//        int adjDeclaredHeight = declaredHeight == -1 ? -1 : declaredHeight + insets.top + insets.bottom + paddingInsets.top + paddingInsets.bottom; 
//		if (hauto
//				&& !hscroll
//				&& ((adjDeclaredWidth != -1 && rblockWidth > adjDeclaredWidth) || (rblockWidth > tentativeWidth))) {
//			hscroll = true;
//			insets = this.getInsets(hscroll, vscroll);
//			rblockWidth = bodyLayout.width + insets.left + insets.right;
//		}
//		// Calculate resulting width.
//		boolean visibleX = overflowX == RenderState.OVERFLOW_VISIBLE || overflowX == RenderState.OVERFLOW_NONE; 
//        boolean visibleY = overflowY == RenderState.OVERFLOW_VISIBLE || overflowY == RenderState.OVERFLOW_NONE; 
//		int resultingWidth;
//		if (adjDeclaredWidth == -1) {
//			resultingWidth = rblockWidth;
//			if (hscroll && resultingWidth > tentativeWidth) {
//				resultingWidth = Math.max(tentativeWidth, SCROLL_BAR_THICKNESS);
//			} else if (expandWidth && resultingWidth < tentativeWidth) {
//				resultingWidth = tentativeWidth;
//			}
//		} else {
//			resultingWidth = visibleX ? Math.max(rblockWidth, adjDeclaredWidth)
//					: adjDeclaredWidth;
//		}
//		// Align horizontally now. This may change canvas height.
//		int alignmentXPercent = rs.getAlignXPercent();
//		if (alignmentXPercent > 0) {
//			// TODO: OPTIMIZATION: alignment should not be done in table cell
//			// sizing determination.
//			int canvasWidth = Math.max(bodyLayout.width, resultingWidth
//					- insets.left - insets.right);
//			// Alignment is done afterwards because canvas dimensions might have
//			// changed.
//			bodyLayout.alignX(alignmentXPercent, canvasWidth, paddingInsets);
//		}
//
//		int resultingHeight;
//		int rblockHeight = bodyLayout.height + insets.top + insets.bottom;
//		if (autoY
//				&& !vscroll
//				&& ((adjDeclaredHeight != -1 && rblockHeight > adjDeclaredHeight) || (rblockHeight > tentativeHeight))) {
//			vscroll = true;
//			insets = this.getInsets(hscroll, vscroll);
//			rblockHeight = bodyLayout.height + insets.top + insets.bottom;
//		}
//		if (adjDeclaredHeight == -1) {
//			resultingHeight = rblockHeight;
//			if (vscroll && resultingHeight > tentativeHeight) {
//				resultingHeight = Math.max(tentativeHeight,
//						SCROLL_BAR_THICKNESS);
//			} else if (expandHeight && resultingHeight < tentativeHeight) {
//				resultingHeight = tentativeHeight;
//			}
//		} else {
//			resultingHeight = visibleY ? Math.max(rblockHeight,
//					adjDeclaredHeight) : adjDeclaredHeight;
//		}
//
//		// Align vertically now
//		int alignmentYPercent = rs.getAlignYPercent();
//		if (alignmentYPercent > 0) {
//			// TODO: OPTIMIZATION: alignment should not be done in table cell
//			// sizing determination.
//			int canvasHeight = Math.max(bodyLayout.height, resultingHeight
//					- insets.top - insets.bottom);
//			// Alignment is done afterwards because canvas dimensions might have
//			// changed.
//			bodyLayout.alignY(alignmentYPercent, canvasHeight, paddingInsets);
//		}
//
//		if (vscroll) {
//			JScrollBar sb = this.getVScrollBar();
//			this.addComponent(sb);
//			// Bounds set by updateWidgetBounds
//		}
//		if (hscroll) {
//			JScrollBar sb = this.getHScrollBar();
//			this.addComponent(sb);
//			// Bounds set by updateWidgetBounds
//		}
//
//		if (hscroll || vscroll) {
//			// In this case, viewport origin should not be changed.
//			// We don't want to cause the document to scroll back
//			// up while rendering.
//			this.correctViewportOrigin(insets, resultingWidth, resultingHeight);
//			// Depends on width, height and origin
//			this.resetScrollBars(rs);
//		} else {
//			bodyLayout.x = insets.left;
//			bodyLayout.y = insets.top;
//		}
//		return new LayoutValue(resultingWidth, resultingHeight, hscroll, vscroll);
//	}

	private int getVUnitIncrement(RenderState renderState) {
		if (renderState != null) {
			return renderState.getFontMetrics().getHeight();
		} else {
			return new BlockRenderState(null).getFontMetrics().getHeight();
		}
	}

	private boolean resettingScrollBars = false;

	/**
	 * Changes scroll bar state to match viewport origin.
	 */
	private void resetScrollBars(RenderState renderState) {
		// Expected to be called only in the GUI thread.
		this.resettingScrollBars = true;
		try {
			RBlockViewport bodyLayout = this.bodyLayout;
			if (bodyLayout != null) {
				Insets insets = this.getInsets(this.hasHScrollBar,
						this.hasVScrollBar);
				JScrollBar vsb = this.vScrollBar;
				if (vsb != null) {
					int newValue = insets.top - bodyLayout.y;
					int newExtent = this.height - insets.top - insets.bottom;
					int newMin = 0;
					int newMax = bodyLayout.height;
					vsb.setValues(newValue, newExtent, newMin, newMax);
					vsb.setUnitIncrement(this.getVUnitIncrement(renderState));
					vsb.setBlockIncrement(newExtent);
				}
				JScrollBar hsb = this.hScrollBar;
				if (hsb != null) {
					int newValue = insets.left - bodyLayout.x;
					int newExtent = this.width - insets.left - insets.right;
					int newMin = 0;
					int newMax = bodyLayout.width;
					hsb.setValues(newValue, newExtent, newMin, newMax);
				}
			}
		} finally {
			this.resettingScrollBars = false;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.UIControl#paintSelection(java.awt.Graphics,
	 *      boolean, org.xamjwg.html.renderer.RenderablePoint,
	 *      org.xamjwg.html.renderer.RenderablePoint)
	 */
	public boolean paintSelection(Graphics g, boolean inSelection,
			RenderableSpot startPoint, RenderableSpot endPoint) {
		Graphics newG = g.create();
		try {
			Insets insets = this.getInsets(this.hasHScrollBar,
					this.hasVScrollBar);
			// Just clip, don't translate.
			newG.clipRect(insets.left, insets.top, this.width - insets.left
					- insets.right, this.height - insets.top - insets.bottom);
			return super
					.paintSelection(newG, inSelection, startPoint, endPoint);
		} finally {
			newG.dispose();
		}
		// boolean endSelectionLater = false;
		// if(inSelection) {
		// if(startPoint.renderable == this || endPoint.renderable == this) {
		// return false;
		// }
		// }
		// else {
		// if(startPoint.renderable == this || endPoint.renderable == this) {
		// // This can only occur if the selection point
		// // is on the margin or border or the block.
		// inSelection = true;
		// if(startPoint.renderable == this && endPoint.renderable == this) {
		// // Start and end selection points on margin or border.
		// endSelectionLater = true;
		// }
		// }
		// }
		// RBlockViewport bodyLayout = this.bodyLayout;
		// if(bodyLayout != null) {
		// Insets insets = this.getInsets(this.hasHScrollBar,
		// this.hasVScrollBar);
		// Graphics newG = g.create(insets.left, insets.top, this.width -
		// insets.left - insets.right, this.height - insets.top -
		// insets.bottom);
		// try {
		// newG.translate(bodyLayout.x - insets.left, bodyLayout.y -
		// insets.top);
		// boolean newInSelection = bodyLayout.paintSelection(newG, inSelection,
		// startPoint, endPoint);
		// if(endSelectionLater) {
		// return false;
		// }
		// return newInSelection;
		// } finally {
		// newG.dispose();
		// }
		// }
		// else {
		// return inSelection;
		// }
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.BoundableRenderable#getRenderablePoint(int,
	 *      int)
	 */
	public RenderableSpot getLowestRenderableSpot(int x, int y) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			Insets insets = this.getInsets(this.hasHScrollBar,
					this.hasVScrollBar);
			if (x > insets.left && x < this.width - insets.right
					&& y > insets.top && y < this.height - insets.bottom) {
				return bodyLayout.getLowestRenderableSpot(x - bodyLayout.x, y
						- bodyLayout.y);
			} else {
				return new RenderableSpot(this, x, y);
			}
		} else {
			return new RenderableSpot(this, x, y);
		}
	}

	/**
	 * RBlocks should only be invalidated if one of their properties change, or
	 * if a descendent changes, or if a style property of an ancestor is such
	 * that it could produce layout changes in this RBlock.
	 */
	public void invalidateLayoutLocal() {
		super.invalidateLayoutLocal();
		this.cachedLayout.clear();
		this.lastLayoutKey = null;
		this.lastLayoutValue = null;
		JScrollBar hScrollBar = this.hScrollBar;
		if(hScrollBar != null) {
		    // Necessary
		    hScrollBar.invalidate();
		}
		JScrollBar vScrollBar = this.vScrollBar;
		if(vScrollBar != null) {
            // Necessary
		    vScrollBar.invalidate();
		}
	}
	
	protected void clearStyle(boolean isRootBlock) {
	    super.clearStyle(isRootBlock);
	    this.overflowX = this.defaultOverflowX;
	    this.overflowY = this.defaultOverflowY;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseClick(java.awt.event.MouseEvent,
	 *      int, int)
	 */
	public boolean onMouseClick(MouseEvent event, int x, int y) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			if (!bodyLayout.onMouseClick(event, x - bodyLayout.x, y
					- bodyLayout.y)) {
				return false;
			}
		}
		if (!HtmlController.getInstance().onMouseClick(this.modelNode, event,
				x, y)) {
			return false;
		}
		if (this.backgroundColor != null) {
			return false;
		}
		return true;
	}

	public boolean onDoubleClick(MouseEvent event, int x, int y) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			if (!bodyLayout.onDoubleClick(event, x - bodyLayout.x, y
					- bodyLayout.y)) {
				return false;
			}
		}
		if (this.backgroundColor != null) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseDisarmed(java.awt.event.MouseEvent)
	 */
	public boolean onMouseDisarmed(MouseEvent event) {
		BoundableRenderable br = this.armedRenderable;
		if (br != null) {
			try {
				return br.onMouseDisarmed(event);
			} finally {
				this.armedRenderable = null;
			}
		} else {
			return true;
		}
	}

	private BoundableRenderable armedRenderable;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMousePressed(java.awt.event.MouseEvent,
	 *      int, int)
	 */
	public boolean onMousePressed(MouseEvent event, int x, int y) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			int newX = x - bodyLayout.x;
			int newY = y - bodyLayout.y;
			if (bodyLayout.contains(newX, newY)) {
				this.armedRenderable = bodyLayout;
				if (!bodyLayout.onMousePressed(event, newX, newY)) {
					return false;
				}
			} else {
				this.armedRenderable = null;
			}
		} else {
			this.armedRenderable = null;
		}
		if (!HtmlController.getInstance().onMouseDown(this.modelNode, event, x,
				y)) {
			return false;
		}
		if (this.backgroundColor != null) {
			return false;
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseReleased(java.awt.event.MouseEvent,
	 *      int, int)
	 */
	public boolean onMouseReleased(MouseEvent event, int x, int y) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			int newX = x - bodyLayout.x;
			int newY = y - bodyLayout.y;
			if (bodyLayout.contains(newX, newY)) {
				this.armedRenderable = null;
				if (!bodyLayout.onMouseReleased(event, newX, newY)) {
					return false;
				}
			} else {
				BoundableRenderable br = this.armedRenderable;
				if (br != null) {
					br.onMouseDisarmed(event);
				}
			}
		}
		if (!HtmlController.getInstance()
				.onMouseUp(this.modelNode, event, x, y)) {
			return false;
		}
		if (this.backgroundColor != null) {
			return false;
		}
		return true;
	}

	public Color getPaintedBackgroundColor() {
		return this.backgroundColor;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.renderer.RCollection#getRenderables()
	 */
	public Iterator getRenderables() {
		final RBlockViewport bodyLayout = this.bodyLayout;
		return new Iterator() {
			private RBlockViewport bl = bodyLayout;

			public boolean hasNext() {
				return bl != null;
			}

			public Object next() {
				if (bl == null) {
					throw new NoSuchElementException();
				}
				try {
					return bl;
				} finally {
					bl = null;
				}
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xamjwg.html.domimpl.ContainingBlockContext#repaint(org.xamjwg.html.domimpl.RenderableContext)
	 */
	public void repaint(ModelNode modelNode) {
		// this.invalidateRenderStyle();
		this.repaint();
	}

	// public boolean extractSelectionText(StringBuffer buffer, boolean
	// inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
	// RBlockViewport bodyLayout = this.bodyLayout;
	// if(bodyLayout != null) {
	// inSelection = inSelection ? endPoint.renderable != this :
	// startPoint.renderable == this;
	// return bodyLayout.extractSelectionText(buffer, inSelection, startPoint,
	// endPoint);
	// }
	// else {
	// return inSelection;
	// }
	// }

	public void updateWidgetBounds(int guiX, int guiY) {
		super.updateWidgetBounds(guiX, guiY);
		boolean hscroll = this.hasHScrollBar;
		boolean vscroll = this.hasVScrollBar;
		if (hscroll || vscroll) {
			Insets insets = this.getInsets(hscroll, vscroll);
			if (hscroll) {
				JScrollBar hsb = this.hScrollBar;
				if (hsb != null) {
					hsb.setBounds(guiX + insets.left, guiY + this.height
							- insets.bottom, this.width - insets.left
							- insets.right, SCROLL_BAR_THICKNESS);
				}
			}
			if (vscroll) {
				JScrollBar vsb = this.vScrollBar;
				if (vsb != null) {
					vsb.setBounds(guiX + this.width - insets.right, guiY
							+ insets.top, SCROLL_BAR_THICKNESS, this.height
							- insets.top - insets.bottom);
				}
			}
		}
	}

	public void scrollHorizontalTo(int newX) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			Insets insets = this.getInsets(this.hasHScrollBar,
					this.hasVScrollBar);
			int viewPortX = newX;
			if (viewPortX > insets.left) {
				bodyLayout.x = insets.left;
			} else if (viewPortX < this.width - insets.right - bodyLayout.width) {
				bodyLayout.x = Math.min(insets.left, this.width - insets.right
						- bodyLayout.width);
			} else {
				bodyLayout.x = viewPortX;
			}
			this.resetScrollBars(null);
			this.updateWidgetBounds();
			this.repaint();
		}
	}

	public void scrollVerticalTo(int newY) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			Insets insets = this.getInsets(this.hasHScrollBar,
					this.hasVScrollBar);
			int viewPortY = newY;
			if (viewPortY > insets.top) {
				bodyLayout.y = insets.top;
			} else if (viewPortY < this.height - insets.bottom
					- bodyLayout.height) {
				bodyLayout.y = Math.min(insets.top, this.height - insets.bottom
						- bodyLayout.height);
			} else {
				bodyLayout.y = viewPortY;
			}
			this.resetScrollBars(null);
			this.updateWidgetBounds();
			this.repaint();
		}
	}

	public void scrollByUnits(int orientation, int units) {
		int offset = orientation == JScrollBar.VERTICAL ? this
				.getVUnitIncrement(null)
				* units : units;
		this.scrollBy(orientation, offset);
	}

	public void scrollBy(int orientation, int offset) {
		RBlockViewport bodyLayout = this.bodyLayout;
		if (bodyLayout != null) {
			switch (orientation) {
			case JScrollBar.HORIZONTAL:
				this.scrollHorizontalTo(bodyLayout.x - offset);
				break;
			case JScrollBar.VERTICAL:
				this.scrollVerticalTo(bodyLayout.y - offset);
				break;
			}
		}
	}

	/**
	 * Scrolls the viewport's origin to the given location, or
	 * as close to it as possible.
	 * <p>
	 * This method should be invoked in the GUI thread.
	 * @param bounds The bounds of the scrollable area that should become visible.
	 * @param xIfNeeded If this parameter is <code>true</code> the x coordinate
	 *                  is changed only if the horizontal bounds are not currently visible.
	 * @param yIfNeeded If this parameter is <code>true</code> the y coordinate
	 *                  is changed only if the vertical bounds are not currently visible.
	 */
	public void scrollTo(Rectangle bounds, boolean xIfNeeded, boolean yIfNeeded) {
		boolean hscroll = this.hasHScrollBar;
		boolean vscroll = this.hasVScrollBar;
		if(hscroll || vscroll) {
			RBlockViewport bv = this.bodyLayout;
			Insets insets = this.getInsets(hscroll, vscroll);
			int vpheight = this.height - insets.top - insets.bottom;
			int vpwidth = this.width - insets.left - insets.right;
			int tentativeX = insets.left - bounds.x;
			int tentativeY = insets.top - bounds.y;
			boolean needCorrection = false;
			if(!(xIfNeeded && tentativeX <= bv.x && -tentativeX + bv.x + bounds.width <= vpwidth)) {
				bv.setX(tentativeX);
				needCorrection = true;
			}
			if(!(yIfNeeded && tentativeY <= bv.y && -tentativeY + bv.y + bounds.height <= vpheight)) {
				bv.setY(tentativeY);
				needCorrection = true;
			}
			if(needCorrection) {
				this.correctViewportOrigin(insets, this.width, this.height);
			}
		}		
	}

	private void scrollToSBValue(int orientation, int value) {
		Insets insets = this.getInsets(this.hasHScrollBar, this.hasVScrollBar);
		switch (orientation) {
		case JScrollBar.HORIZONTAL:
			int xOrigin = insets.left - value;
			this.scrollHorizontalTo(xOrigin);
			break;
		case JScrollBar.VERTICAL:
			int yOrigin = insets.top - value;
			this.scrollVerticalTo(yOrigin);
			break;
		}
	}

	public RBlockViewport getRBlockViewport() {
		return this.bodyLayout;
	}

	public boolean extractSelectionText(StringBuffer buffer, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
		boolean result = super.extractSelectionText(buffer, inSelection, startPoint, endPoint);
		String br = System.getProperty("line.separator");
		if(inSelection) {
			buffer.insert(0, br);
		}
		if(result) {
			buffer.append(br);
		}
		return result;
	}

	public String toString() {
		return "RBlock[node=" + this.modelNode + "]";
	}

//	public FloatingBounds getExportableFloatingBounds() {
//		RBlockViewport viewport = this.bodyLayout;
//		FloatingBounds viewportBounds = viewport.getExportableFloatingBounds();
//		if (viewportBounds == null) {
//			return null;
//		}
//		Insets insets = this.getInsets(this.hasHScrollBar, this.hasVScrollBar);
//		return new ShiftedFloatingBounds(viewportBounds, insets.left,
//				insets.right, viewport.y);
//	}
	
	public FloatingInfo getExportableFloatingInfo() {
	    FloatingInfo info = this.bodyLayout.getExportableFloatingInfo();
	    if(info == null) {
	        return null;
	    }
        Insets insets = this.getInsets(this.hasHScrollBar, this.hasVScrollBar);
        return new FloatingInfo(info.shiftX + insets.left, info.shiftY + insets.top, info.floats);
	}

	private class LocalAdjustmentListener implements AdjustmentListener {
		private final int orientation;

		public LocalAdjustmentListener(int orientation) {
			this.orientation = orientation;
		}

		public void adjustmentValueChanged(AdjustmentEvent e) {
			if (RBlock.this.resettingScrollBars) {
				return;
			}
			switch (e.getAdjustmentType()) {
			case AdjustmentEvent.UNIT_INCREMENT:
				// fall through
			case AdjustmentEvent.UNIT_DECREMENT:
				// fall through
			case AdjustmentEvent.BLOCK_INCREMENT:
				// fall through
			case AdjustmentEvent.BLOCK_DECREMENT:
				// fall through
			case AdjustmentEvent.TRACK: {
				int value = e.getValue();
				RBlock.this.scrollToSBValue(this.orientation, value);
				break;
			}
			}
		}
	}

	private static class BodyFilter implements NodeFilter {
		public boolean accept(Node node) {
			return node instanceof org.w3c.dom.html2.HTMLBodyElement;
		}
	}

    public int getDefaultOverflowX() {
        return defaultOverflowX;
    }

    public int getDefaultOverflowY() {
        return defaultOverflowY;
    }

    public void setDefaultOverflowX(int defaultOverflowX) {
        this.defaultOverflowX = defaultOverflowX;
    }

    public void setDefaultOverflowY(int defaultOverflowY) {
        this.defaultOverflowY = defaultOverflowY;
    }
    
    private static class LayoutKey {
        public final int availWidth;
        public final int availHeight;
        public final FloatingBoundsSource floatBoundsSource;
        public final int defaultOverflowX;
        public final int defaultOverflowY;
        public final int whitespace;
        public final Font font;
        public final boolean expandWidth;
        public final boolean expandHeight;
        public final boolean useDeclaredSize;
        
        public LayoutKey(int availWidth, int availHeight, boolean expandWidth, boolean expandHeight,
                FloatingBoundsSource floatBoundsSource, int defaultOverflowX,
                int defaultOverflowY, int whitespace, Font font) {
            super();
            this.availWidth = availWidth;
            this.availHeight = availHeight;
            this.floatBoundsSource = floatBoundsSource;
            this.defaultOverflowX = defaultOverflowX;
            this.defaultOverflowY = defaultOverflowY;
            this.whitespace = whitespace;
            this.font = font;
            this.expandWidth = expandWidth;
            this.expandHeight = expandHeight;
            this.useDeclaredSize = true;
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
            other.defaultOverflowX == this.defaultOverflowX &&
            other.defaultOverflowY == this.defaultOverflowY &&
            other.whitespace == this.whitespace &&
            other.expandWidth == this.expandWidth &&
            other.expandHeight == this.expandHeight &&
            other.useDeclaredSize == this.useDeclaredSize &&
            Objects.equals(other.font, this.font) &&
            Objects.equals(other.floatBoundsSource, this.floatBoundsSource);            
        }

        public int hashCode() {
            Font font = this.font;
            return (this.availWidth * 1000 + this.availHeight) ^ (font == null ? 0 : font.hashCode()) ^ (this.expandWidth ? 2 : 0) ^ (this.expandHeight ? 1 : 0); 
        }
    }
    
    private static class LayoutValue {
        public final int width;
        public final int height;
        public final boolean hasHScrollBar;
        public final boolean hasVScrollBar;
        
        public LayoutValue(int width, int height, boolean hasHScrollBar,
                boolean hasVScrollBar) {
            this.width = width;
            this.height = height;
            this.hasHScrollBar = hasHScrollBar;
            this.hasVScrollBar = hasVScrollBar;
        }
    }
}