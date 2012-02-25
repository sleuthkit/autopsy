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

import java.awt.*;
import java.util.*;
import org.lobobrowser.html.domimpl.ModelNode;
import org.lobobrowser.html.style.RenderState;

/**
 * @author J. H. S.
 */
class RLine extends BaseRCollection {
	private final ArrayList renderables = new ArrayList(8);
	//private final RenderState startRenderState;
	private int baseLineOffset;
	private int desiredMaxWidth;
	
	/**
	 * Offset where next renderable should be placed.
	 * This can be different to width.
	 */
	private int xoffset;
	
	private boolean allowOverflow = false;
	private boolean firstAllowOverflowWord = false;
	
	public RLine(ModelNode modelNode, RenderableContainer container, int x, int y, int desiredMaxWidth, int height, boolean initialAllowOverflow) {
		// Note that in the case of RLine, modelNode is the context node
		// at the beginning of the line, not a node that encloses the whole line.
		super(container, modelNode);
		this.x = x;
		this.y = y;
		this.height = height;
		this.desiredMaxWidth = desiredMaxWidth;
		// Layout here can always be "invalidated"
		this.layoutUpTreeCanBeInvalidated = true;
		this.allowOverflow = initialAllowOverflow;
	}
	
	
	public void setAllowOverflow(boolean flag) {
	    if(flag != this.allowOverflow) {
	        this.allowOverflow = flag;
	        if(flag) {
	            // Set to true only if allowOverflow was
	            // previously false.
	            this.firstAllowOverflowWord = true;
	        }
	    }
	}
	
	public boolean isAllowOverflow() {
	    return this.allowOverflow;
	}
	
	/**
	 * This method should only be invoked when the line has
	 * no items yet.
	 */
	public void changeLimits(int x, int desiredMaxWidth) {
		this.x = x;
		this.desiredMaxWidth = desiredMaxWidth;
	}
	
	public int getBaselineOffset() {
		return this.baseLineOffset;
	}
	
	protected void invalidateLayoutLocal() {		
		// Workaround for fact that RBlockViewport does not 
		// get validated or invalidated.
		this.layoutUpTreeCanBeInvalidated = true;
	}
	
	/* (non-Javadoc)
	 * @see net.sourceforge.xamj.domimpl.markup.Renderable#paint(java.awt.Graphics)
	 */

	public void paint(Graphics g) {
		// Paint according to render state of the start of line first.
		RenderState rs = this.modelNode.getRenderState();
		if(rs != null) {
			Color textColor = rs.getColor();
			g.setColor(textColor);
			Font font = rs.getFont();
			g.setFont(font);
		}
		// Note that partial paints of the line can only be done
		// if all RStyleChanger's are applied first.
		Iterator i = this.renderables.iterator();
		if(i != null) {
			while(i.hasNext()) {
				Object r = i.next();
				if(r instanceof RElement) {
					// RElement's should be clipped.
					RElement relement = (RElement) r;
					Graphics newG = g.create(relement.getX(), relement.getY(), relement.getWidth(), relement.getHeight());
					try {
						relement.paint(newG);
					} finally {
						newG.dispose();
					}
				}
				else if(r instanceof BoundableRenderable) {
					BoundableRenderable br = (BoundableRenderable) r;
					br.paintTranslated(g);
				}
				else {
					((Renderable) r).paint(g);
				}
			}
		}
	}
		
	public boolean extractSelectionText(StringBuffer buffer, boolean inSelection, RenderableSpot startPoint, RenderableSpot endPoint) {
		boolean result = super.extractSelectionText(buffer, inSelection, startPoint, endPoint);
		if(result) {
			LineBreak br = this.lineBreak;
			if(br != null) {
				buffer.append(System.getProperty("line.separator"));
			}
			else {
				ArrayList renderables = this.renderables;
				int size = renderables.size();
				if(size > 0 && !(renderables.get(size-1) instanceof RBlank)) {
					buffer.append(" ");
				}					
			}
		}
		return result;
	}

	public final void addStyleChanger(RStyleChanger sc) {
		this.renderables.add(sc);
	}

	public final void simplyAdd(Renderable r) {
		this.renderables.add(r);
	}

