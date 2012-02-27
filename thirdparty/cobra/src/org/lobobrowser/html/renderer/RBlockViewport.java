/*    GNU LESSER GENERAL PUBLIC LICENSE
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

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;

import org.lobobrowser.html.*;
import org.lobobrowser.html.style.*;
import org.lobobrowser.html.domimpl.*;
import org.w3c.dom.*;
import java.util.logging.*;

/**
 * A substantial portion of the HTML rendering logic of the package can
 * be found in this class.
 * This class is in charge of laying out the DOM subtree of a node,
 * usually on behalf of an RBlock.
 * It creates a renderer subtree consisting of RLine's or RBlock's. RLine's in
 * turn contain RWord's and so on.
 * This class also happens to be used as an RBlock scrollable viewport.
 * @author J. H. S.
 */
public class RBlockViewport extends BaseRCollection {
	// GENERAL NOTES
    // An RBlockViewport basically consists of two collections:
    // seqRenderables and positionedRenderables. The seqRenderables
    // collection is a sequential list of RLine's and RBlock's
    // that is amenable to a binary search by Y position. The
    // positionedRenderables collection is a z-index ordered 
    // collection meant for blocks with position=absolute and such.
    //
    // HOW FLOATS WORK
    // Float boxes are scheduled to be added on the next available line.
    // Line layout is bounded by the current floatBounds. 
    // When a float is placed with placeFloat(), an absolutely positioned
    // box is added. Whether the float height expands the RBlockViewport
    // height is determined by isFloatLimit().
    // 
    // FloatingBounds are inherited by sub-boxes, but the bounds are 
    // shifted.
    // 
    // The RBlockViewport also publishes a collection of "exporatable
    // floating bounds." These are float boxes that go beyond the bounds
    // of the RBlockViewport, so ancestor blocks can obtain them to adjust
    // their own bounds.
	
	public static final Insets ZERO_INSETS = new Insets(0, 0, 0, 0);
	private static final Logger logger = Logger.getLogger(RBlockViewport.class.getName());
	
	//private final ArrayList awtComponents = new ArrayList();
	private final RenderableContainer container;
	private final int listNesting;
	private final UserAgentContext userAgentContext;
	private final HtmlRendererContext rendererContext;
	private final FrameContext frameContext;
	
	private SortedSet positionedRenderables;
	private ArrayList seqRenderables = null;
	private ArrayList exportableFloats = null;
//	private Collection exportedRenderables;
	private RLine currentLine;
	private int maxX;
	private int maxY;
	//private int availHeight;
	private int desiredWidth; // includes insets
	private int desiredHeight; // includes insets
	private int availContentHeight; // does not include insets
	private int availContentWidth; // does not include insets
	private int yLimit; 
	private int positionedOrdinal;
	private int currentCollapsibleMargin;
	private Insets paddingInsets;
	private boolean overrideNoWrap;
	private FloatingBounds floatBounds = null;
	private boolean sizeOnly;
	private BoundableRenderable lastSeqBlock;
	
	private static final Map elementLayout = new HashMap(70);
	private static final MarkupLayout miscLayout = new MiscLayout();
	
	static {
		Map el = elementLayout;
		EmLayout em = new EmLayout();
		el.put("I", em);
		el.put("EM", em);
		el.put("CITE", em);
		el.put("H1", new HLayout(24));
		el.put("H2", new HLayout(18));
		el.put("H3", new HLayout(15));
		el.put("H4", new HLayout(12));
		el.put("H5", new HLayout(10));
		el.put("H6", new HLayout(8));
		StrongLayout strong = new StrongLayout();
		el.put("B", strong);
		el.put("STRONG", strong);
		el.put("TH", strong);
		el.put("U", new ULayout());
		el.put("STRIKE", new StrikeLayout());
		el.put("BR", new BrLayout());
		el.put("P", new PLayout());
		el.put("NOSCRIPT", new NoScriptLayout());
		NopLayout nop = new NopLayout();
		el.put("SCRIPT", nop);
		el.put("HEAD", nop);
		el.put("TITLE", nop);
		el.put("META", nop);
		el.put("STYLE", nop);
		el.put("LINK", nop);
		el.put("IMG", new ImgLayout());
		el.put("TABLE", new TableLayout());
		ChildrenLayout children = new ChildrenLayout();
		el.put("HTML", children);
		AnchorLayout anchor = new AnchorLayout();
		el.put("A", anchor);
		el.put("ANCHOR", anchor);
		el.put("INPUT", new InputLayout2());
		el.put("TEXTAREA", new TextAreaLayout2());
		el.put("SELECT", new SelectLayout());
		ListItemLayout list = new ListItemLayout();
		el.put("UL", list);
		el.put("OL", list);
		el.put("LI", list);
		CommonBlockLayout cbl = new CommonBlockLayout();		
		el.put("PRE", cbl);
		el.put("CENTER", cbl);
		el.put("CAPTION", cbl);
		DivLayout div = new DivLayout();
		el.put("DIV", div);
		el.put("BODY", div);
		el.put("DL", div);
		el.put("DT", div);
		BlockQuoteLayout bq = new BlockQuoteLayout();
		el.put("BLOCKQUOTE", bq);
		el.put("DD", bq);
		el.put("HR", new HrLayout());
		el.put("SPAN", new SpanLayout());
		ObjectLayout ol = new ObjectLayout(false, true);
		el.put("OBJECT", new ObjectLayout(true, true));
		el.put("APPLET", ol);
		el.put("EMBED", ol);
		el.put("IFRAME", new IFrameLayout());
	}
	
	/**
	 * Constructs an HtmlBlockLayout.
	 * @param container This is usually going to be an RBlock.
	 * @param listNesting The nesting level for lists. This is zero except inside a list.
	 * @param pcontext The HTMLParserContext instance.
	 * @param frameContext This is usually going to be HtmlBlock, an object where text selections are contained.
	 * @param parent This is usually going to be the parent of <code>container</code>.
	 */
	public RBlockViewport(ModelNode modelNode, RenderableContainer container, int listNesting, UserAgentContext pcontext, HtmlRendererContext rcontext, FrameContext frameContext, RCollection parent) {
		super(container, modelNode);
		this.parent = parent;
		this.userAgentContext = pcontext;
		this.rendererContext = rcontext;
		this.frameContext = frameContext;
		this.container = container;
		this.listNesting = listNesting;
		// Layout here can always be "invalidated"
		this.layoutUpTreeCanBeInvalidated = true;		
	}
	
	public void invalidateLayoutLocal() {	
		// Workaround for fact that RBlockViewport does not 
		// get validated or invalidated.
		this.layoutUpTreeCanBeInvalidated = true;
	}
	
	public int getAvailContentWidth() {
		return this.availContentWidth;
	}
	
	private int initCollapsibleMargin() {
	    Object parent = this.parent;
	    if(!(parent instanceof RBlock)) {
	        return 0;
	    }
	    RBlock parentBlock = (RBlock) parent;
	    return parentBlock.getCollapsibleMarginTop();
	}
	
	/**
	 * Builds the layout/renderer tree from scratch.
	 * Note: Returned dimension needs to be actual size needed for rendered content,
	 * not the available container size. This is relied upon by table layout.
	 * @param yLimit If other than -1, <code>layout</code> will throw <code>SizeExceededException</code>
	 * in the event that the layout goes beyond this y-coordinate point.
	 */
	public void layout(int desiredWidth, int desiredHeight, Insets paddingInsets, int yLimit, FloatingBounds floatBounds, boolean sizeOnly) {
		// Expected in GUI thread. It's possible it may be invoked during pack()
		// outside of the GUI thread.
		if(!EventQueue.isDispatchThread() && logger.isLoggable(Level.INFO)) {
			logger.warning("layout(): Invoked outside GUI dispatch thread.");
		}
		RenderableContainer container = this.container;	
		this.paddingInsets = paddingInsets;
		this.yLimit = yLimit;
		this.desiredHeight = desiredHeight;
		this.desiredWidth = desiredWidth;
		this.floatBounds = floatBounds;
		this.isFloatLimit = null;
		this.pendingFloats = null;
		this.sizeOnly = sizeOnly;
        this.lastSeqBlock = null;
		this.currentCollapsibleMargin = this.initCollapsibleMargin();

		// maxX and maxY should not be reset by layoutPass.
		this.maxX = paddingInsets.left;
		this.maxY = paddingInsets.top;

		int availw = desiredWidth - paddingInsets.left - paddingInsets.right;
		if(availw < 0) {
			availw = 0;
		}
		int availh = desiredHeight - paddingInsets.top - paddingInsets.bottom;
		if(availh == 0) {
			availh = 0;
		}
		this.availContentHeight = availh;
		this.availContentWidth = availw;

		// New floating algorithm.
		this.layoutPass((NodeImpl) this.modelNode);
		
		Collection delayedPairs = container.getDelayedPairs();
		if(delayedPairs != null && delayedPairs.size() > 0) {
			// Add positioned renderables that belong here
			Iterator i = delayedPairs.iterator();
			while(i.hasNext()) {
				DelayedPair pair = (DelayedPair) i.next();
				if(pair.targetParent == container) {
					this.importDelayedPair(pair);
				}				
			}
		}		

		// Compute maxY according to last block.
		int maxY = this.maxY;
		int maxYWholeBlock = maxY;
        BoundableRenderable lastSeqBlock = this.lastSeqBlock;
        if(lastSeqBlock != null) {
            int effBlockHeight = this.getEffectiveBlockHeight(lastSeqBlock);
            if(lastSeqBlock.getY() + effBlockHeight > maxY) {
                this.maxY = maxY = lastSeqBlock.getY() + effBlockHeight;
                maxYWholeBlock = lastSeqBlock.getY() + lastSeqBlock.getHeight(); 
            }
        }
        
        // See if line should increase maxY. Empty
        // lines shouldn't, except in cases where
        // there was a BR.
		RLine lastLine = this.currentLine;
		Rectangle lastBounds = lastLine.getBounds();
		if(lastBounds.height > 0 || lastBounds.y > maxYWholeBlock) {
		    int lastTopX = lastBounds.x + lastBounds.width;
		    if(lastTopX > this.maxX) {
		        this.maxX = lastTopX;
		    }
		    int lastTopY = lastBounds.y + lastBounds.height;
		    if(lastTopY > maxY) {
		        this.maxY = maxY = lastTopY;
		    }
		}

	    // Check positioned renderables for maxX and maxY
        SortedSet posRenderables = this.positionedRenderables;
        if(posRenderables != null) {
            boolean isFloatLimit = this.isFloatLimit();
            Iterator i = posRenderables.iterator();
            while(i.hasNext()) {
                PositionedRenderable pr = (PositionedRenderable) i.next();
                BoundableRenderable br = pr.renderable;
                if(br.getX() + br.getWidth() > this.maxX) {
                    this.maxX = br.getX() + br.getWidth();
                }
                if(isFloatLimit || !pr.isFloat) {
                    if(br.getY() + br.getHeight() > maxY) {
                        this.maxY = maxY = br.getY() + br.getHeight();
                    }
                }
            }
        }
		
		this.width = paddingInsets.right + this.maxX;
		this.height = paddingInsets.bottom + maxY;
	}

	private void layoutPass(NodeImpl rootNode) {
		RenderableContainer container = this.container;	
		container.clearDelayedPairs();
		this.positionedOrdinal = 0;

		// Remove sequential renderables...
		this.seqRenderables = null;

		// Remove other renderables... 
		this.positionedRenderables = null;
		
		// Remove exporatable floats...
		this.exportableFloats = null;
		
		// Call addLine after setting margins
		this.currentLine = this.addLine(rootNode, null, this.paddingInsets.top);
		
		// Start laying out...
		// The parent is expected to have set the RenderState already.
		this.layoutChildren(rootNode);		
		
		// This adds last-line floats.
		this.lineDone(this.currentLine);
	}
	
