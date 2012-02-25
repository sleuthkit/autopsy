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
package org.lobobrowser.html.style;
import java.awt.*;

/**
 * @author J. H. S.
 */
public interface RenderState {
	public static final int MASK_TEXTDECORATION_UNDERLINE = 1;
	public static final int MASK_TEXTDECORATION_OVERLINE = 2;
	public static final int MASK_TEXTDECORATION_LINE_THROUGH = 4;
	public static final int MASK_TEXTDECORATION_BLINK = 8;

	public static final int TEXTTRANSFORM_NONE = 0;
	public static final int TEXTTRANSFORM_CAPITALIZE = 1;
	public static final int TEXTTRANSFORM_UPPERCASE = 2;
	public static final int TEXTTRANSFORM_LOWERCASE = 4;
//	TODO how to handle style cascading?	
//	public static final int TEXTTRANSFORM_INHERIT = 8;
	
	public static final int DISPLAY_NONE = 0;
	public static final int DISPLAY_INLINE = 1;
	public static final int DISPLAY_BLOCK = 2;
	public static final int DISPLAY_LIST_ITEM = 3;
	public static final int DISPLAY_TABLE_ROW = 4;
	public static final int DISPLAY_TABLE_CELL = 5;		
	public static final int DISPLAY_TABLE = 6;			
	
	public static final int WS_NORMAL = 0;
	public static final int WS_PRE = 1;
	public static final int WS_NOWRAP = 2;

	public static final int VISIBILITY_VISIBLE = 0;
	public static final int VISIBILITY_HIDDEN = 1;
	public static final int VISIBILITY_COLLAPSE = 2;
	
	public static final int POSITION_STATIC = 0;
	public static final int POSITION_ABSOLUTE = 1;
	public static final int POSITION_RELATIVE = 2;
	public static final int POSITION_FIXED = 3;	
	
	public static final int FLOAT_NONE = 0;
	public static final int FLOAT_LEFT = 1;
	public static final int FLOAT_RIGHT = 2;

    public static final int OVERFLOW_NONE = 0;
    public static final int OVERFLOW_SCROLL = 1;
    public static final int OVERFLOW_AUTO = 2;
    public static final int OVERFLOW_HIDDEN = 3;
    public static final int OVERFLOW_VISIBLE = 4;	    

	public int getPosition();
	public int getFloat();
	public int getVisibility();
	public Font getFont();
	public int getFontBase();
	public WordInfo getWordInfo(String word);
	public Color getColor();
	public Color getBackgroundColor();
	public Color getTextBackgroundColor();
	public BackgroundInfo getBackgroundInfo();
	public Color getOverlayColor();
	public int getTextTransform();
	public int getTextDecorationMask();
	public FontMetrics getFontMetrics();
	public int getBlankWidth();
	public boolean isHighlight();
	public void setHighlight(boolean highlight);
	public RenderState getPreviousRenderState();
	public int getAlignXPercent();
	public int getAlignYPercent();
	public int getCount(String counter, int nesting);
	public int getDisplay();
	public void resetCount(String counter, int nesting, int value);
	public int incrementCount(String counter, int nesting);
	public int getTextIndent(int availWidth);
	public String getTextIndentText();
	public int getWhiteSpace();
	public HtmlInsets getMarginInsets();
	public HtmlInsets getPaddingInsets();
	public int getOverflowX();
	public int getOverflowY();
	public void invalidate();
	public BorderInfo getBorderInfo();
}