	/**
	 * This method adds and positions a renderable in the line, if possible.
	 * Note that RLine does not set sizes, but only origins.
	 * @throws OverflowException Thrown if the renderable overflows the line. All overflowing renderables are added to the exception.
	 */
	public final void add(Renderable renderable) throws OverflowException {
		if(renderable instanceof RWord) {
			this.addWord((RWord) renderable);
		}
		else if(renderable instanceof RBlank) {
			this.addBlank((RBlank) renderable);
		}
		else if(renderable instanceof RUIControl) {
			this.addElement((RElement) renderable);
		}
		else if(renderable instanceof RSpacing) {
			this.addSpacing((RSpacing) renderable);
		}
		else if(renderable instanceof RStyleChanger) {
			this.addStyleChanger((RStyleChanger) renderable);
		}
		else if(renderable instanceof RFloatInfo) {
			this.simplyAdd((RFloatInfo) renderable);
		}
		else {
			throw new IllegalArgumentException("Can't add " + renderable);
		}
	}
	
	public final void addWord(RWord rword) throws OverflowException {
		// Check if it fits horzizontally
		int offset = this.xoffset;
		int wiwidth = rword.width;
		boolean allowOverflow = this.allowOverflow;
		boolean firstAllowOverflowWord = this.firstAllowOverflowWord;
        if(allowOverflow && firstAllowOverflowWord) {
            this.firstAllowOverflowWord = false;
        }
		if((!allowOverflow || firstAllowOverflowWord) && offset != 0 && (offset + wiwidth > this.desiredMaxWidth)) {
			ArrayList renderables = this.renderables;
			ArrayList overflow = null;
			boolean cancel = false;
			// Check if other words need to be overflown (for example,
			// a word just before a markup tag adjacent to the word
			// we're trying to add). An RBlank between words prevents
			// a word from being overflown to the next line (and this
			// is the usefulness of RBlank.)
			int newOffset = offset;
			int newWidth = offset;
			for(int i = renderables.size(); --i >= 0;) {
				Renderable renderable = (Renderable) renderables.get(i);
				if(renderable instanceof RWord || !(renderable instanceof BoundableRenderable)) {
					if(overflow == null) {
						overflow = new ArrayList();
					}
					if(renderable != rword && renderable instanceof RWord && ((RWord) renderable).getX() == 0) {
						// Can't overflow words starting at offset zero.
						// Note that all or none should be overflown.
						cancel = true;
						// No need to set offset - set later.
						break;
					}
					overflow.add(0, renderable);
					renderables.remove(i);
				}
				else {
					if(renderable instanceof RBlank) {
						RBlank rblank = (RBlank) renderable;
						newWidth = rblank.getX();
						newOffset = newWidth + rblank.getWidth();
					}
					else {
						BoundableRenderable br = (BoundableRenderable) renderable;
						newWidth = newOffset = br.getX() + br.getWidth();
					}
					break;
				}
			}
			if(cancel) {
			   // Oops. Need to undo overflow.
			   if(overflow != null) {
			       Iterator i = overflow.iterator();
			       while(i.hasNext()) {
			          renderables.add(i.next()); 
			       }
			   }
			}
			else {
				this.xoffset = newOffset;
				this.width = newWidth;
				if(overflow == null) {
					throw new OverflowException(Collections.singleton(rword));
				}
				else {
					overflow.add(rword);
					throw new OverflowException(overflow);
				}
			}
		}

		// Add it
		
		int extraHeight = 0;
		int maxDescent = this.height - this.baseLineOffset;
		if(rword.descent > maxDescent) {
			extraHeight += (rword.descent - maxDescent);
		}
		int maxAscentPlusLeading = this.baseLineOffset;
		if(rword.ascentPlusLeading > maxAscentPlusLeading) {
			extraHeight += (rword.ascentPlusLeading - maxAscentPlusLeading);
		}
		if(extraHeight > 0) {
			int newHeight = this.height + extraHeight;
			this.adjustHeight(newHeight, newHeight, RElement.VALIGN_ABSBOTTOM);
		}
		this.renderables.add(rword);
		rword.setParent(this);
		int x = offset;
		offset += wiwidth;
		this.width = this.xoffset = offset;
		rword.setOrigin(x, this.baseLineOffset - rword.ascentPlusLeading);
	}

	public final void addBlank(RBlank rblank) {
		//NOTE: Blanks may be added without concern for wrapping (?)
		int x = this.xoffset;
		int width = rblank.width;
		rblank.setOrigin(x, this.baseLineOffset - rblank.ascentPlusLeading);
		this.renderables.add(rblank);
		rblank.setParent(this);
		// Only move xoffset, but not width
		this.xoffset = x + width;
	}