	/**
	 * Applies any horizonal aLignment. It may adjust height if necessary.
	 * @param canvasWidth The new width of the viewport. It could be
	 *                    different to the previously calculated width.
	 * @param paddingInsets
	 */
	public void alignX(int alignXPercent, int canvasWidth, Insets paddingInsets) {
		int prevMaxY = this.maxY;
		// Horizontal alignment
		if(alignXPercent > 0) {
			ArrayList renderables = this.seqRenderables;
			if(renderables != null) {
				Insets insets = this.paddingInsets;
				FloatingBounds floatBounds = this.floatBounds;
				int numRenderables = renderables.size();
				int yoffset = 0; // This may get adjusted due to blocks and floats.
				for(int i = 0; i < numRenderables; i++) {
					Object r = renderables.get(i);
					if(r instanceof BoundableRenderable) {
						BoundableRenderable seqRenderable = (BoundableRenderable) r;
						int y = seqRenderable.getY();
						int newY;
						if(yoffset > 0) {
							newY = y + yoffset;
							seqRenderable.setY(newY);
							if(newY + seqRenderable.getHeight() > this.maxY) {
								this.maxY = newY + seqRenderable.getHeight();
							}
						}
						else {
							newY = y;
						}
						boolean isVisibleBlock = seqRenderable instanceof RBlock && ((RBlock) seqRenderable).isOverflowVisibleX();
						int leftOffset = isVisibleBlock ? insets.left : this.fetchLeftOffset(y);
						int rightOffset = isVisibleBlock ? insets.right : this.fetchRightOffset(y);
						int actualAvailWidth = canvasWidth - leftOffset - rightOffset;
						int difference = actualAvailWidth - seqRenderable.getWidth();
						if(difference > 0) {
						    // The difference check means that only
						    // blocks with a declared width would get adjusted?
							int shift = (difference * alignXPercent) / 100;
//							if(floatBounds != null && isVisibleBlock) {
//								RBlock block = (RBlock) seqRenderable;
//								// Block needs to layed out again. Contents need
//								// to shift because of float. 
//					            final int expectedWidth = availContentWidth;
//					            final int blockShiftRight = insets.right;
//					            final int newX = leftOffset;
//					            FloatingBoundsSource floatBoundsSource = new ParentFloatingBoundsSource(blockShiftRight, expectedWidth, newX, newY, floatBounds);         
//					            block.layout(actualAvailWidth, this.availContentHeight, true, false, floatBoundsSource);
//							}
							if(!isVisibleBlock) {
	                            int newX = leftOffset + shift;
	                            seqRenderable.setX(newX);							    
							}
						}
					}
				}
			}
		}		
		if(prevMaxY != this.maxY) {
			this.height += (this.maxY - prevMaxY);
		}
	}

	/**
	 * Applies vertical alignment.
	 * @param canvasHeight
	 * @param paddingInsets
	 */
	public void alignY(int alignYPercent, int canvasHeight, Insets paddingInsets) {
		int prevMaxY = this.maxY;
		if(alignYPercent > 0) {
			int availContentHeight = canvasHeight - paddingInsets.top - paddingInsets.bottom;
			int usedHeight = this.maxY - paddingInsets.top;
			int difference = availContentHeight - usedHeight;
			if(difference > 0) {
				int shift = (difference * alignYPercent) / 100;
				ArrayList rlist = this.seqRenderables;
				if(rlist != null) {
					// Try sequential renderables first.
					Iterator renderables = rlist.iterator();
					while(renderables.hasNext()) {
						Object r = renderables.next();
						if(r instanceof BoundableRenderable) {
							BoundableRenderable line = (BoundableRenderable) r;
							int newY = line.getY() + shift;
							line.setY(newY);
							if(newY + line.getHeight() > this.maxY) {
								this.maxY = newY + line.getHeight();
							}
						}
					}
				}
				
				// Now other renderables, but only those that can be
				// vertically aligned
				Set others = this.positionedRenderables;
				if(others != null) {
					Iterator i2 = others.iterator();
					while(i2.hasNext()) {
						PositionedRenderable pr = (PositionedRenderable) i2.next();
						if(pr.verticalAlignable) {
							BoundableRenderable br = pr.renderable;
							int newY = br.getY() + shift;
							br.setY(newY);
							if(newY + br.getHeight() > this.maxY) {
								this.maxY = newY + br.getHeight();
							}
						}
					}
				}
			}
		}
		if(prevMaxY != this.maxY) {
			this.height += (this.maxY - prevMaxY);
		}
	}
	
//	/**
//	 * 
//	 * @param block A block needing readjustment due to horizontal alignment.
//	 * @return 
//	 */
//	private int readjustBlock(RBlock block, final int newX, final int newY, final FloatingBounds floatBounds) {
//		final int rightInsets = this.paddingInsets.right;
//		final int expectedWidth = this.desiredWidth - rightInsets - newX;
//		final int blockShiftRight = rightInsets;
//		final int prevHeight = block.height;
//		FloatingBoundsSource floatBoundsSource = new FloatingBoundsSource() {
//			public FloatingBounds getChildBlockFloatingBounds(int apparentBlockWidth) {
//				int actualRightShift = blockShiftRight + (expectedWidth - apparentBlockWidth);		
//				return new ShiftedFloatingBounds(floatBounds, -newX, -actualRightShift, -newY);
//			}
//		};
//		block.adjust(expectedWidth, this.availContentHeight, true, false, floatBoundsSource, true);
//		return block.height - prevHeight;
//	}
//
	private RLine addLine(ModelNode startNode, RLine prevLine, int newLineY) {
		// lineDone must be called before we try to
		// get float bounds.
		this.lineDone(prevLine);
		this.checkY(newLineY);
		int leftOffset = this.fetchLeftOffset(newLineY);
		int newX = leftOffset;
		int newMaxWidth = this.desiredWidth - this.fetchRightOffset(newLineY) - leftOffset;
		RLine rline;
		boolean initialAllowOverflow;
		if(prevLine == null) {
			// Note: Assumes that prevLine == null means it's the first line.
			RenderState rs = this.modelNode.getRenderState();
            initialAllowOverflow = rs == null ? false : rs.getWhiteSpace() == RenderState.WS_NOWRAP;
			// Text indentation only applies to the first line in the block.
			int textIndent = rs == null ? 0 : rs.getTextIndent(this.availContentWidth);
			if(textIndent != 0) {
				newX += textIndent;
				// Line width also changes!
				newMaxWidth += (leftOffset - newX);
			}
		}
		else {
		    int prevLineHeight = prevLine.getHeight();
		    if(prevLineHeight > 0) {
		        this.currentCollapsibleMargin = 0;
		    }
		    initialAllowOverflow = prevLine.isAllowOverflow();
			if(prevLine.x + prevLine.width > this.maxX) {
				this.maxX = prevLine.x + prevLine.width;
			}
		}
		rline = new RLine(startNode, this.container, newX, newLineY, newMaxWidth, 0, initialAllowOverflow);
		rline.setParent(this);
		ArrayList sr = this.seqRenderables;
		if(sr == null) {
			sr = new ArrayList(1);
			this.seqRenderables = sr;
		}
		sr.add(rline);
		this.currentLine = rline;
		return rline;
	}
	
	private void layoutMarkup(NodeImpl node) {
		// This is the "inline" layout of an element.
		// The difference with layoutChildren is that this
		// method checks for padding and margin insets.
		RenderState rs = node.getRenderState();
		Insets marginInsets = null;
		Insets paddingInsets = null;
		if(rs != null) {
		    HtmlInsets mi = rs.getMarginInsets();
			marginInsets = mi == null ? null : mi.getSimpleAWTInsets(this.availContentWidth, this.availContentHeight); 
            HtmlInsets pi = rs.getPaddingInsets();
			paddingInsets = pi == null ? null : pi.getSimpleAWTInsets(this.availContentWidth, this.availContentHeight);
		}
		int leftSpacing = 0;
		int rightSpacing = 0;
		if(marginInsets != null) {
			leftSpacing += marginInsets.left;
			rightSpacing += marginInsets.right;
		}
		if(paddingInsets != null) {
			leftSpacing += paddingInsets.left;
			rightSpacing += paddingInsets.right;
		}
		if(leftSpacing > 0) {
			RLine line = this.currentLine;
			line.addSpacing(new RSpacing(node, this.container, leftSpacing, line.height));
		}
		this.layoutChildren( node);
		if(rightSpacing > 0) {
			RLine line = this.currentLine;
			line.addSpacing(new RSpacing(node, this.container, rightSpacing, line.height));
		}
	}

	private void layoutChildren(NodeImpl node) {
		NodeImpl[] childrenArray = node.getChildrenArray();
		if(childrenArray != null) {
			int length = childrenArray.length;
			for(int i = 0; i < length; i++) {
				NodeImpl child = childrenArray[i];
				short nodeType = child.getNodeType();
				if(nodeType == Node.TEXT_NODE) {
					this.layoutText( child);					
				}
				else if(nodeType == Node.ELEMENT_NODE) {
					// Note that scanning for node bounds (anchor location)
					// depends on there being a style changer for inline elements.
					this.currentLine.addStyleChanger(new RStyleChanger(child));
					String nodeName = child.getNodeName().toUpperCase();
					MarkupLayout ml = (MarkupLayout) elementLayout.get(nodeName);
					if(ml == null) {
						ml = miscLayout;
					}
					ml.layoutMarkup(this, (HTMLElementImpl) child);
					this.currentLine.addStyleChanger(new RStyleChanger(node));
				}
				else if(nodeType == Node.COMMENT_NODE || nodeType == Node.PROCESSING_INSTRUCTION_NODE) {
					// ignore
				}
				else {
					throw new IllegalStateException("Unknown node: " + child);
				}
			}
		}
	}

	private final void positionRBlock(HTMLElementImpl markupElement, RBlock renderable) {
        if(!this.addElsewhereIfPositioned( renderable, markupElement, false, true, false)) {
            int availContentHeight = this.availContentHeight;
            RLine line = this.currentLine;
            // Inform line done before layout so floats are considered.
            this.lineDone(line);
            Insets paddingInsets = this.paddingInsets;
            int newLineY = line == null ? paddingInsets.top : line.y + line.height;
            //int leftOffset = this.fetchLeftOffset(newLineY);
            //int rightOffset = this.fetchRightOffset(newLineY);
            // Float offsets are ignored with block.
            int availContentWidth = this.availContentWidth;
            final int expectedWidth = availContentWidth;
            final int blockShiftRight = paddingInsets.right;
            final int newX = paddingInsets.left;
            final int newY = newLineY;
            FloatingBounds floatBounds = this.floatBounds;
            FloatingBoundsSource floatBoundsSource = floatBounds == null ? null : new ParentFloatingBoundsSource(blockShiftRight, expectedWidth, newX, newY, floatBounds);         
            renderable.layout(availContentWidth, availContentHeight, true, false, floatBoundsSource, this.sizeOnly);
            this.addAsSeqBlock(renderable, false, false, false, false);
            // Calculate new floating bounds after block has been put in place.
            FloatingInfo floatingInfo = renderable.getExportableFloatingInfo();
            if(floatingInfo != null) {
                this.importFloatingInfo(floatingInfo, renderable);
            }
            // Now add line, after float is set. 
            this.addLineAfterBlock(renderable, false);
        }
	}
	
	private final void positionRElement(HTMLElementImpl markupElement, RElement renderable, boolean usesAlignAttribute, boolean obeysFloats, boolean alignCenterAttribute) {
		if(!this.addElsewhereIfPositioned( renderable, markupElement, usesAlignAttribute, true, true)) {
			int availContentWidth = this.availContentWidth;
			int availContentHeight = this.availContentHeight;
			RLine line = this.currentLine;
			// Inform line done before layout so floats are considered.
			this.lineDone(line);
			if(obeysFloats) {
				int newLineY = line == null ? this.paddingInsets.top : line.y + line.height;
				int leftOffset = this.fetchLeftOffset(newLineY);
				int rightOffset = this.fetchRightOffset(newLineY);
				availContentWidth = this.desiredWidth - leftOffset - rightOffset;
			}
			renderable.layout(availContentWidth, availContentHeight, this.sizeOnly);
			boolean centerBlock = false;
			if(alignCenterAttribute) {
			    String align = markupElement.getAttribute("align");
			    centerBlock = align != null && align.equalsIgnoreCase("center");
			}
			this.addAsSeqBlock(renderable, obeysFloats, false, true, centerBlock);
		}
	}

