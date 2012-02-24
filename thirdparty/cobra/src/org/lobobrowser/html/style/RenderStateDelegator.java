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
package org.lobobrowser.html.style;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;


public abstract class RenderStateDelegator implements RenderState {
	protected final RenderState delegate;

	public RenderStateDelegator(final RenderState delegate) {
		super();
		this.delegate = delegate;
	}

	public RenderState getPreviousRenderState() {
		return this.delegate;
	}

	public int getAlignXPercent() {
		return delegate.getAlignXPercent();
	}

	public int getAlignYPercent() {
		return delegate.getAlignYPercent();
	}

	public int getBlankWidth() {
		return delegate.getBlankWidth();
	}

	public Color getColor() {
		return delegate.getColor();
	}

	public Font getFont() {
		return delegate.getFont();
	}

	public int getFontBase() {
		return delegate.getFontBase();
	}

	public FontMetrics getFontMetrics() {
		return delegate.getFontMetrics();
	}

	public Color getOverlayColor() {
		return delegate.getOverlayColor();
	}

	public Color getBackgroundColor() {
		return delegate.getBackgroundColor();
	}

	public int getTextDecorationMask() {
		return delegate.getTextDecorationMask();
	}
	
	public int getTextTransform() {
		return delegate.getTextTransform();
	}

	public WordInfo getWordInfo(String word) {
		return delegate.getWordInfo(word);
	}

	public void invalidate() {
		delegate.invalidate();
	}

	public boolean isHighlight() {
		return delegate.isHighlight();
	}

	public void setHighlight(boolean highlight) {
		delegate.setHighlight(highlight);
	}

	public int getCount(String counter, int nesting) {
		return this.delegate.getCount(counter, nesting);
	}

	public void resetCount(String counter, int nesting, int value) {
		this.delegate.resetCount(counter, nesting, value);
	}

	public int incrementCount(String counter, int nesting) {
		return this.delegate.incrementCount(counter, nesting);
	}

	public BackgroundInfo getBackgroundInfo() {
		return this.delegate.getBackgroundInfo();
	}

	public int getDisplay() {
		return this.delegate.getDisplay();
	}

	public Color getTextBackgroundColor() {
		return this.delegate.getTextBackgroundColor();
	}

	public int getTextIndent(int availWidth) {
		return this.delegate.getTextIndent(availWidth);
	}

	public String getTextIndentText() {
		return this.delegate.getTextIndentText();
	}

	public int getWhiteSpace() {
		return this.delegate.getWhiteSpace();
	}

	public HtmlInsets getMarginInsets() {
		return this.delegate.getMarginInsets();
	}

	public HtmlInsets getPaddingInsets() {
		return this.delegate.getPaddingInsets();
	}

	public int getVisibility() {
		return this.delegate.getVisibility();
	}

	public int getPosition() {
		return this.delegate.getPosition();
	}
	
	public int getFloat() {
		return this.delegate.getFloat();
	}

    public int getOverflowX() {
        return this.delegate.getOverflowX();
    }

    public int getOverflowY() {
        return this.delegate.getOverflowY();
    }

    public BorderInfo getBorderInfo() {
        return this.delegate.getBorderInfo();
    }
}