	public final void addSpacing(RSpacing rblank) {
		//NOTE: Spacing may be added without concern for wrapping (?)
		int x = this.xoffset;
		int width = rblank.width;
		rblank.setOrigin(x, (this.height - rblank.height) / 2);
		this.renderables.add(rblank);
		rblank.setParent(this);
		this.width = this.xoffset = x + width;
	}

	/**
	 * 
	 * @param relement
	 * @param x
	 * @param elementHeight The required new line height.
	 * @param valign
	 */
	private final void setElementY(RElement relement, int elementHeight, int valign) {
		// At this point height should be more than what's needed.
		int yoffset;
		switch(valign) {
		case RElement.VALIGN_ABSBOTTOM:
			yoffset = this.height - elementHeight;
			break;
		case RElement.VALIGN_ABSMIDDLE:
			yoffset = (this.height - elementHeight) / 2;
			break;
		case RElement.VALIGN_BASELINE:
		case RElement.VALIGN_BOTTOM:
			yoffset = this.baseLineOffset - elementHeight;
			break;
		case RElement.VALIGN_MIDDLE:
			yoffset = this.baseLineOffset - elementHeight / 2;
			break;
		case RElement.VALIGN_TOP:
			yoffset = 0;
			break;
		default:
			yoffset = this.baseLineOffset - elementHeight;
		}
		//RLine only sets origins, not sizes.
		//relement.setBounds(x, yoffset, width, height);
		relement.setY(yoffset);
	}
	
	private final void addElement(RElement relement) throws OverflowException {
		// Check if it fits horizontally
		int origXOffset = this.xoffset;
		int desiredMaxWidth = this.desiredMaxWidth;
		int pw = relement.getWidth();
        boolean allowOverflow = this.allowOverflow;
        boolean firstAllowOverflowWord = this.firstAllowOverflowWord;
        if(allowOverflow && firstAllowOverflowWord) {
            this.firstAllowOverflowWord = false;
        }
		if((!allowOverflow || firstAllowOverflowWord) && origXOffset != 0  && (origXOffset + pw > desiredMaxWidth)) {
			throw new OverflowException(Collections.singleton(relement));
		}
		//Note: Renderable for widget doesn't paint the widget, but
		//it's needed for height readjustment.
		int boundsh = this.height;
		int ph = relement.getHeight();
		int requiredHeight;
		int valign = relement.getVAlign();
		switch(valign) {
		case RElement.VALIGN_BASELINE:
		case RElement.VALIGN_BOTTOM:
			requiredHeight = ph + (boundsh - this.baseLineOffset);
			break;
		case RElement.VALIGN_MIDDLE:
			requiredHeight = Math.max(ph, ph / 2 + (boundsh - this.baseLineOffset));
			break;
		default:
			requiredHeight = ph;
			break;
		}
		if(requiredHeight > boundsh) {
			// Height adjustment depends on bounds being already set.
			this.adjustHeight(requiredHeight, ph, valign);
		}
		this.renderables.add(relement);		
		relement.setParent(this);
		relement.setX(origXOffset);
		this.setElementY(relement, ph, valign);
		int newX = origXOffset + pw;
		this.width = this.xoffset = newX;
	}

//	/**
//	 * Positions line elements vertically.
//	 */
//	public final void positionVertically() {		
//		ArrayList renderables = this.renderables;
//
//		// Find word maximum metrics.
//		int maxDescent = 0;
//		int maxAscentPlusLeading = 0;
//		int maxWordHeight = 0;
//		for(Iterator i = renderables.iterator(); i.hasNext(); ) {
//			Renderable r = (Renderable) i.next();
//			if(r instanceof RWord) {
//				RWord rword = (RWord) r;
//				int descent = rword.descent;
//				if(descent > maxDescent) {
//					maxDescent = descent;
//				}
//				int ascentPlusLeading = rword.ascentPlusLeading;
//				if(ascentPlusLeading > maxAscentPlusLeading) {
//					maxAscentPlusLeading = ascentPlusLeading;
//				}
//				if(rword.height > maxWordHeight) {
//					maxWordHeight = rword.height;
//				}
//			}
//		}
//
//		// Determine proper baseline
//		int lineHeight = this.height;
//		int baseLine = lineHeight - maxDescent;
//		for(Iterator i = renderables.iterator(); i.hasNext(); ) {
//			Renderable r = (Renderable) i.next();
//			if(r instanceof RElement) {
//				RElement relement = (RElement) r;
//				switch(relement.getVAlign()) {
//				case RElement.VALIGN_ABSBOTTOM:
//					//TODO
//					break;
//				case RElement.VALIGN_ABSMIDDLE:
//					int midWord = baseLine + maxDescent - maxWordHeight / 2; 
//					int halfElementHeight = relement.getHeight() / 2;
//					if(midWord + halfElementHeight > lineHeight) {
//						// Change baseLine
//						midWord = lineHeight - halfElementHeight;
//						baseLine = midWord + maxWordHeight / 2 - maxDescent;
//					}
//					else if(midWord - halfElementHeight < 0) {
//						midWord = halfElementHeight;
//						baseLine = midWord + maxWordHeight / 2 - maxDescent;
//					}
//					else {
//						relement.setY(midWord - halfElementHeight);
//					}
//					break;
//				}
//			}
//		}
//				
//	}
	