	private final void layoutRBlock(HTMLElementImpl markupElement) {
		RBlock renderable = (RBlock) markupElement.getUINode();
		if(renderable == null) {
			renderable = new RBlock(markupElement, this.listNesting, this.userAgentContext, this.rendererContext, this.frameContext, this.container);
			markupElement.setUINode(renderable);
		}
		renderable.setOriginalParent(this);
		this.positionRBlock( markupElement, renderable);
	}

	private final void layoutRTable(HTMLElementImpl markupElement) {
		RElement renderable = (RElement) markupElement.getUINode();
		if(renderable == null) {
			renderable = new RTable(markupElement, this.userAgentContext, this.rendererContext, this.frameContext, container);
			markupElement.setUINode((UINode) renderable);
		}
		renderable.setOriginalParent(this);
		this.positionRElement(markupElement, renderable, markupElement instanceof HTMLTableElementImpl, true, true);
	}

	private final void layoutListItem(HTMLElementImpl markupElement) {
		RListItem renderable = (RListItem) markupElement.getUINode();
		if(renderable == null) {
			renderable = new RListItem(markupElement, this.listNesting, this.userAgentContext, this.rendererContext, this.frameContext, this.container, null);
			markupElement.setUINode(renderable);
		}
		renderable.setOriginalParent(this);
		this.positionRBlock( markupElement, renderable);		
	}

	private final void layoutList(HTMLElementImpl markupElement) {
		RList renderable = (RList) markupElement.getUINode();
		if(renderable == null) {
			renderable = new RList(markupElement, this.listNesting, this.userAgentContext, this.rendererContext, this.frameContext, this.container, null);
			markupElement.setUINode(renderable);
		}
		renderable.setOriginalParent(this);
		this.positionRBlock( markupElement, renderable);		
	}

//	private void addParagraphBreak(ModelNode startNode) {
//		// This needs to get replaced with paragraph collapsing
//		this.addLineBreak(startNode, LineBreak.NONE);
//		this.addLineBreak(startNode, LineBreak.NONE);
//	}
	
	private void addLineBreak(ModelNode startNode, int breakType) {
		RLine line = this.currentLine;
		if(line == null) {
			Insets insets = this.paddingInsets;
			this.addLine(startNode, null, insets.top);
			line = this.currentLine;
		}
		if(line.getHeight() == 0) {
			RenderState rs = startNode.getRenderState();
			int fontHeight = rs.getFontMetrics().getHeight();
			line.setHeight(fontHeight);
		}
		line.setLineBreak(new LineBreak(breakType, startNode));
		int newLineY;
		FloatingBounds fb = this.floatBounds;
		if(breakType == LineBreak.NONE || fb == null) {
			newLineY = line == null ? this.paddingInsets.top : line.y + line.height;
		}
		else {
			int prevY = line == null ? this.paddingInsets.top : line.y + line.height;
			switch(breakType) {
			case LineBreak.LEFT:
				newLineY = fb.getLeftClearY(prevY);
				break;
			case LineBreak.RIGHT:
				newLineY = fb.getRightClearY(prevY);
				break;
			default:
				newLineY = fb.getClearY(prevY);
				break;
			}			
		}
		this.currentLine = this.addLine(startNode, line, newLineY);
	}
	
	private boolean addElsewhereIfFloat(BoundableRenderable renderable, HTMLElementImpl element, boolean usesAlignAttribute, AbstractCSS2Properties style, boolean layout) {
		// "static" handled here
		String align = null;
		if(style != null) {
			align = style.getFloat();
			if(align != null && align.length() == 0) {
				align = null;
			}
		}
		if(align == null && usesAlignAttribute) {
			align = element.getAttribute("align");
		}
		if(align != null) {
			if("left".equalsIgnoreCase(align)) {
				this.layoutFloat(renderable, layout, true);
				return true;
			}
			else if("right".equalsIgnoreCase(align)) {
				this.layoutFloat(renderable, layout, false);
				return true;
			}
			else {
				// fall through
			}
		}	
		return false;
	}
	
//	final RBlockViewport getParentViewport(ExportedRenderable er) {
//		if(er.alignment == 0) {
//			return this.getParentViewport();
//		}
//		else {
//			return this.getParentViewportForAlign();
//		}
//	}
//	
//	final boolean isImportable(ExportedRenderable er) {
//		if(er.alignment == 0) {
//			return this.positionsAbsolutes();
//		}
//		else {
//			return this.getParentViewportForAlign() == null;
//		}
//	}
	
	final RBlockViewport getParentViewport() {
		// Use originalParent, which for one, is not going to be null during layout.
		RCollection parent = this.getOriginalOrCurrentParent();
		while(parent != null && !(parent instanceof RBlockViewport)) {
			parent = parent.getOriginalOrCurrentParent();
		}
		return (RBlockViewport) parent;
	}

//	final RBlockViewport getParentViewportForAlign() {
//		// Use originalParent, which for one, is not going to be null during layout.
//		Object parent = this.getOriginalOrCurrentParent();
//		if(parent instanceof RBlock) {
//			RBlock block = (RBlock) parent;
//			if(!block.couldBeScrollable()) {
//				parent = ((BaseElementRenderable) parent).getOriginalOrCurrentParent();
//				if(parent instanceof RBlockViewport) {
//					return (RBlockViewport) parent;
//				}
//			}	
//		}
//		return null;
//	}
//
	private static int getPosition(HTMLElementImpl element) {
		RenderState rs = element.getRenderState();
		return rs == null ? RenderState.POSITION_STATIC : rs.getPosition();
	}
	
