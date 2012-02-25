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

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;

public class FontNameRenderState extends RenderStateDelegator {
	private final String fontName;
	
	public FontNameRenderState(RenderState prevRenderState, String fontName) {
		super(prevRenderState);
		this.fontName = fontName;
	}

	private Font iFont;
	
	public Font getFont() {
		Font f = this.iFont;
		if(f != null) {
			return f;
		}
		Font parentFont = this.delegate.getFont();
		f = new Font(this.fontName, parentFont.getStyle(), parentFont.getSize());
		this.iFont = f;
		return f;		
	}

	private FontMetrics iFontMetrics;
	
	public FontMetrics getFontMetrics() {
		FontMetrics fm = this.iFontMetrics;
		if(fm == null) {
			//TODO getFontMetrics deprecated. How to get text width?
			fm = Toolkit.getDefaultToolkit().getFontMetrics(this.getFont());
			this.iFontMetrics = fm;
		}
		return fm;
	}

	public void invalidate() {
		super.invalidate();
		this.iFont = null;
		this.iFontMetrics = null;
		Map map = this.iWordInfoMap;
		if(map != null) {
			map.clear();
		}
	}

	Map iWordInfoMap = null;
	
	public final WordInfo getWordInfo(String word) {
		// Expected to be called only in the GUI (rendering) thread.
		// No synchronization necessary.
		Map map = this.iWordInfoMap;
		if(map == null) {
			map = new HashMap(1);
			this.iWordInfoMap = map;
		}
		WordInfo wi = (WordInfo) map.get(word);
		if(wi != null) {
			return wi;
		}
		wi = new WordInfo();
		FontMetrics fm = this.getFontMetrics();
		wi.fontMetrics = fm;
		wi.ascentPlusLeading = fm.getAscent() + fm.getLeading();
		wi.descent = fm.getDescent();
		wi.height = fm.getHeight();
		wi.width = fm.stringWidth(word);
		map.put(word, wi);
		return wi;
	}
}