	/**
	 * Rearrange line elements based on a new line height and
	 * alignment provided. All line elements are expected to
	 * have bounds preset.
	 * @param newHeight
	 * @param alignmentY
	 */
	private void adjustHeight(int newHeight, int elementHeight, int valign) {
		// Set new line height
		//int oldHeight = this.height;
		this.height = newHeight;
		ArrayList renderables = this.renderables;
		// Find max baseline 
		FontMetrics firstFm = this.modelNode.getRenderState().getFontMetrics();
		int maxDescent = firstFm.getDescent();
		int maxAscentPlusLeading = firstFm.getAscent() + firstFm.getLeading();
		for(Iterator i = renderables.iterator(); i.hasNext();) {
			Object r = i.next();
			if(r instanceof RStyleChanger) {
				RStyleChanger rstyleChanger = (RStyleChanger) r;
				FontMetrics fm = rstyleChanger.getModelNode().getRenderState().getFontMetrics();
				int descent = fm.getDescent();
				if(descent > maxDescent) {
					maxDescent = descent;
				}
				int ascentPlusLeading = fm.getAscent() + fm.getLeading();
				if(ascentPlusLeading > maxAscentPlusLeading) {
					maxAscentPlusLeading = ascentPlusLeading;
				}
			}
		}
		int textHeight = maxDescent + maxAscentPlusLeading;
		
		//TODO: Need to take into account previous RElement's and
		//their alignments?
				
		int baseline;
		switch(valign) {
		case RElement.VALIGN_ABSBOTTOM:
			baseline = newHeight - maxDescent;
			break;
		case RElement.VALIGN_ABSMIDDLE:
			baseline = (newHeight + textHeight) / 2 - maxDescent;
			break;
		case RElement.VALIGN_BASELINE:
		case RElement.VALIGN_BOTTOM:
			baseline = elementHeight;
			break;
		case RElement.VALIGN_MIDDLE:
			baseline = newHeight / 2;
			break;
		case RElement.VALIGN_TOP:
			baseline = maxAscentPlusLeading;
			break;
		default:
			baseline = elementHeight;
			break;
		}
		this.baseLineOffset = baseline;
		
		// Change bounds of renderables accordingly
		for(Iterator i = renderables.iterator(); i.hasNext();) {
			Object r = i.next();
			if(r instanceof RWord) {
				RWord rword = (RWord) r;
				rword.setY(baseline - rword.ascentPlusLeading);
			}
			else if(r instanceof RBlank) {
				RBlank rblank = (RBlank) r;
				rblank.setY(baseline - rblank.ascentPlusLeading);
			}
			else if(r instanceof RElement) {
				RElement relement = (RElement) r;
				//int w = relement.getWidth();
				this.setElementY(relement, relement.getHeight(), relement.getVAlign());
			}
			else {
				// RSpacing and RStyleChanger don't matter?
			}
		}
		//TODO: Could throw OverflowException when we add floating widgets
	}
	
	public boolean onMouseClick(java.awt.event.MouseEvent event, int x, int y) {
		Renderable[] rarray = (Renderable[]) this.renderables.toArray(Renderable.EMPTY_ARRAY);
		BoundableRenderable r = MarkupUtilities.findRenderable(rarray, x, y, false);
		if(r != null) {
			Rectangle rbounds = r.getBounds();
			return r.onMouseClick(event, x - rbounds.x, y - rbounds.y);
		}
		else {
			return true;
		}
	}
	
	public boolean onDoubleClick(java.awt.event.MouseEvent event, int x, int y) {
		Renderable[] rarray = (Renderable[]) this.renderables.toArray(Renderable.EMPTY_ARRAY);
		BoundableRenderable r = MarkupUtilities.findRenderable(rarray, x, y, false);
		if(r != null) {
			Rectangle rbounds = r.getBounds();
			return r.onDoubleClick(event, x - rbounds.x, y - rbounds.y);
		}
		else {
			return true;
		}
	}
	