	/**
	 * Checks for position and float attributes.
	 * @param container
	 * @param containerSize
	 * @param insets
	 * @param renderable
	 * @param element
	 * @param usesAlignAttribute
	 * @return True if it was added elsewhere.
	 */
	private boolean addElsewhereIfPositioned(RElement renderable, HTMLElementImpl element, boolean usesAlignAttribute, boolean layoutIfPositioned, boolean obeysFloats) {
		// At this point block already has bounds.
		AbstractCSS2Properties style = element.getCurrentStyle();
		int position = getPosition(element);
		boolean absolute = position == RenderState.POSITION_ABSOLUTE;
		boolean relative = position == RenderState.POSITION_RELATIVE;
		if(absolute || relative) {
			if(layoutIfPositioned) {
				// Presumes the method will return true.
			    if(renderable instanceof RBlock) {
			        RBlock block = (RBlock) renderable;
			        FloatingBoundsSource inheritedFloatBoundsSource = null;
			        if(relative) {
			            Insets paddingInsets = this.paddingInsets;
			            RLine line = this.currentLine;
			            // Inform line done before layout so floats are considered.
			            this.lineDone(line);
			            int newY = line == null ? paddingInsets.top : line.y + line.height;
			            final int expectedWidth = this.availContentWidth;
			            final int blockShiftRight = paddingInsets.right;
			            final int newX = paddingInsets.left;
			            FloatingBounds floatBounds = this.floatBounds;
			            inheritedFloatBoundsSource = floatBounds == null ? null : new ParentFloatingBoundsSource(blockShiftRight, expectedWidth, newX, newY, floatBounds);         
			        }
			        block.layout(this.availContentWidth, this.availContentHeight, false, false, inheritedFloatBoundsSource, this.sizeOnly);
			    }
			    else {
			        renderable.layout(this.availContentWidth, this.availContentHeight, this.sizeOnly);
			    }			        
			}
			RenderState rs = element.getRenderState();
			String leftText = style.getLeft();
			RLine line = this.currentLine;
			int lineBottomY = line == null ? 0 : line.getY() + line.getHeight();
			int newLeft;
			if(leftText != null) {
				newLeft = HtmlValues.getPixelSize(leftText, rs, 0, this.availContentWidth);
			}
			else {
				String rightText = style.getRight();
				if(rightText != null) {
					int right = HtmlValues.getPixelSize(rightText, rs, 0, this.availContentWidth);
					newLeft = this.desiredWidth - right - renderable.getWidth();
					// If right==0 and renderable.width is larger than the parent's width, 
					// the expected behavior is for newLeft to be negative.
				}
				else {
					newLeft = 0;
				}
			}
			int newTop = relative ? 0 : lineBottomY;
			String topText = style.getTop();
			if(topText != null) {
				newTop = HtmlValues.getPixelSize(topText, rs, newTop, this.availContentHeight);
			}
			else {
				String bottomText = style.getBottom();
				if(bottomText != null) {
					int bottom = HtmlValues.getPixelSize(bottomText, rs, 0, this.availContentHeight);
					newTop = this.desiredHeight - bottom - renderable.getHeight();
					if(!relative && newTop < 0) {
						newTop = 0;
					}
				}
			}
			if(relative) {
				// First, try to add normally.
				RRelative rrel = new RRelative(this.container, element, renderable, newLeft, newTop);
				rrel.assignDimension();
				if(!this.addElsewhereIfFloat(rrel, element, usesAlignAttribute, style, true)) {
		            boolean centerBlock = false;
		            if(renderable instanceof RTable) {
		                String align = element.getAttribute("align");
		                centerBlock = align != null && align.equalsIgnoreCase("center");
		            }
					this.addAsSeqBlock(rrel, obeysFloats, true, true, centerBlock);
                    // Need to import float boxes from relative, after
                    // the box's origin has been set.
                    FloatingInfo floatingInfo = rrel.getExportableFloatingInfo();
                    if(floatingInfo != null) {
	                   this.importFloatingInfo(floatingInfo, rrel);
	                }
				}
				else {
					// Adjust size of RRelative again - float might have been adjusted.
					rrel.assignDimension();
				}
			}
			else {
				// Schedule as delayed pair. Will be positioned after
				// everything else.
				this.scheduleAbsDelayedPair(renderable, newLeft, newTop);
				// Does not affect bounds of this viewport yet.
				return true;
			}
			int newBottomY = renderable.getY() + renderable.getHeight();
			this.checkY(newBottomY);
			if(newBottomY > this.maxY) {
				this.maxY = newBottomY;
			}
			return true;
		}
		else {
			if(this.addElsewhereIfFloat( renderable, element, usesAlignAttribute, style, layoutIfPositioned)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks property 'float' and in some cases attribute 'align'.
	 */
	private void addRenderableToLineCheckStyle(RElement renderable, HTMLElementImpl element, boolean usesAlignAttribute) {
		if(this.addElsewhereIfPositioned( renderable, element, usesAlignAttribute, true, true)) {
			return;
		}
		renderable.layout(this.availContentWidth, this.availContentHeight, this.sizeOnly);
		this.addRenderableToLine(renderable);
	}
	
	private void addRenderableToLine(Renderable renderable) {
		//this.skipLineBreakBefore = false;
		RenderState rs = renderable.getModelNode().getRenderState();
		RLine line = this.currentLine;
		int liney = line.y;
		boolean emptyLine = line.isEmpty();
		FloatingBounds floatBounds = this.floatBounds;
		int cleary;
		if(floatBounds != null) {
			cleary = floatBounds.getFirstClearY(liney);
		}
		else {
			cleary = liney + line.height;
		}
		try {
			line.add(renderable);
			// Check if the line goes into the float.
			if(floatBounds != null && cleary > liney) {
			    int rightOffset = this.fetchRightOffset(liney);
			    int topLineX = this.desiredWidth - rightOffset; 
			    if(line.getX() + line.getWidth() > topLineX) {
			       // Shift line down to clear area
			       line.setY(cleary);      
			    }
			}
		} catch(OverflowException oe) {
			int nextY = emptyLine ? cleary : liney + line.height;
			this.addLine(renderable.getModelNode(), line, nextY);
			Collection renderables = oe.getRenderables();
			Iterator i = renderables.iterator();
			while(i.hasNext()) {
				Renderable r = (Renderable) i.next();
				this.addRenderableToLine(r);
			}
		}
		if(renderable instanceof RUIControl) {
			this.container.addComponent(((RUIControl) renderable).widget.getComponent());
		}
	}

	private void addWordToLine(RWord renderable) {
		//this.skipLineBreakBefore = false;
		RLine line = this.currentLine;
		int liney = line.y;
		boolean emptyLine = line.isEmpty();
		FloatingBounds floatBounds = this.floatBounds;
		int cleary;
		if(floatBounds != null) {
			cleary = floatBounds.getFirstClearY(liney);
		}
		else {
			cleary = liney + line.height;
		}
		try {
			line.addWord(renderable);
            // Check if the line goes into the float.
            if(floatBounds != null && cleary > liney) {
                int rightOffset = this.fetchRightOffset(liney);
                int topLineX = this.desiredWidth - rightOffset; 
                if(line.getX() + line.getWidth() > topLineX) {
                   // Shift line down to clear area
                   line.setY(cleary);      
                }
            }
		} catch(OverflowException oe) {
			int nextY = emptyLine ? cleary : liney + line.height;
			this.addLine(renderable.getModelNode(), line, nextY);
			Collection renderables = oe.getRenderables();
			Iterator i = renderables.iterator();
			while(i.hasNext()) {
				Renderable r = (Renderable) i.next();
				this.addRenderableToLine(r);
			}
		}
	}

	private void addAsSeqBlockCheckStyle(RElement block, HTMLElementImpl element, boolean usesAlignAttribute) {
		if(this.addElsewhereIfPositioned(block, element, usesAlignAttribute, false, true)) {
			return;
		}
		this.addAsSeqBlock(block);		
	}
	
	private void addAsSeqBlock(RElement block) {
		this.addAsSeqBlock(block, true, true, true, false);
	}
	
	private int getNewBlockY(BoundableRenderable newBlock, int expectedY) {
	    // Assumes the previous block is not a line with height > 0.
	    if(!(newBlock instanceof RElement)) {
	        return expectedY;
	    }
	    RElement block = (RElement) newBlock;
	    int ccm = this.currentCollapsibleMargin;
	    int topMargin = block.getMarginTop();
	    if(topMargin == 0 && ccm == 0) {
	        return expectedY;
	    }
	    return expectedY - Math.min(topMargin, ccm);
	}
	
	private int getEffectiveBlockHeight(BoundableRenderable block) {
	    // Assumes block is the last one in the sequence.
	    if(!(block instanceof RElement)) {
	        return block.getHeight();
	    }
	    RCollection parent = this.getParent();
	    if(!(parent instanceof RElement)) {
	        return block.getHeight();
	    }
	    int blockMarginBottom = ((RElement) block).getMarginBottom();
	    int parentMarginBottom = ((RElement) parent).getCollapsibleMarginBottom();
	    return block.getHeight() - Math.min(blockMarginBottom, parentMarginBottom);
	}
	
	private void addAsSeqBlock(BoundableRenderable block, boolean obeysFloats, boolean informLineDone, boolean addLine, boolean centerBlock) {
		Insets insets = this.paddingInsets;
		int insetsl = insets.left;
		ArrayList sr = this.seqRenderables;
		if(sr == null) {
			sr = new ArrayList(1);
			this.seqRenderables = sr;
		}
		RLine prevLine = this.currentLine;
		boolean initialAllowOverflow;
		if(prevLine != null) {
		    initialAllowOverflow = prevLine.isAllowOverflow();
			if(informLineDone) {
				this.lineDone(prevLine);	
			}
			if(prevLine.x + prevLine.width > this.maxX) {
				this.maxX = prevLine.x + prevLine.width;
			}
			// Check height only with floats.
		}
		else {
		    initialAllowOverflow = false;
		}
		int prevLineHeight = prevLine == null ? 0 : prevLine.height;
		int newLineY = prevLine == null ? insets.top : prevLine.y + prevLineHeight;
		int blockX;
		int blockY = prevLineHeight == 0 ? this.getNewBlockY(block, newLineY) : newLineY;
		int blockWidth = block.getWidth();
		if(obeysFloats) {
			//TODO: execution of fetchLeftOffset done twice with positionRElement.
			FloatingBounds floatBounds = this.floatBounds;
			int actualAvailWidth;
			if(floatBounds != null) {
				int blockOffset = this.fetchLeftOffset(newLineY);
				blockX = blockOffset;
				int rightOffset = this.fetchRightOffset(newLineY);
				actualAvailWidth = this.desiredWidth - rightOffset - blockOffset;
				if(blockWidth > actualAvailWidth) {
					blockY = floatBounds.getClearY(newLineY);
				}	
			}
			else {
			    actualAvailWidth = this.availContentWidth;
				blockX = insetsl;
			}
			if(centerBlock) {
			    int roomX = actualAvailWidth - blockWidth;
			    if(roomX > 0) {
			        blockX += (roomX / 2);
			    }
			}
		}
		else {
			//Block does not obey alignment margins
			blockX = insetsl;
		}
		block.setOrigin(blockX, blockY);
		sr.add(block);
		block.setParent(this);
		if(blockX + blockWidth > this.maxX) {
			this.maxX = blockX + blockWidth;
		}
		this.lastSeqBlock = block;
		this.currentCollapsibleMargin = block instanceof RElement ? ((RElement) block).getMarginBottom() : 0;
		if(addLine) {
	        newLineY = blockY + block.getHeight();
	        this.checkY(newLineY);
	        int leftOffset = this.fetchLeftOffset(newLineY);
	        int newX = leftOffset;
	        int newMaxWidth = this.desiredWidth - this.fetchRightOffset(newLineY) - leftOffset;
	        ModelNode lineNode = block.getModelNode().getParentModelNode();
		    RLine newLine = new RLine(lineNode, this.container, newX, newLineY, newMaxWidth, 0, initialAllowOverflow);
		    newLine.setParent(this);
		    sr.add(newLine);
		    this.currentLine = newLine;
		}
	}
	
	private void addLineAfterBlock(RBlock block, boolean informLineDone) {
        ArrayList sr = this.seqRenderables;
        if(sr == null) {
            sr = new ArrayList(1);
            this.seqRenderables = sr;
        }
        RLine prevLine = this.currentLine;
        boolean initialAllowOverflow;
        if(prevLine != null) {
            initialAllowOverflow = prevLine.isAllowOverflow();
            if(informLineDone) {
                this.lineDone(prevLine);    
            }
            if(prevLine.x + prevLine.width > this.maxX) {
                this.maxX = prevLine.x + prevLine.width;
            }
            // Check height only with floats.
        }
        else {
            initialAllowOverflow = false;
        }
        ModelNode lineNode = block.getModelNode().getParentModelNode();
        int newLineY = block.getY() + block.getHeight();
        this.checkY(newLineY);
        int leftOffset = this.fetchLeftOffset(newLineY);
        int newX = leftOffset;
        int newMaxWidth = this.desiredWidth - this.fetchRightOffset(newLineY) - leftOffset;
        RLine newLine = new RLine(lineNode, this.container, newX, newLineY, newMaxWidth, 0, initialAllowOverflow);
        newLine.setParent(this);
        sr.add(newLine);
        this.currentLine = newLine;
	}
			
	private void layoutText(NodeImpl textNode) {
		RenderState renderState = textNode.getRenderState();
		if(renderState == null) {
			throw new IllegalStateException("RenderState is null for node " + textNode + " with parent " + textNode.getParentNode());
		}
		FontMetrics fm = renderState.getFontMetrics();
		int descent = fm.getDescent();
		int ascentPlusLeading = fm.getAscent() + fm.getLeading();
		int wordHeight = fm.getHeight();
		int blankWidth = fm.charWidth(' ');
		int whiteSpace = this.overrideNoWrap ? RenderState.WS_NOWRAP : renderState.getWhiteSpace();
		int textTransform = renderState.getTextTransform();
		String text = textNode.getNodeValue(); 
		if(whiteSpace != RenderState.WS_PRE) {
	        boolean prevAllowOverflow = this.currentLine.isAllowOverflow();
			boolean allowOverflow = whiteSpace == RenderState.WS_NOWRAP;
			this.currentLine.setAllowOverflow(allowOverflow);
			try {
			    int length = text.length();
			    boolean firstWord = true;
			    StringBuffer word = new StringBuffer(12);
			    for(int i = 0; i < length; i++) {
			        char ch = text.charAt(i);
			        if(Character.isWhitespace(ch)) {
			            if(firstWord) {
			                firstWord = false;
			            }
			            int wlen = word.length();
			            if(wlen > 0) {
			                RWord rword = new RWord(textNode, word.toString(), container, fm, descent, ascentPlusLeading, wordHeight, textTransform);
			                this.addWordToLine(rword);
			                word.delete(0, wlen);
			            }
			            RLine line = this.currentLine;
			            if(line.width > 0) {
			                RBlank rblank = new RBlank(textNode, fm, container, ascentPlusLeading, blankWidth, wordHeight);
			                line.addBlank(rblank);					
			            }
			            for(i++; i < length; i++) {
			                ch = text.charAt(i);
			                if(!Character.isWhitespace(ch)) {
			                    word.append(ch);
			                    break;
			                }
			            }
			        }
			        else {
			            word.append(ch);
			        }
			    }
			    if(word.length() > 0) {
			        RWord rword = new RWord(textNode, word.toString(), container, fm, descent, ascentPlusLeading, wordHeight, textTransform);
			        this.addWordToLine(rword);
			    }
			} finally {
			    this.currentLine.setAllowOverflow(prevAllowOverflow);
			}
		}
		else {
			int length = text.length();
			boolean lastCharSlashR = false;
			StringBuffer line = new StringBuffer();
			for(int i = 0; i < length; i++) {
				char ch = text.charAt(i);
				switch(ch) {
				case '\r':
					lastCharSlashR = true;
					break;
				case '\n':
					int llen = line.length();
					if(llen > 0) {
						RWord rword = new RWord(textNode, line.toString(), container, fm, descent, ascentPlusLeading, wordHeight, textTransform);
						this.addWordToLine(rword);
						line.delete(0, line.length());
					}
					RLine prevLine = this.currentLine;
					prevLine.setLineBreak(new LineBreak(LineBreak.NONE, textNode));
					this.addLine(textNode, prevLine, prevLine.y + prevLine.height);
					break;
				default:
					if(lastCharSlashR) {
						line.append('\r');
						lastCharSlashR = false;
					}
					line.append(ch);
					break;
				}
			}
			if(line.length() > 0) {
				RWord rword = new RWord(textNode, line.toString(), container, fm, descent, ascentPlusLeading, wordHeight, textTransform);
				this.addWordToLine(rword);
			}
		}
	}

	/**
	 * 
	 * @param others An ordered collection.
	 * @param seqRenderables
	 * @param destination
	 */
	private void populateZIndexGroups(Collection others, Collection seqRenderables, ArrayList destination) {
		this.populateZIndexGroups(others, seqRenderables == null ? null : seqRenderables.iterator(), destination);
	}

	/**
	 * 
	 * @param others An ordered collection.
	 * @param seqRenderablesIterator
	 * @param destination
	 */
	private void populateZIndexGroups(Collection others, Iterator seqRenderablesIterator, ArrayList destination) {
		// First, others with z-index < 0
		Iterator i1 = others.iterator();
		Renderable pending = null;
		while(i1.hasNext()) {
			PositionedRenderable pr = (PositionedRenderable) i1.next();
			BoundableRenderable r = pr.renderable;
			if(r.getZIndex() >= 0) {
				pending = r;
				break;
			}
			destination.add(r);
		}
		
		// Second, sequential renderables
		Iterator i2 = seqRenderablesIterator;
		if(i2 != null) {
			while(i2.hasNext()) {
				destination.add(i2.next());
			}
		}
		
		// Third, other renderables with z-index >= 0.
		if(pending != null) {
			destination.add(pending);
			while(i1.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i1.next();
				Renderable r = pr.renderable;
				destination.add(r);			
			}
		}
	}
	
	public Renderable[] getRenderablesArray() {
		SortedSet others = this.positionedRenderables;
		int othersSize = others == null ? 0 : others.size();
		if(othersSize == 0) {
			ArrayList sr = this.seqRenderables;
			return sr == null ? Renderable.EMPTY_ARRAY : (Renderable[]) sr.toArray(Renderable.EMPTY_ARRAY);
		}
		else {
			ArrayList allRenderables = new ArrayList();
			this.populateZIndexGroups(others, this.seqRenderables, allRenderables);
			return (Renderable[]) allRenderables.toArray(Renderable.EMPTY_ARRAY);
		}
	}
	
	public Iterator getRenderables() {
		SortedSet others = this.positionedRenderables;
		if(others == null || others.size() == 0) {
			ArrayList sr = this.seqRenderables;
			return sr == null ? null : sr.iterator();
		}
		else {
			ArrayList allRenderables = new ArrayList();
			this.populateZIndexGroups(others, this.seqRenderables, allRenderables);
			return allRenderables.iterator();
		}
	}

	public Iterator getRenderables(Rectangle clipBounds) {
		if(!EventQueue.isDispatchThread() && logger.isLoggable(Level.INFO)) {
			logger.warning("getRenderables(): Invoked outside GUI dispatch thread.");
		}
		ArrayList sr = this.seqRenderables;
		Iterator baseIterator = null;
		if(sr != null) {
			Renderable[] array = (Renderable[]) sr.toArray(Renderable.EMPTY_ARRAY);
			Range range = MarkupUtilities.findRenderables(array, clipBounds, true);
			baseIterator = org.lobobrowser.util.ArrayUtilities.iterator(array, range.offset, range.length);
		}
		SortedSet others = this.positionedRenderables;
		if(others == null || others.size() == 0) {
			return baseIterator;
		}
		else {
			ArrayList matches = new ArrayList();
			// ArrayList "matches" keeps the order from "others".
			Iterator i = others.iterator();
			while(i.hasNext()) {
				PositionedRenderable pr = (PositionedRenderable) i.next();
				Object r = pr.renderable;
				if(r instanceof BoundableRenderable) {
					BoundableRenderable br = (BoundableRenderable) r;
					Rectangle rbounds = br.getBounds();
					if(clipBounds.intersects(rbounds)) {
						matches.add(pr);
					}
				}
			}
			if(matches.size() == 0) {
				return baseIterator;
			}
			else {
				ArrayList destination = new ArrayList();
				this.populateZIndexGroups(matches, baseIterator, destination);
				return destination.iterator();
			}
		}
	}

	
	public BoundableRenderable getRenderable(int x, int y) {
		Iterator i = this.getRenderables(x, y);
		return i == null ? null : (i.hasNext() ? (BoundableRenderable) i.next() : null);
	}

	public BoundableRenderable getRenderable(java.awt.Point point) {
		return this.getRenderable(point.x, point.y);
	}

	public Iterator getRenderables(java.awt.Point point) {
		return this.getRenderables(point.x, point.y);
	}
	
	public Iterator getRenderables(int pointx, int pointy) {
		if(!EventQueue.isDispatchThread() && logger.isLoggable(Level.INFO)) {
			logger.warning("getRenderable(): Invoked outside GUI dispatch thread.");
		}
		Collection result = null;
		SortedSet others = this.positionedRenderables;
		int size = others == null ? 0 : others.size();
		PositionedRenderable[] otherArray = size == 0 ? null : (PositionedRenderable[]) others.toArray(PositionedRenderable.EMPTY_ARRAY);
		// Try to find in other renderables with z-index >= 0 first.
		int index = 0;
		if(size != 0) {
			int px = pointx;
			int py = pointy;	
			// Must go in reverse order
			for(index = size; --index >= 0;) {
				PositionedRenderable pr = otherArray[index];
				BoundableRenderable r = pr.renderable;
				if(r.getZIndex() < 0) {
					break;
				}
				if(r instanceof BoundableRenderable) {
					BoundableRenderable br = (BoundableRenderable) r;
					Rectangle rbounds = br.getBounds();
					if(rbounds.contains(px, py)) {
						if(result == null) {
							result = new LinkedList();
						}
						result.add(br);
					}
				}
			}
		}
		
		// Now do a "binary" search on sequential renderables.
		ArrayList sr = this.seqRenderables;
		if(sr != null) {
			Renderable[] array = (Renderable[]) sr.toArray(Renderable.EMPTY_ARRAY);
			BoundableRenderable found = MarkupUtilities.findRenderable(array, pointx, pointy, true);
			if(found != null) {
				if(result == null) {
					result = new LinkedList();
				}
				result.add(found);
			}
		}
		
		// Finally, try to find it in renderables with z-index < 0.
		if(size != 0) {
			int px = pointx;
			int py = pointy;	
			// Must go in reverse order
			for(; index >= 0; index--) {
				PositionedRenderable pr = otherArray[index];
				Renderable r = pr.renderable;
				if(r instanceof BoundableRenderable) {
					BoundableRenderable br = (BoundableRenderable) r;
					Rectangle rbounds = br.getBounds();
					if(rbounds.contains(px, py)) {
						if(result == null) {
							result = new LinkedList();
						}
						result.add(br);
					}
				}
			}
		}	
		return result == null ? null : result.iterator();
	}

	private RElement setupNewUIControl(RenderableContainer container, HTMLElementImpl element, UIControl control) {
		RElement renderable = new RUIControl(element, control, container, this.frameContext, this.userAgentContext);
		element.setUINode((UINode) renderable);
		return renderable;
	}
	
	private final void addAlignableAsBlock(HTMLElementImpl markupElement, RElement renderable) {
		//TODO: Get rid of this method?
		// At this point block already has bounds.
		boolean regularAdd = false;
		String align = markupElement.getAttribute("align");
		if(align != null) {
			if("left".equalsIgnoreCase(align)) {
				this.layoutFloat(renderable, false, true); 
			}
			else if("right".equalsIgnoreCase(align)) {
				this.layoutFloat(renderable, false, false);
			}
			else {
				regularAdd = true;
			}	
		}
		else {
			regularAdd = true;
		}		
		if(regularAdd) {
			this.addAsSeqBlock(renderable);
		}
	}
	
	private final void layoutHr(HTMLElementImpl markupElement) {
		RElement renderable = (RElement) markupElement.getUINode();
		if(renderable == null) {
			renderable = this.setupNewUIControl(container, markupElement, new HrControl(markupElement));
		}
		renderable.layout(this.availContentWidth, this.availContentHeight, this.sizeOnly);
		this.addAlignableAsBlock( markupElement, renderable);
	}

	private final BaseInputControl createInputControl(HTMLBaseInputElement markupElement) {
		String type = markupElement.getAttribute("type");
		if(type == null) {
			return new InputTextControl(markupElement);
		}
		type = type.toLowerCase();
		if("text".equals(type) || type.length() == 0) {
			return new InputTextControl(markupElement);
		}
		else if("hidden".equals(type)) {
			return null;
		}
		else if("submit".equals(type)) {
			return new InputButtonControl(markupElement);
		}
		else if("password".equals(type)) {
			return new InputPasswordControl(markupElement);
		}
		else if("radio".equals(type)) {
			return new InputRadioControl(markupElement);
		}
		else if("checkbox".equals(type)) {
			return new InputCheckboxControl(markupElement);
		}
		else if("image".equals(type)) {
			return new InputImageControl(markupElement);
		}
		else if("reset".equals(type)) {
			return new InputButtonControl(markupElement);
		}
		else if("button".equals(type)) {
			return new InputButtonControl(markupElement);
		}
		else if("file".equals(type)) {
			return new InputFileControl(markupElement);
		}
		else {
			return null;
		}
	}
	
	/**
	 * Gets offset from the left due to floats. It includes padding.
	 */
	private final int fetchLeftOffset(int newLineY) {
		Insets paddingInsets = this.paddingInsets;
		FloatingBounds floatBounds = this.floatBounds;
		if(floatBounds == null) {
			return paddingInsets.left;
		}
		int left = floatBounds.getLeft(newLineY);
		if(left < paddingInsets.left) {
			return paddingInsets.left;
		}
		return left;
	}
	
	/**
	 * Gets offset from the right due to floats. It includes padding.
	 */
	private final int fetchRightOffset(int newLineY) {
		Insets paddingInsets = this.paddingInsets;
		FloatingBounds floatBounds = this.floatBounds;
		if(floatBounds == null) {
			return paddingInsets.right;
		}
		int right = floatBounds.getRight(newLineY);
		if(right < paddingInsets.right) {
			return paddingInsets.right;
		}
		return right;
	}
	
	private static final SizeExceededException SEE = new SizeExceededException();
	
	private final void checkY(int y) {
		if(this.yLimit != -1 && y > this.yLimit) {
			throw SEE;
		}
	}
	
	private final void layoutFloat(BoundableRenderable renderable, boolean layout, boolean leftFloat) {
		renderable.setOriginalParent(this);
		if(layout) {
			int availWidth = this.availContentWidth;
			int availHeight = this.availContentHeight;
			if(renderable instanceof RBlock) {
			    RBlock block = (RBlock) renderable;
			    // Float boxes don't inherit float bounds?
			    block.layout(availWidth, availHeight, false, false, null, this.sizeOnly);
			}
			else if(renderable instanceof RElement) {
				RElement e = (RElement) renderable;
				e.layout(availWidth, availHeight, this.sizeOnly);
			}
		}
		RFloatInfo floatInfo = new RFloatInfo(renderable.getModelNode(), renderable, leftFloat);
		this.currentLine.simplyAdd(floatInfo);		
		this.scheduleFloat(floatInfo);
	}	

	
	private void scheduleAbsDelayedPair(BoundableRenderable renderable, int x, int y) {
		// It gets reimported in the local
		// viewport if it turns out it can't be exported up.
		RenderableContainer container = this.container;
		for(;;) {
			if(container instanceof Renderable) {
				Object node = ((Renderable) container).getModelNode();
				if(node instanceof HTMLElementImpl) {
					HTMLElementImpl element = (HTMLElementImpl) node;
					int position = getPosition(element);
					if(position != RenderState.POSITION_STATIC) {
						break;
					}
					RenderableContainer newContainer = container.getParentContainer();
					if(newContainer == null) {
						break;
					}
					container = newContainer;
				}
				else {
					break;
				}
			}
			else {
				break;
			}
		}
		DelayedPair pair = new DelayedPair(container, renderable, x, y);
		this.container.addDelayedPair(pair);
	}

	void importDelayedPair(DelayedPair pair) {
		BoundableRenderable r = pair.child;
		r.setOrigin(pair.x, pair.y);
		this.addPositionedRenderable(r, false, false);
		// Size of block does not change - it's
		// set in stone?
	}

	private final void addPositionedRenderable(BoundableRenderable renderable, boolean verticalAlignable, boolean isFloat) {
		// Expected to be called only in GUI thread.
		SortedSet others = this.positionedRenderables;
		if(others == null) {
			others = new TreeSet(new ZIndexComparator());
			this.positionedRenderables = others;
		}
		others.add(new PositionedRenderable(renderable, verticalAlignable, this.positionedOrdinal++, isFloat));
		renderable.setParent(this);
		if(renderable instanceof RUIControl) {
			this.container.addComponent(((RUIControl) renderable).widget.getComponent());
		}
	}
	
	public int getFirstLineHeight() {
		ArrayList renderables = this.seqRenderables;
		if(renderables != null) {
			int size = renderables.size();
			if(size == 0) {
				return 0;
			}
			for(int i = 0; i < size; i++) {
				BoundableRenderable br = (BoundableRenderable) renderables.get(0);
				int height = br.getHeight();
				if(height != 0) {
					return height;
				}
			}
		}
		// Not found!!
		return 1;
	}

	public int getFirstBaselineOffset() {
		ArrayList renderables = this.seqRenderables;
		if(renderables != null) {
			Iterator i = renderables.iterator();
			while(i.hasNext()) {
				Object r = i.next();
				if(r instanceof RLine) {
					int blo =((RLine) r).getBaselineOffset();
					if(blo != 0) {
						return blo;
					}
				}
				else if(r instanceof RBlock) {
					RBlock block = (RBlock) r;
					if(block.getHeight() > 0) {
						Insets insets = block.getInsets(false, false);
						Insets paddingInsets = this.paddingInsets;
						return block.getFirstBaselineOffset() + insets.top + (paddingInsets == null ? 0 : paddingInsets.top);
					}					
				}
			}
		}
		return 0;
	}
	
    //----------------------------------------------------------------

	public RenderableSpot getLowestRenderableSpot(int x, int y) {
	    BoundableRenderable br = this.getRenderable(new Point(x, y));
	    if(br != null) {
	    	return br.getLowestRenderableSpot(x - br.getX(), y - br.getY());
	    }
	    else {
	    	return new RenderableSpot(this, x, y);
	    }
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseClick(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMouseClick(MouseEvent event, int x, int y) {
		Iterator i = this.getRenderables(new Point(x, y));
		if(i != null) {
			while(i.hasNext()) {
			    BoundableRenderable br = (BoundableRenderable) i.next();
			    if(br != null) {
			    	Rectangle bounds = br.getBounds();
			    	if(!br.onMouseClick(event, x - bounds.x, y - bounds.y)) {
			    		return false;
			    	}
			    }
			}
		}
		return true;
	}

	public boolean onDoubleClick(MouseEvent event, int x, int y) {
		Iterator i = this.getRenderables(new Point(x, y));
		if(i != null) {
			while(i.hasNext()) {
			    BoundableRenderable br = (BoundableRenderable) i.next();
			    if(br != null) {
			    	Rectangle bounds = br.getBounds();
			    	if(!br.onDoubleClick(event, x - bounds.x, y - bounds.y)) {
			    		return false;
			    	}
			    }
			}
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseDisarmed(java.awt.event.MouseEvent)
	 */
	public boolean onMouseDisarmed(MouseEvent event) {
		BoundableRenderable br = this.armedRenderable;
		if(br != null) {
			try {
				return br.onMouseDisarmed(event);
			} finally {
				this.armedRenderable = null;
			}
		}
		else {
			return true;
		}
	}

	private BoundableRenderable armedRenderable;
	
	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMousePressed(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMousePressed(MouseEvent event, int x, int y) {
		Iterator i = this.getRenderables(new Point(x, y));
		if(i != null) {
			while(i.hasNext()) {
			    BoundableRenderable br = (BoundableRenderable) i.next();
			    if(br != null) {
			    	Rectangle bounds = br.getBounds();
			    	if(!br.onMousePressed(event, x - bounds.x, y - bounds.y)) {
				    	this.armedRenderable = br;
			    		return false;
			    	}
			    }
			}
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.BoundableRenderable#onMouseReleased(java.awt.event.MouseEvent, int, int)
	 */
	public boolean onMouseReleased(MouseEvent event, int x, int y) {
		Iterator i = this.getRenderables(new Point(x, y));
		if(i != null) {
			while(i.hasNext()) {
			    BoundableRenderable br = (BoundableRenderable) i.next();
			    if(br != null) {
			    	Rectangle bounds = br.getBounds();
			    	if(!br.onMouseReleased(event, x - bounds.x, y - bounds.y)) {
				    	BoundableRenderable oldArmedRenderable = this.armedRenderable;
				    	if(oldArmedRenderable != null && br != oldArmedRenderable) {
				    		oldArmedRenderable.onMouseDisarmed(event);
				    		this.armedRenderable = null;
				    	}
			    		return false;
			    	}
			    }
			}
		}
    	BoundableRenderable oldArmedRenderable = this.armedRenderable;
    	if(oldArmedRenderable != null) {
    		oldArmedRenderable.onMouseDisarmed(event);
    		this.armedRenderable = null;
    	}	    	
    	return true;
	}

	public void paint(Graphics g) {
		Rectangle clipBounds = g.getClipBounds();
		Iterator i = this.getRenderables(clipBounds);
		if(i != null) {
			int renderableCount = 0;
			while(i.hasNext()) {
				renderableCount++;
				Object robj = i.next();			
				// The expected behavior in HTML is for boxes
				// not to be clipped unless overflow=hidden.
				if(robj instanceof BoundableRenderable) {
					BoundableRenderable renderable = (BoundableRenderable) robj;
					renderable.paintTranslated(g);
					//numRenderables++;
				}
				else {
					((Renderable) robj).paint(g);
				}
			}
		}
	}
	
	//----------------------------------------------------------------

	private static class NopLayout implements MarkupLayout {
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
		}
	}

	private static class NoScriptLayout implements MarkupLayout {
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			UserAgentContext ucontext = bodyLayout.userAgentContext;
			if(!ucontext.isScriptingEnabled()) {
				bodyLayout.layoutMarkup( markupElement);
			}
			else {
				//NOP
			}
		}
	}

	private static class ChildrenLayout implements MarkupLayout {
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			bodyLayout.layoutChildren(markupElement);
		}
	}

	private static class MiscLayout extends CommonLayout {
		public MiscLayout() {
			super(DISPLAY_INLINE);
		}
	}

	private static class HLayout extends CommonLayout {		
		public HLayout(int fontSize) {
			super(DISPLAY_BLOCK);
		}
		
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			super.layoutMarkup(bodyLayout, markupElement);
		}
	}

	private static class PLayout extends CommonLayout {
		public PLayout() {
			super(DISPLAY_BLOCK);
		}
		
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			super.layoutMarkup(bodyLayout, markupElement);
		}
	}

	private static class ListItemLayout extends CommonLayout {
		public ListItemLayout() {
			super(DISPLAY_LIST_ITEM);
		}
	}

	private static class BrLayout implements MarkupLayout {
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			String clear = markupElement.getAttribute("clear");
			bodyLayout.addLineBreak(markupElement, LineBreak.getBreakType(clear));
		}
	}

	private static class HrLayout implements MarkupLayout {
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			bodyLayout.layoutHr( markupElement);
		}
	}

	private static class TableLayout extends CommonLayout {
		public TableLayout() {
			super(DISPLAY_TABLE);
		}
	}

	private static class CommonBlockLayout extends CommonLayout {
		public CommonBlockLayout() {
			super(DISPLAY_BLOCK);
		}		
	}

	//---------------------------------------------------------------------------
	
	private static class DivLayout extends CommonLayout {
		public DivLayout() {
			super(DISPLAY_BLOCK);
		}
	}
	
	private static class BlockQuoteLayout extends CommonLayout {
		public BlockQuoteLayout() {
			super(DISPLAY_BLOCK);
		}
	}

	private static class SpanLayout extends CommonLayout {
		public SpanLayout() {
			super(DISPLAY_INLINE);
		}
	}

	private static class EmLayout extends CommonLayout {
		public EmLayout() {
			super(DISPLAY_INLINE);
		}
		
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			super.layoutMarkup(bodyLayout, markupElement);
		}
	}
	
	private static class ULayout extends CommonLayout {
		public ULayout() {
			super(DISPLAY_INLINE);
		}
		
		/* (non-Javadoc)
		 * @see org.xamjwg.html.renderer.MarkupLayout#layoutMarkup(java.awt.Container, java.awt.Insets, org.xamjwg.html.domimpl.HTMLElementImpl)
		 */
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			super.layoutMarkup(bodyLayout, markupElement);
		}
	}

	private static class StrikeLayout extends CommonLayout {
		public StrikeLayout() {
			super(DISPLAY_INLINE);
		}
	}

	private static class StrongLayout extends CommonLayout {
		public StrongLayout() {
			super(DISPLAY_INLINE);
		}
	}
	
	private static class AnchorLayout extends CommonLayout {
		public AnchorLayout() {
			super(DISPLAY_INLINE);
		}
	}
	
	private static class ObjectLayout extends CommonWidgetLayout {
		private boolean tryToRenderContent;
		
		/**
		 * @param tryToRenderContent If the object is unknown, content is rendered as HTML.
		 * @param usesAlignAttribute
		 */
		public ObjectLayout(boolean tryToRenderContent, boolean usesAlignAttribute) {
			super(ADD_INLINE, usesAlignAttribute);
			this.tryToRenderContent = tryToRenderContent;
		}
		
		/**
		 * Must use this ThreadLocal because
		 * an ObjectLayout instance is shared
		 * across renderers.
		 */
		private final ThreadLocal htmlObject = new ThreadLocal();

		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			HtmlObject ho = bodyLayout.rendererContext.getHtmlObject(markupElement);
			if(ho == null && this.tryToRenderContent) {
				// Don't know what to do with it - render contents.
				bodyLayout.layoutMarkup( markupElement);
			}
			else if (ho != null) {
				this.htmlObject.set(ho);
				super.layoutMarkup(bodyLayout, markupElement);
			}
		}

		protected RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			HtmlObject ho = (HtmlObject) this.htmlObject.get();
			UIControl uiControl = new UIControlWrapper(ho);
			RUIControl ruiControl = new RUIControl(markupElement, uiControl, bodyLayout.container, bodyLayout.frameContext, bodyLayout.userAgentContext);
			return ruiControl;
		}
	}
	
	private static class ImgLayout extends CommonWidgetLayout {
		public ImgLayout() {
			super(ADD_INLINE, true);
		}

		protected RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			UIControl control = new ImgControl((HTMLImageElementImpl) markupElement);
			return new RImgControl(markupElement, control, bodyLayout.container, bodyLayout.frameContext, bodyLayout.userAgentContext);
		}
	}
	
	private static class InputLayout2 extends CommonWidgetLayout {
		public InputLayout2() {
			super(ADD_INLINE, true);			
		}

		protected RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {			
			HTMLBaseInputElement bie = (HTMLBaseInputElement) markupElement;
			BaseInputControl uiControl = bodyLayout.createInputControl(bie);
			if(uiControl == null) {
				return null;
			}
			bie.setInputContext(uiControl);
			return new RUIControl(markupElement, uiControl, bodyLayout.container, bodyLayout.frameContext, bodyLayout.userAgentContext);
		}
	}

	private static class SelectLayout extends CommonWidgetLayout {
		public SelectLayout() {
			super(ADD_INLINE, true);			
		}
		
		protected RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {			
			HTMLBaseInputElement bie = (HTMLBaseInputElement) markupElement;
			BaseInputControl uiControl = new InputSelectControl(bie);
			bie.setInputContext(uiControl);
			return new RUIControl(markupElement, uiControl, bodyLayout.container, bodyLayout.frameContext, bodyLayout.userAgentContext);
		}
	}

	private static class TextAreaLayout2 extends CommonWidgetLayout {
		public TextAreaLayout2() {
			super(ADD_INLINE, true);			
		}
		
		protected RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {			
			HTMLBaseInputElement bie = (HTMLBaseInputElement) markupElement;
			BaseInputControl control = new InputTextAreaControl(bie);
			bie.setInputContext(control);
			return new RUIControl(markupElement, control, bodyLayout.container, bodyLayout.frameContext, bodyLayout.userAgentContext);
		}
	}

	private static class IFrameLayout extends CommonWidgetLayout {
		public IFrameLayout() {
			super(ADD_INLINE, true);			
		}
		
		protected RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {			
			BrowserFrame frame = bodyLayout.rendererContext.createBrowserFrame();	
			((HTMLIFrameElementImpl) markupElement).setBrowserFrame(frame);
			UIControl control = new BrowserFrameUIControl(markupElement, frame);
			return new RUIControl(markupElement, control, bodyLayout.container, bodyLayout.frameContext, bodyLayout.userAgentContext);
		}
	}

	//------------------------------------------------------------------------
	
	/**
	 * This is layout common to elements that render themselves,
	 * except RBlock, RTable and RList.
	 */
	private static abstract class CommonWidgetLayout implements MarkupLayout {
		protected static final int ADD_INLINE = 0;
		protected static final int ADD_AS_BLOCK = 1;
		private final int method;
		private final boolean useAlignAttribute;
		
		public CommonWidgetLayout(int method, boolean usesAlignAttribute) {
			this.method = method;
			this.useAlignAttribute = usesAlignAttribute;
		}
		
		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			AbstractCSS2Properties style = markupElement.getCurrentStyle();
			if(style != null) {
				String display = style.getDisplay();
				if(display != null && "none".equalsIgnoreCase(display)) {
					return;
				}
			}
			UINode node = markupElement.getUINode();
			RElement renderable = null;
			if(node == null) {
				renderable = this.createRenderable(bodyLayout, markupElement);
				if(renderable == null) {
					if(logger.isLoggable(Level.INFO)) {
						logger.info("layoutMarkup(): Don't know how to render " + markupElement + ".");
					}
					return;
				}
				markupElement.setUINode((UINode) renderable);
			}
			else {
				renderable = (RElement) node;
			}
			renderable.setOriginalParent(bodyLayout);
			switch(this.method) {
			case ADD_INLINE:
				bodyLayout.addRenderableToLineCheckStyle(renderable, markupElement, this.useAlignAttribute);
				break;
			case ADD_AS_BLOCK:
			    bodyLayout.positionRElement(markupElement, renderable, this.useAlignAttribute, true, false);
				break;
			}
		}

		protected abstract RElement createRenderable(RBlockViewport bodyLayout, HTMLElementImpl markupElement);
	}
	
	private static abstract class CommonLayout implements MarkupLayout {
		protected static final int DISPLAY_NONE = 0;
		protected static final int DISPLAY_INLINE = 1;
		protected static final int DISPLAY_BLOCK = 2;
		protected static final int DISPLAY_LIST_ITEM = 3;
		protected static final int DISPLAY_TABLE_ROW = 4;
		protected static final int DISPLAY_TABLE_CELL = 5;		
		protected static final int DISPLAY_TABLE = 6;				
		
		private final int display;
		
		public CommonLayout(int defaultDisplay) {
			this.display = defaultDisplay;
		}

		public void layoutMarkup(RBlockViewport bodyLayout, HTMLElementImpl markupElement) {
			RenderState rs = markupElement.getRenderState();
			int display = rs == null ? this.display : rs.getDisplay();
			if(display == RenderState.DISPLAY_INLINE) {
				// Inline elements with absolute or fixed positions need
				// to be treated as blocks.
				int position = rs == null ? RenderState.POSITION_STATIC : rs.getPosition();
				if(position == RenderState.POSITION_ABSOLUTE || position == RenderState.POSITION_FIXED) {
					display = RenderState.DISPLAY_BLOCK;
				}				
				else {
					int boxFloat = rs == null ? RenderState.FLOAT_NONE : rs.getFloat();
					if(boxFloat != RenderState.FLOAT_NONE) {
						display = RenderState.DISPLAY_BLOCK;
					}
				}
			}
			switch(display) {
				case DISPLAY_NONE:
					// skip it completely.
					UINode node = markupElement.getUINode();
					if(node instanceof BaseBoundableRenderable) {
						// This is necessary so that if the element is made
						// visible again, it can be invalidated.
						((BaseBoundableRenderable) node).markLayoutValid();
					}
					break;
				case DISPLAY_BLOCK:
					bodyLayout.layoutRBlock( markupElement);
					break;
				case DISPLAY_LIST_ITEM:
					String tagName = markupElement.getTagName();
					if("UL".equalsIgnoreCase(tagName) || "OL".equalsIgnoreCase(tagName)) {
						bodyLayout.layoutList( markupElement);
					}
					else {
						bodyLayout.layoutListItem( markupElement);
					}
					break;
				case DISPLAY_TABLE:
					bodyLayout.layoutRTable( markupElement);
					break;
				default:
					// Assume INLINE
					bodyLayout.layoutMarkup( markupElement);
					break;
			}
		}		
	}
	
	public boolean isContainedByNode() {
		return false;
	}

	public String toString() {
		return "RBlockViewport[node=" + this.modelNode + "]";
	}
	
//	/**
//	 * Performs layout adjustment step. 
//	 * @param desiredWidth The desired viewport width, including padding.
//	 * @param desiredHeight The desired viewport height, including padding.
//	 * @param paddingInsets The padding insets.
//	 * @param floatBounds The starting float bounds, including floats 
//	 *                    in ancestors.
//	 */
//	public void adjust(int desiredWidth, int desiredHeight, Insets paddingInsets, FloatingBounds floatBounds) {
//		// Initializations
//		this.paddingInsets = paddingInsets;
//		this.desiredHeight = desiredHeight;
//		this.desiredWidth = desiredWidth;
//		this.floatBounds = floatBounds;
//
//		int availw = desiredWidth - paddingInsets.left - paddingInsets.right;
//		if(availw < 0) {
//			availw = 0;
//		}
//		int availh = desiredHeight - paddingInsets.top - paddingInsets.bottom;
//		if(availh < 0) {
//			availh = 0;
//		}
//		this.availContentWidth = availw;
//		this.availContentHeight = availh;
//
//		// maxX and maxY should not be reset by layoutPass.
//		this.maxX = paddingInsets.left;
//		this.maxY = paddingInsets.top;
//
//		// Keep copy of old sequential renderables,
//		// and clear the list.
//		ArrayList oldSeqRenderables = this.seqRenderables;
//		this.seqRenderables = null;
//		
//		// Clear current line
//		this.currentLine = null;
//		
//		// Reprocess all sequential renderables
//		if(oldSeqRenderables != null) {
//			Iterator i = oldSeqRenderables.iterator();
//			while(i.hasNext()) {
//				Renderable r = (Renderable) i.next();
//				this.reprocessSeqRenderable(r);
//			}
//		}
//
//		RLine lastLine = this.currentLine;
//
//		// This adds any pending floats
//		this.lineDone(this.currentLine);
//
//		// Calculate maxX and maxY.
//		if(lastLine != null) {
//			Rectangle lastBounds = lastLine.getBounds();
//			int lastTopX = lastBounds.x + lastBounds.width;
//			if(lastTopX > this.maxX) {
//				this.maxX = lastTopX;
//			}
//			int lastTopY = lastBounds.y + lastBounds.height;
//			int maxY = this.maxY;
//			if(lastTopY > maxY) {
//				this.maxY = maxY = lastTopY;
//			}
//		}
//		
//		// Check positioned renderables for maxX and maxY
//		SortedSet posRenderables = this.positionedRenderables;
//		if(posRenderables != null) {
//			Iterator i = posRenderables.iterator();
//			while(i.hasNext()) {
//				PositionedRenderable pr = (PositionedRenderable) i.next();
//				BoundableRenderable br = pr.renderable;
//				if(br.getX() + br.getWidth() > this.maxX) {
//					this.maxX = br.getX() + br.getWidth();
//				}
//				if(br.getY() + br.getHeight() > this.maxY) {
//					this.maxY = br.getY() + br.getHeight();
//				}
//			}
//		}
//		
//		this.width = paddingInsets.right + this.maxX;
//		this.height = paddingInsets.bottom + maxY;		
//	}
	