    private BoundableRenderable mousePressTarget;
    
	public boolean onMousePressed(java.awt.event.MouseEvent event, int x, int y) {
		Renderable[] rarray = (Renderable[]) this.renderables.toArray(Renderable.EMPTY_ARRAY);
		BoundableRenderable r = MarkupUtilities.findRenderable(rarray, x, y, false);
		if(r != null) {
			this.mousePressTarget = r;
			Rectangle rbounds = r.getBounds();
			return r.onMousePressed(event, x - rbounds.x, y - rbounds.y);
		}
		else {
			return true;
		}
	}
	
	public RenderableSpot getLowestRenderableSpot(int x, int y) {
		Renderable[] rarray = (Renderable[]) this.renderables.toArray(Renderable.EMPTY_ARRAY);
		BoundableRenderable br = MarkupUtilities.findRenderable(rarray, x, y, false);
		if(br != null) {
			Rectangle rbounds = br.getBounds();
			return br.getLowestRenderableSpot(x - rbounds.x, y - rbounds.y);
		}
		else {
			return new RenderableSpot(this, x, y);
		}
	}

	public boolean onMouseReleased(java.awt.event.MouseEvent event, int x, int y) {
		Renderable[] rarray = (Renderable[]) this.renderables.toArray(Renderable.EMPTY_ARRAY);
		BoundableRenderable r = MarkupUtilities.findRenderable(rarray, x, y, false);
		if(r != null) {
			Rectangle rbounds = r.getBounds();
	    	BoundableRenderable oldArmedRenderable = this.mousePressTarget;
	    	if(oldArmedRenderable != null && r != oldArmedRenderable) {
	    		oldArmedRenderable.onMouseDisarmed(event);
	    		this.mousePressTarget = null;
	    	}
			return r.onMouseReleased(event, x - rbounds.x, y - rbounds.y);
		}
		else {
	    	BoundableRenderable oldArmedRenderable = this.mousePressTarget;
	    	if(oldArmedRenderable != null) {
	    		oldArmedRenderable.onMouseDisarmed(event);
	    		this.mousePressTarget = null;
	    	}			
	    	return true;
		}
	}
	
	public boolean onMouseDisarmed(java.awt.event.MouseEvent event) {
		BoundableRenderable target = this.mousePressTarget;
		if(target != null) {
			this.mousePressTarget = null;
			return target.onMouseDisarmed(event);
		}
		else {
			return true;
		}
	}
	
	public Color getBlockBackgroundColor() {
		return this.container.getPaintedBackgroundColor();
	}
	
//	public final void adjustHorizontalBounds(int newX, int newMaxWidth) throws OverflowException {
//		this.x = newX;
//		this.desiredMaxWidth = newMaxWidth;
//		int topX = newX + newMaxWidth;
//		ArrayList renderables = this.renderables;
//		int size = renderables.size();
//		ArrayList overflown = null;
//		Rectangle lastInLine = null;
//		for(int i = 0; i < size; i++) {
//			Object r = renderables.get(i);
//			if(overflown == null) {
//				if(r instanceof BoundableRenderable) {
//					BoundableRenderable br = (BoundableRenderable) r;
//					Rectangle brb = br.getBounds();
//					int x2 = brb.x + brb.width;
//					if(x2 > topX) {
//						overflown = new ArrayList(1);
//					}
//					else {
//						lastInLine = brb;
//					}
//				}
//			}
//			/* must not be else here */
//			if(overflown != null) {
//				//TODO: This could break a word across markup boundary.
//				overflown.add(r);
//				renderables.remove(i--);
//				size--;
//			}
//		}
//		if(overflown != null) {
//			if(lastInLine != null) {
//				this.width = this.xoffset = lastInLine.x + lastInLine.width;
//			}
//			throw new OverflowException(overflown);
//		}
//	}

	/* (non-Javadoc)
	 * @see org.xamjwg.html.renderer.RCollection#getRenderables()
	 */
	public Iterator getRenderables() {
		return this.renderables.iterator();
	}
	
	public boolean isContainedByNode() {
		return false;
	}
	
	private LineBreak lineBreak;

	public LineBreak getLineBreak() {
		return lineBreak;
	}

	public void setLineBreak(LineBreak lineBreak) {
		this.lineBreak = lineBreak;
	}
	
	public boolean isEmpty() {
		return this.xoffset == 0;
	}
}