//	private void reprocessSeqRenderable(Renderable r) {
//		if(r instanceof RLine) {
//			this.reprocessLine((RLine) r);
//		}
//		else if(r instanceof RElement) {
//			this.reprocessElement((RElement) r);
//		}
//		else if(r instanceof RRelative) {
//			this.reprocessRelative((RRelative) r);
//		}
//		else {
//			throw new IllegalStateException("Unexpected Renderable: " + r);
//		}
//	}
//	
//	private void reprocessLine(RLine line) {
//		Iterator renderables = line.getRenderables();
//		if(renderables != null) {
//			while(renderables.hasNext()) {
//				Renderable r = (Renderable) renderables.next();
//                if(this.currentLine == null) {
//                    // Must add at this point in case there was a float.
//                    this.currentLine = this.addLine(r.getModelNode(), null, this.paddingInsets.top);
//                }
//				if(r instanceof RWord) {
//					RWord word = (RWord) r;
//					this.addWordToLine(word);
//				}
//				else if (r instanceof RFloatInfo) { 
//					RFloatInfo oldr = (RFloatInfo) r;
//					// Switch to a float info with registerElement=true.
//					this.scheduleFloat(new RFloatInfo(oldr.getModelNode(), oldr.getRenderable(), oldr.isLeftFloat()));
//				}
//				else if (r instanceof RStyleChanger) {
//				    RStyleChanger sc = (RStyleChanger) r;
//				    RenderState rs = sc.getModelNode().getRenderState();
//				    int whiteSpace = rs == null ? RenderState.WS_NORMAL : rs.getWhiteSpace();
//				    boolean isAO = this.currentLine.isAllowOverflow();
//				    if(!isAO && whiteSpace == RenderState.WS_NOWRAP) {
//				        this.currentLine.setAllowOverflow(true);
//				    }
//				    else if(isAO && whiteSpace != RenderState.WS_NOWRAP) {
//				        this.currentLine.setAllowOverflow(false);
//				    }
//                    this.addRenderableToLine(r);
//				}
//				else {
//					this.addRenderableToLine(r);
//				}
//			}
//		}
//		LineBreak br = line.getLineBreak();
//		if(br != null) {
//			this.addLineBreak(br.getModelNode(), br.getBreakType());
//		}
//	} 
//
//	private void reprocessElement(RElement element) {
//		RLine line = this.currentLine;
//		this.lineDone(line);
//		boolean isRBlock = element instanceof RBlock;
//		boolean obeysFloats = !isRBlock || !((RBlock) element).isOverflowVisibleY() || !((RBlock) element).isOverflowVisibleY();
//		if(obeysFloats) {
//			if(isRBlock) {
//				RBlock block = (RBlock) element;
//				int newLineY = line == null ? this.paddingInsets.top : line.y + line.height;
//				int leftOffset = this.fetchLeftOffset(newLineY);
//				int rightOffset = this.fetchRightOffset(newLineY);
//				int availContentWidth = this.desiredWidth - leftOffset - rightOffset;				
//				block.adjust(availContentWidth, this.availContentHeight, true, false, null, true);
//				// Because a block that obeys margins is also a float limit,
//				// we don't expect exported float bounds.
//			}
//			else if(element instanceof RTable) {
//			    RTable table = (RTable) element;
//                int newLineY = line == null ? this.paddingInsets.top : line.y + line.height;
//                int leftOffset = this.fetchLeftOffset(newLineY);
//                int rightOffset = this.fetchRightOffset(newLineY);
//                int availContentWidth = this.desiredWidth - leftOffset - rightOffset;               
//			    table.adjust(availContentWidth, this.availContentHeight);
//			}
//		}
//		else {
//			RBlock block = (RBlock) element;
//			final FloatingBounds currentFloatBounds = this.floatBounds;
//			FloatingBoundsSource blockFloatBoundsSource = null;
//			if(currentFloatBounds != null) {
//				Insets paddingInsets = this.paddingInsets;
//				final int blockShiftX = paddingInsets.left;
//				final int blockShiftRight = paddingInsets.right;
//				final int blockShiftY = line == null ? paddingInsets.top : line.y + line.height;
//				final int expectedBlockWidth = this.availContentWidth;
//				blockFloatBoundsSource = new FloatingBoundsSource() {
//					public FloatingBounds getChildBlockFloatingBounds(int apparentBlockWidth) {
//						int actualRightShift = blockShiftRight + (expectedBlockWidth - apparentBlockWidth);
//						return new ShiftedFloatingBounds(currentFloatBounds, -blockShiftX, -actualRightShift, -blockShiftY);
//					}
//				};
//			}
//			block.adjust(this.availContentWidth, this.availContentHeight, true, false, blockFloatBoundsSource, true);
//			FloatingBounds blockBounds = block.getExportableFloatingBounds();
//			if(blockBounds != null) {
//				FloatingBounds prevBounds = this.floatBounds;
//				FloatingBounds newBounds;
//				if(prevBounds == null) {
//					newBounds = blockBounds;
//				}
//				else {
//					newBounds = new CombinedFloatingBounds(prevBounds, blockBounds);
//				}
//				if(newBounds.getMaxY() > this.maxY && this.isFloatLimit()) {
//					this.maxY = newBounds.getMaxY();
//				}
//			}
//		}
//		this.addAsSeqBlock(element, obeysFloats, false);
//	}

//	private void reprocessRelative(RRelative relative) {
//		RLine line = this.currentLine;
//		this.lineDone(line);
//		boolean obeysFloats = false;
//		RElement element = relative.getElement();
//		if(element instanceof RBlock) {
//			obeysFloats = false;
//			RBlock block = (RBlock) element;
//			final FloatingBounds currentFloatBounds = this.floatBounds;
//			FloatingBoundsSource blockFloatBoundsSource = null;
//			if(currentFloatBounds != null) {
//				Insets paddingInsets = this.paddingInsets;
//				final int blockShiftX = paddingInsets.left + relative.getXOffset();
//				final int blockShiftRight = paddingInsets.right - relative.getXOffset();
//				final int blockShiftY = (line == null ? paddingInsets.top : line.y + line.height) + relative.getYOffset();
//				final int expectedBlockWidth = this.availContentWidth;
//				blockFloatBoundsSource = new FloatingBoundsSource() {
//					public FloatingBounds getChildBlockFloatingBounds(int apparentBlockWidth) {
//						int actualRightShift = blockShiftRight + (expectedBlockWidth - apparentBlockWidth);
//						return new ShiftedFloatingBounds(currentFloatBounds, -blockShiftX, -actualRightShift, -blockShiftY);
//					}
//				};
//			}
//			block.adjust(this.availContentWidth, this.availContentHeight, true, false, blockFloatBoundsSource, true);
//			relative.assignDimension();
//			FloatingBounds blockBounds = relative.getExportableFloatingBounds();
//			if(blockBounds != null) {
//				FloatingBounds prevBounds = this.floatBounds;
//				FloatingBounds newBounds;
//				if(prevBounds == null) {
//					newBounds = blockBounds;
//				}
//				else {
//					newBounds = new CombinedFloatingBounds(prevBounds, blockBounds);
//				}
//				if(newBounds.getMaxY() > this.maxY && this.isFloatLimit()) {
//					this.maxY = newBounds.getMaxY();
//				}
//			}
//		}
//		else {
//			obeysFloats = true;
//		}
//		this.addAsSeqBlock(relative, obeysFloats, false);
//	}

	private void scheduleFloat(RFloatInfo floatInfo) {
		RLine line = this.currentLine;
		if(line == null) {
			int y = line == null ? this.paddingInsets.top : line.getY();
			this.placeFloat(floatInfo.getRenderable(), y, floatInfo.isLeftFloat());
		}
		else if(line.getWidth() == 0) {
			int y = line.getY();
			this.placeFloat(floatInfo.getRenderable(), y, floatInfo.isLeftFloat());			
			int leftOffset = this.fetchLeftOffset(y);
			int rightOffset = this.fetchRightOffset(y);
			line.changeLimits(leftOffset, this.desiredWidth - leftOffset - rightOffset);
		}
		else {
			// These pending floats are positioned when
			// lineDone() is called. 
			Collection c = this.pendingFloats;
			if(c == null) {
				c = new LinkedList();
				this.pendingFloats = c;
			}
			c.add(floatInfo);
		}
	}

	private Collection pendingFloats = null;
	
	private void lineDone(RLine line) {
		int yAfterLine = line == null ? this.paddingInsets.top : line.y + line.height;
		Collection pfs = this.pendingFloats;
		if(pfs != null) {
			this.pendingFloats = null;
			Iterator i = pfs.iterator();
			while(i.hasNext()) {
				RFloatInfo pf = (RFloatInfo) i.next();
				this.placeFloat(pf.getRenderable(), yAfterLine, pf.isLeftFloat());
			}
		}
	}
	
	private void addExportableFloat(BoundableRenderable element, boolean leftFloat, int origX, int origY) {
	    ArrayList ep = this.exportableFloats;
	    if(ep == null) {
	        ep = new ArrayList(1);
	        this.exportableFloats = ep;
	    }
	    ep.add(new ExportableFloat(element, leftFloat, origX, origY));
	}
	
	/**
	 * 
	 * @param element
	 * @param y The desired top position of the float element.
	 * @param floatType -1 (left) or +1 (right)
	 */
	private void placeFloat(BoundableRenderable element, int y, boolean leftFloat) {
		Insets insets = this.paddingInsets;
		int boxY = y;
		int boxWidth = element.getWidth();
		int boxHeight = element.getHeight();
		int desiredWidth = this.desiredWidth;
		int boxX;
		for(;;) {
			int leftOffset = this.fetchLeftOffset(boxY);
			int rightOffset = this.fetchRightOffset(boxY);
			boxX = leftFloat ? leftOffset : desiredWidth - rightOffset - boxWidth; 
			if(leftOffset == insets.left && rightOffset == insets.right) {
				// Probably typical scenario. If it's overflowing to the left, 
			    // we need to correct.
			    if(!leftFloat && boxX < leftOffset) {
			        boxX = leftOffset;
			    }
			    break;
			}
			if(boxWidth <= desiredWidth - rightOffset - leftOffset) {
			    // Size is fine.
			    break;
			}
			// At this point the float doesn't fit at the current Y position.
			if(element instanceof RBlock) {
                // Try shrinking it.
			    RBlock relement = (RBlock) element;
			    if(!relement.hasDeclaredWidth()) {
			        int availableBoxWidth = desiredWidth - rightOffset - leftOffset;
			        relement.layout(availableBoxWidth, this.availContentHeight, this.sizeOnly);
			        if(relement.getWidth() < boxWidth) {
			            if(relement.getWidth() > desiredWidth - rightOffset - leftOffset) {
			                // Didn't work out. Put it back the way it was. 
			                relement.layout(this.availContentWidth, this.availContentHeight, this.sizeOnly);
			            }
			            else {
			                // Retry
	                        boxWidth = relement.getWidth();
	                        boxHeight = relement.getHeight();
			                continue;
			            }
			        }
			    }			    
			}			
			FloatingBounds fb = this.floatBounds;
			int newY = fb == null ? boxY + boxHeight : fb.getFirstClearY(boxY);
			if(newY == boxY) {
				// Possible if prior box has height zero?
				break;
			}
			boxY = newY;
		}
		// Position element
		element.setOrigin(boxX, boxY);
		// Update float bounds accordingly
		int offsetFromBorder = leftFloat ? boxX + boxWidth : desiredWidth - boxX;
		this.floatBounds = new FloatingViewportBounds(this.floatBounds, leftFloat, boxY, offsetFromBorder, boxHeight);
		// Add element to collection
		boolean isFloatLimit = this.isFloatLimit();
		if(isFloatLimit) {
		    this.addPositionedRenderable(element, true, true);
		}
		else {
		    this.addExportableFloat(element, leftFloat, boxX, boxY);
		}
		// Adjust maxX based on float.
		if(boxX + boxWidth > this.maxX) {
			this.maxX = boxX + boxWidth;
		}
		// Adjust maxY based on float, but only if this viewport is the float limit.
		if(this.isFloatLimit()) {
			if(boxY + boxHeight > this.maxY) {
				this.maxY = boxY + boxHeight;
			}
		}
	}

	private Boolean isFloatLimit = null;
	
	private boolean isFloatLimit() {
		Boolean fl = this.isFloatLimit;
		if(fl == null) {
			fl = this.isFloatLimitImpl();
			this.isFloatLimit = fl;
		}
		return fl.booleanValue();
	}

	private Boolean isFloatLimitImpl() {
		Object parent = this.getOriginalOrCurrentParent();
		if(!(parent instanceof RBlock)) {
			return Boolean.TRUE;
		}
		RBlock blockParent = (RBlock) parent;
		Object grandParent = blockParent.getOriginalOrCurrentParent();
		if(!(grandParent instanceof RBlockViewport)) {
			// Could be contained in a table, or it could
			// be a list item, for example.
			return Boolean.TRUE;
		}
		ModelNode node = this.modelNode;
		if(!(node instanceof HTMLElementImpl)) {
			// Can only be a document here.
			return Boolean.TRUE;
		}
		HTMLElementImpl element = (HTMLElementImpl) node;
		int position = getPosition(element);
		if(position == RenderState.POSITION_ABSOLUTE || position == RenderState.POSITION_FIXED) {
			return Boolean.TRUE;
		}
		AbstractCSS2Properties props = element.getCurrentStyle();
		RenderState rs = element.getRenderState();
		int floatValue = rs == null ? RenderState.FLOAT_NONE : rs.getFloat();
		if(floatValue != RenderState.FLOAT_NONE) {
			return Boolean.TRUE;
		}
		int overflowX = rs == null ? RenderState.OVERFLOW_NONE : rs.getOverflowX();
		int overflowY = rs == null ? RenderState.OVERFLOW_NONE : rs.getOverflowY();
		if(overflowX == RenderState.OVERFLOW_AUTO || overflowX == RenderState.OVERFLOW_SCROLL || overflowY == RenderState.OVERFLOW_AUTO || overflowY == RenderState.OVERFLOW_SCROLL) {
			return Boolean.TRUE;
		}
		return Boolean.FALSE;
	}
	
//	/**
//	 * Gets FloatingBounds from this viewport that should
//	 * be considered by an ancestor block.
//	 */
//	public FloatingBounds getExportableFloatingBounds() {
//		FloatingBounds floatBounds = this.floatBounds;
//		if(floatBounds == null) {
//			return null;
//		}
//		if(this.isFloatLimit()) {
//			return null;
//		}
//		int maxY = floatBounds.getMaxY();
//		if(maxY > this.height) {
//			return floatBounds;
//		}
//		return null;
//	}	
	
	public FloatingInfo getExportableFloatingInfo() {
	    ArrayList ef = this.exportableFloats;
	    if(ef == null) {
	        return null;
	    }
	    ExportableFloat[] floats = (ExportableFloat[]) ef.toArray(ExportableFloat.EMPTY_ARRAY);
	    return new FloatingInfo(0, 0, floats);	    
	}
	
	private void importFloatingInfo(FloatingInfo floatingInfo, BoundableRenderable block) {
	    int shiftX = floatingInfo.shiftX + block.getX();
	    int shiftY = floatingInfo.shiftY + block.getY();
	    ExportableFloat[] floats = floatingInfo.floats;
	    int length = floats.length;
	    for(int i = 0; i < length; i++) {
	        ExportableFloat ef = floats[i];
	        this.importFloat(ef, shiftX, shiftY);
	    }
	}
	
	private void importFloat(ExportableFloat ef, int shiftX, int shiftY) {
	    BoundableRenderable renderable = ef.element;
	    int newX = ef.origX + shiftX;
	    int newY = ef.origY + shiftY;
	    renderable.setOrigin(newX, newY);
	    FloatingBounds prevBounds = this.floatBounds;
	    int offsetFromBorder;
	    boolean leftFloat = ef.leftFloat;
	    if(leftFloat) {
	        offsetFromBorder = newX + renderable.getWidth();
	    }
	    else {
	        offsetFromBorder = this.desiredWidth - newX; 
	    }
        this.floatBounds = new FloatingViewportBounds(prevBounds, leftFloat, newY, offsetFromBorder, renderable.getHeight());
        if(this.isFloatLimit()) {
            this.addPositionedRenderable(renderable, true, true);
        }
        else {
            this.addExportableFloat(renderable, leftFloat, newX, newY);
        }
	}
}
