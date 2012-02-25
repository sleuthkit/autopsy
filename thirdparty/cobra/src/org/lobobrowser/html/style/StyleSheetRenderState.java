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
import java.awt.font.TextAttribute;
import java.util.*;

import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.util.gui.ColorFactory;
import org.lobobrowser.util.gui.FontFactory;
import org.w3c.dom.css.*;
import org.w3c.dom.html2.*;

/**
 * @author J. H. S.
 */
public class StyleSheetRenderState implements RenderState {
	private static final FontFactory FONT_FACTORY = FontFactory.getInstance();
	// Default font needs to be something that displays in all languages.
	// Serif, SansSerif, Monospaced.
	private static final String DEFAULT_FONT_FAMILY = "SansSerif"; 
	private static final Font DEFAULT_FONT = FONT_FACTORY.getFont(DEFAULT_FONT_FAMILY, null, null, null, HtmlValues.DEFAULT_FONT_SIZE, null, null);
	protected static final HtmlInsets INVALID_INSETS = new HtmlInsets();
	protected static final BackgroundInfo INVALID_BACKGROUND_INFO = new BackgroundInfo();
    protected static final BorderInfo INVALID_BORDER_INFO = new BorderInfo();
	protected static final Color INVALID_COLOR = new Color(100, 0, 100);

	protected final HTMLElementImpl element;
	protected final HTMLDocumentImpl document;
	protected final RenderState prevRenderState;
	
	private Font iFont;
	private FontMetrics iFontMetrics;
	private Color iColor;
	private Color iBackgroundColor = INVALID_COLOR;
	private Color iTextBackgroundColor = INVALID_COLOR;
	private Color iOverlayColor = INVALID_COLOR;
	private int iTextDecoration = -1;
	private int iTextTransform = -1;
	private int iBlankWidth = -1;
	private boolean iHighlight;

	protected BackgroundInfo iBackgroundInfo = INVALID_BACKGROUND_INFO;

	static {
	}
	
	public StyleSheetRenderState(RenderState prevRenderState, HTMLElementImpl element) {
		this.prevRenderState = prevRenderState;
		this.element = element;
		this.document = (HTMLDocumentImpl) element.getOwnerDocument();
	}

	public StyleSheetRenderState(HTMLDocumentImpl document) {
		this.prevRenderState = null;
		this.element = null;
		this.document = document;
	}

//	public TextRenderState(RenderState prevRenderState) {
//		this.css2properties = new CSS2PropertiesImpl(this);
//		this.prevRenderState = prevRenderState;
//	}
	
	protected int getDefaultDisplay() {
		return DISPLAY_INLINE;
	}

	private Integer iDisplay;
	
	public int getDisplay() {
		Integer d = this.iDisplay;
		if(d != null) {
			return d.intValue();
		}
		CSS2Properties props = this.getCssProperties();
		String displayText = props == null ? null : props.getDisplay();
		int displayInt;
		if(displayText != null) {
			String displayTextTL = displayText.toLowerCase();
			if("block".equals(displayTextTL)) {
				displayInt = DISPLAY_BLOCK;
			}
			else if("inline".equals(displayTextTL)) {
				displayInt = DISPLAY_INLINE;
			}
			else if("none".equals(displayTextTL)) {
				displayInt = DISPLAY_NONE;
			}
			else if("list-item".equals(displayTextTL)) {
				displayInt = DISPLAY_LIST_ITEM;
			}
			else if("table".equals(displayTextTL)) {
				displayInt = DISPLAY_TABLE;
			}
			else if("table-cell".equals(displayTextTL)) {
				displayInt = DISPLAY_TABLE_CELL;
			}
			else if("table-row".equals(displayTextTL)) {
				displayInt = DISPLAY_TABLE_ROW;
			}
			else {
				displayInt = this.getDefaultDisplay();
			}
		}
		else {
			displayInt = this.getDefaultDisplay();
		}
		d = new Integer(displayInt);
		this.iDisplay = d;
		return displayInt;		
	}
	
	public RenderState getPreviousRenderState() {
		return this.prevRenderState;
	}

	public int getFontBase() {
		RenderState prs = this.prevRenderState;
		return prs == null ? 3 : prs.getFontBase();
	}

	public void repaint() {
		// Dummy implementation
	}

	protected final AbstractCSS2Properties getCssProperties() {
		HTMLElementImpl element = this.element;
		return element == null ? null : element.getCurrentStyle();
	}

	public void invalidate() {
		Map map = this.iWordInfoMap;
		if(map != null) {
			map.clear();
		}
		this.iFont = null;
		this.iFontMetrics = null;
		this.iColor = null;
		this.iTextDecoration = -1;
		this.iBlankWidth = -1;
		this.alignXPercent = -1;
		this.iBackgroundColor = INVALID_COLOR;
		this.iTextBackgroundColor = INVALID_COLOR;
		this.iOverlayColor = INVALID_COLOR;
		this.iBackgroundInfo = INVALID_BACKGROUND_INFO;
		this.iDisplay = null;
		this.iTextIndentText = null;
		this.iWhiteSpace = null;
		this.marginInsets = INVALID_INSETS;
		this.paddingInsets = INVALID_INSETS;
		this.overflowX = -1;
		this.overflowY = -1;
		this.borderInfo = INVALID_BORDER_INFO;
		// Should NOT invalidate parent render state.
	}
		
	public Font getFont() {
		Font f = this.iFont;
		if(f != null) {
			return f;
		}
		AbstractCSS2Properties style = this.getCssProperties();
		RenderState prs = this.prevRenderState;
		if(style == null) {
			if(prs != null) {
				f = prs.getFont();
				this.iFont = f;
				return f;
			}
			f = DEFAULT_FONT;
			this.iFont = f;
			return f;
		}
		Float fontSize = null;
		String fontStyle = null;
		String fontVariant = null;
		String fontWeight = null;
		String fontFamily = null;
		
		String newFontSize = style == null ? null : style.getFontSize();
		String newFontFamily = style == null ? null : style.getFontFamily();
		String newFontStyle = style == null ? null : style.getFontStyle();
		String newFontVariant = style == null ? null : style.getFontVariant();
		String newFontWeight = style == null ? null : style.getFontWeight();
		String verticalAlign = style == null ? null : style.getVerticalAlign();
		boolean isSuper = verticalAlign != null && verticalAlign.equalsIgnoreCase("super");
		boolean isSub = verticalAlign != null && verticalAlign.equalsIgnoreCase("sub");
		if(newFontSize == null && newFontWeight == null && newFontStyle == null && newFontFamily == null && newFontVariant == null) {
		    if (!isSuper && !isSub) {
		        if(prs != null) {
		            f = prs.getFont();
		            this.iFont = f;
		            return f;
		        }
		        f = DEFAULT_FONT;
		        this.iFont = f;
		        return f;
		    }
		}
		if(newFontSize != null) {
			try {
				fontSize = new Float(HtmlValues.getFontSize(newFontSize, prs));
			} catch(Exception err) {
				fontSize = HtmlValues.DEFAULT_FONT_SIZE_BOX;
			}
		}
		else if(fontSize == null) {
			if(prs != null) {
				fontSize = new Float(prs.getFont().getSize());
			} else {
				fontSize = HtmlValues.DEFAULT_FONT_SIZE_BOX;
			}
		}
		if(newFontFamily != null) {
			fontFamily = newFontFamily;
		}
		else if(fontFamily == null && prs != null) {
			fontFamily = prs.getFont().getFamily();
		}
		if(fontFamily == null) {
			fontFamily = DEFAULT_FONT_FAMILY;
		}
		if(newFontStyle != null) {
			fontStyle = newFontStyle;
		}
		else if(fontStyle == null && prs != null) {
			int fstyle = prs.getFont().getStyle();
			if((fstyle & Font.ITALIC) != 0) {
				fontStyle = "italic";
			}
		}
		if(newFontVariant != null) {
			fontVariant = newFontVariant;
		}
		else if(prs != null) {
			// TODO: smallcaps?
		}
		if(newFontWeight != null) {
			fontWeight = newFontWeight;
		}
		else if(fontWeight == null && prs != null) {
			int fstyle = prs.getFont().getStyle();
			if((fstyle & Font.BOLD) != 0) {
				fontWeight = "bold";
			}			
		}
		HTMLDocumentImpl document = this.document;
		Set locales = document == null ? null : document.getLocales();
		
		Integer superscript = null;
		if(isSuper) {
			superscript = new Integer(1);
		} else if (isSub) {
			superscript = new Integer(-1);
		}
		if (superscript == null && prs != null){
			superscript = (Integer) prs.getFont().getAttributes().get(TextAttribute.SUPERSCRIPT);
		}		
		f = FONT_FACTORY.getFont(fontFamily, fontStyle, fontVariant, fontWeight, fontSize.floatValue(), locales, superscript);
		this.iFont = f;
		return f;
	}
	
	public Color getColor() {
		Color c = this.iColor;
		if(c != null) {
			return c;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		String colorValue = props == null ? null : props.getColor();
		if(colorValue == null || "".equals(colorValue)) {
			RenderState prs = this.prevRenderState;
			if(prs != null) {
				c = prs.getColor();
				this.iColor = c;
				return c;
			}
			else {
				colorValue = "black";
			}
		}
		c = ColorFactory.getInstance().getColor(colorValue);
		this.iColor = c;
		return c;
	}
	
	public Color getBackgroundColor() {
		Color c = this.iBackgroundColor;
		if(c != INVALID_COLOR) {
			return c;
		}
		Color localColor;
		BackgroundInfo binfo = this.getBackgroundInfo();
		localColor = binfo == null ? null : binfo.backgroundColor;
		if(localColor == null && this.getDisplay() == DISPLAY_INLINE) {
			RenderState prs = this.prevRenderState;
			if(prs != null) {
				Color ancestorColor = prs.getBackgroundColor();
				if(ancestorColor != null) {
					this.iBackgroundColor = ancestorColor;
					return ancestorColor;
				}
			}
		}
		this.iBackgroundColor = localColor;
		return localColor;
	}
	
	public Color getTextBackgroundColor() {
		Color c = this.iTextBackgroundColor;
		if(c != INVALID_COLOR) {
			return c;
		}
		Color localColor;
		if(this.getDisplay() != DISPLAY_INLINE) {
			// Background painted by block.
			localColor = null;
		}
		else {
			BackgroundInfo binfo = this.getBackgroundInfo();
			localColor = binfo == null ? null : binfo.backgroundColor;
			if(localColor == null) {
				RenderState prs = this.prevRenderState;
				if(prs != null) {
					Color ancestorColor = prs.getTextBackgroundColor();
					if(ancestorColor != null) {
						this.iTextBackgroundColor = ancestorColor;
						return ancestorColor;
					}
				}
			}
		}
		this.iTextBackgroundColor = localColor;
		return localColor;
	}

	public Color getOverlayColor() {
		Color c = this.iOverlayColor;
		if(c != INVALID_COLOR) {
			return c;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		String colorValue = props == null ? null : props.getOverlayColor();
		if(colorValue == null || colorValue.length() == 0) {
			RenderState prs = this.prevRenderState;
			if(prs != null) {
				c = prs.getOverlayColor();
				this.iOverlayColor = c;
				return c;
			}
			else {
				colorValue = null;
			}
		}
		c = colorValue == null ? null : ColorFactory.getInstance().getColor(colorValue);
		this.iOverlayColor = c;
		return c;
	}
	
	public int getTextDecorationMask() {
		int td = this.iTextDecoration;
		if(td != -1) {
			return td;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		String tdText = props == null ? null : props.getTextDecoration();
		if(tdText == null) {
			RenderState prs = this.prevRenderState;
			if(prs != null) {
				td = prs.getTextDecorationMask();
				this.iTextDecoration = td;
				return td;
			}
		}
		td = 0;
		if(tdText != null) {
			StringTokenizer tok = new StringTokenizer(tdText.toLowerCase(), ", \t\n\r");
			while(tok.hasMoreTokens()) {
				String token = tok.nextToken();
				if("none".equals(token)) {
					// continue
				}
				else if("underline".equals(token)) {
					td |= StyleSheetRenderState.MASK_TEXTDECORATION_UNDERLINE;
				}
				else if("line-through".equals(token)) {
					td |= StyleSheetRenderState.MASK_TEXTDECORATION_LINE_THROUGH;
				}
				else if("blink".equals(token)) {
					td |= StyleSheetRenderState.MASK_TEXTDECORATION_BLINK;
				}
				else if("overline".equals(token)) {
					td |= StyleSheetRenderState.MASK_TEXTDECORATION_OVERLINE;
				}
			}
		}
		this.iTextDecoration = td;
		return td;
	}

	public int getTextTransform()
	{
		int tt = this.iTextTransform;
		if(tt != -1) {
			return tt;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		String tdText = props == null ? null : props.getTextTransform();
		if(tdText == null) {
			RenderState prs = this.prevRenderState;
			if(prs != null) {
				tt = prs.getTextTransform();
				this.iTextTransform = tt;
				return tt;
			}
		}
		tt = 0;
		if(tdText != null) {
			if("none".equals(tdText)) {
				// continue
			}
			else if("capitalize".equals(tdText)) {				
				tt = TEXTTRANSFORM_CAPITALIZE;
			}
			else if("uppercase".equals(tdText)) {
				tt = TEXTTRANSFORM_UPPERCASE;
			}
			else if("lowercase".equals(tdText)) {
				tt = TEXTTRANSFORM_LOWERCASE;
			}
// TODO how the explicit "inherit" value is to be handled?
// 		Who is responsible for CSS cascading? 
//			... painting code? prevRenderState?
//			
//			else if("inherit".equals(tdText)) {
//				tt = TEXTTRANSFORM_INHERIT;								
//			}
		}
		this.iTextTransform = tt;
		return tt;
	}	
	
	public final FontMetrics getFontMetrics() {
		FontMetrics fm = this.iFontMetrics;
		if(fm == null) {
			//TODO getFontMetrics deprecated. How to get text width?
			fm = Toolkit.getDefaultToolkit().getFontMetrics(this.getFont());
			this.iFontMetrics = fm;
		}
		return fm;
	}
	
	public int getBlankWidth() {
		int bw = this.iBlankWidth;
		if(bw == -1) {
			bw = this.getFontMetrics().charWidth(' ');
			this.iBlankWidth = bw;
		}
		return bw;
	}

	/**
	 * @return Returns the iHighlight.
	 */
	public boolean isHighlight() {
		return this.iHighlight;
	}
	
	/**
	 * @param highlight The iHighlight to set.
	 */
	public void setHighlight(boolean highlight) {
		this.iHighlight = highlight;
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
	
	private int alignXPercent = -1;
	
	public int getAlignXPercent() {
		int axp = this.alignXPercent;
		if(axp != -1) {
			return axp;
		}
		CSS2Properties props = this.getCssProperties();
		String textAlign = props == null ? null : props.getTextAlign();
		if(textAlign == null || textAlign.length() == 0) {
			// Fall back to align attribute.
			HTMLElement element = this.element;
			if(element != null) {
				textAlign = element.getAttribute("align");
				if(textAlign == null || textAlign.length() == 0) {
					RenderState prs = this.prevRenderState;
					if(prs != null) {
						return prs.getAlignXPercent();
					}
					textAlign = null;
				}
			}
		}
		if(textAlign == null) {
			axp = 0;
		}
		else if("center".equalsIgnoreCase(textAlign)) {
			axp = 50;
		}
		else if("right".equalsIgnoreCase(textAlign)) {
			axp = 100;
		}
		else {
			//TODO: justify, <string>
			axp = 0;
		}
		this.alignXPercent = axp;
		return axp;
	}
	
	public int getAlignYPercent() {
		// This is only settable in table cells.
		// TODO: Does it work with display: table-cell?		
		return 0;
	}

	private Map counters = null;
	
	public int getCount(String counter, int nesting) {
		// Expected to be called only in GUI thread.
		RenderState prs = this.prevRenderState;
		if(prs != null) {
			return prs.getCount(counter, nesting);
		}
		Map counters = this.counters;
		if(counters == null) {
			return 0;
		}
		ArrayList counterArray = (ArrayList) counters.get(counter);
		if(nesting < 0 || nesting >= counterArray.size()) {
			return 0;
		}
		Integer integer = (Integer) counterArray.get(nesting);
		return integer == null ? 0 : integer.intValue();
	}

	public void resetCount(String counter, int nesting, int value) {
		// Expected to be called only in the GUI thread.
		RenderState prs = this.prevRenderState;
		if(prs != null) {
			prs.resetCount(counter, nesting, value);
		}
		else {
			Map counters = this.counters;
			if(counters == null) {
				counters = new HashMap(2);
				this.counters = counters;
				counters.put(counter, new ArrayList(0));
			}
			ArrayList counterArray = (ArrayList) counters.get(counter);
			while(counterArray.size() <= nesting) {
				counterArray.add(null);
			}
			counterArray.set(nesting, new Integer(value));
		}
	}

	public int incrementCount(String counter, int nesting) {
		// Expected to be called only in the GUI thread.
		RenderState prs = this.prevRenderState;
		if(prs != null) {
			return prs.incrementCount(counter, nesting);
		}
		Map counters = this.counters;
		if(counters == null) {
			counters = new HashMap(2);
			this.counters = counters;
			counters.put(counter, new ArrayList(0));
		}
		ArrayList counterArray = (ArrayList) counters.get(counter);
		while(counterArray.size() <= nesting) {
			counterArray.add(null);
		}
		Integer integer = (Integer) counterArray.get(nesting);
		int prevValue = integer == null ? 0 : integer.intValue();
		counterArray.set(nesting, new Integer(prevValue + 1));
		return prevValue;
	}
	
	public BackgroundInfo getBackgroundInfo() {
		BackgroundInfo binfo = this.iBackgroundInfo;
		if(binfo != INVALID_BACKGROUND_INFO) {
			return binfo;
		}
		binfo = null;
		AbstractCSS2Properties props = this.getCssProperties();
		if(props != null) {
			String backgroundColorText = props.getBackgroundColor();
			if(backgroundColorText != null) {
				if(binfo == null) {
					binfo = new BackgroundInfo();
				}
				binfo.backgroundColor = ColorFactory.getInstance().getColor(backgroundColorText);
			}		
			String backgroundImageText = props.getBackgroundImage();
			if(backgroundImageText != null && backgroundImageText.length() > 0) {
				java.net.URL backgroundImage = HtmlValues.getURIFromStyleValue(backgroundImageText);
				if(backgroundImage != null) {
					if(binfo == null) {
						binfo = new BackgroundInfo();
					}
					binfo.backgroundImage = backgroundImage;
				}
			}
			String backgroundRepeatText = props.getBackgroundRepeat();
			if(backgroundRepeatText != null) {
				if(binfo == null) {
					binfo = new BackgroundInfo();
				}			
				this.applyBackgroundRepeat(binfo, backgroundRepeatText);
			}
			String backgroundPositionText = props.getBackgroundPosition();
			if(backgroundPositionText != null) {
				if(binfo == null) {
					binfo = new BackgroundInfo();
				}			
				this.applyBackgroundPosition(binfo, backgroundPositionText);
			}
		}
		this.iBackgroundInfo = binfo;
		return binfo;
	}
	
	private String iTextIndentText = null;
	
	public String getTextIndentText() {
		String tiText = this.iTextIndentText;
		if(tiText != null) {
			return tiText;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		tiText = props == null ? null : props.getTextIndent();
		if(tiText == null) {
			RenderState prs = this.prevRenderState;
			if(prs != null) {
				String parentText = prs.getTextIndentText();
				this.iTextIndentText = parentText;
				return parentText;
			}
			else {
				tiText = "";
			}
		}
		return tiText;
	}
	
	public int getTextIndent(int availSize) {
		// No caching for this one.
		String tiText = this.getTextIndentText();
		if(tiText.length() == 0) {
			return 0;
		}
		else {
		    return HtmlValues.getPixelSize(tiText, this, 0, availSize);
		}
	}
	
	protected Integer iWhiteSpace;

	public int getWhiteSpace() {
		if(RenderThreadState.getState().overrideNoWrap) {
			return WS_NOWRAP;
		}
		Integer ws = this.iWhiteSpace;
		if(ws != null) {
			return ws.intValue();
		}
		AbstractCSS2Properties props = this.getCssProperties();
		String whiteSpaceText = props == null ? null : props.getWhiteSpace();
		int wsValue;
		if(whiteSpaceText == null) {
		    RenderState prs = this.prevRenderState;
		    if(prs != null) {
		        wsValue = prs.getWhiteSpace();
		    }
		    else {
		        wsValue = WS_NORMAL;
		    }
		}
		else {
			String whiteSpaceTextTL = whiteSpaceText.toLowerCase();
			if("nowrap".equals(whiteSpaceTextTL)) {
				wsValue = WS_NOWRAP;
			}
			else if("pre".equals(whiteSpaceTextTL)) {
				wsValue = WS_PRE;
			}
			else {
				wsValue = WS_NORMAL;
			}
		}
		this.iWhiteSpace = new Integer(wsValue);
		return wsValue;
	}
	
	protected HtmlInsets marginInsets = INVALID_INSETS;
	protected HtmlInsets paddingInsets = INVALID_INSETS;
	
	public HtmlInsets getMarginInsets() {
		HtmlInsets mi = this.marginInsets;
		if(mi != INVALID_INSETS) {
			return mi;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		if(props == null) { 
			mi = null;
		}
		else {
			mi = HtmlValues.getMarginInsets(props, this);
		}
		this.marginInsets = mi;
		return mi;
	}
	
	public HtmlInsets getPaddingInsets() {
		HtmlInsets mi = this.paddingInsets;
		if(mi != INVALID_INSETS) {
			return mi;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		if(props == null) {
			mi = null;
		}
		else {
			mi = HtmlValues.getPaddingInsets(props, this);
			this.paddingInsets = mi;
		}
		return mi;
	}

	private void applyBackgroundHorizontalPositon(BackgroundInfo binfo, String xposition) {
		if(xposition.endsWith("%")) {
			binfo.backgroundXPositionAbsolute = false;
			try {
				binfo.backgroundXPosition = (int) Double.parseDouble(xposition.substring(0, xposition.length() - 1).trim());
			} catch(NumberFormatException nfe) {
				binfo.backgroundXPosition = 0;
			}
		}
		else if("center".equalsIgnoreCase(xposition)) {
			binfo.backgroundXPositionAbsolute = false;
			binfo.backgroundXPosition = 50;			
		}
		else if("right".equalsIgnoreCase(xposition)) {
			binfo.backgroundXPositionAbsolute = false;
			binfo.backgroundXPosition = 100;			
		}
		else if("left".equalsIgnoreCase(xposition)) {
			binfo.backgroundXPositionAbsolute = false;
			binfo.backgroundXPosition = 0;			
		}
		else if("bottom".equalsIgnoreCase(xposition)) {
			// Can happen
			binfo.backgroundYPositionAbsolute = false;
			binfo.backgroundYPosition = 100;			
		}
		else if("top".equalsIgnoreCase(xposition)) {
			// Can happen
			binfo.backgroundYPositionAbsolute = false;
			binfo.backgroundYPosition = 0;			
		}
		else {
			binfo.backgroundXPositionAbsolute = true;
			binfo.backgroundXPosition = HtmlValues.getPixelSize(xposition, this, 0);
		}		
	}

	private void applyBackgroundVerticalPosition(BackgroundInfo binfo, String yposition) {
		if(yposition.endsWith("%")) {
			binfo.backgroundYPositionAbsolute = false;
			try {
				binfo.backgroundYPosition = (int) Double.parseDouble(yposition.substring(0, yposition.length() - 1).trim());
			} catch(NumberFormatException nfe) {
				binfo.backgroundYPosition = 0;
			}
		}
		else if("center".equalsIgnoreCase(yposition)) {
			binfo.backgroundYPositionAbsolute = false;
			binfo.backgroundYPosition = 50;			
		}
		else if("bottom".equalsIgnoreCase(yposition)) {
			binfo.backgroundYPositionAbsolute = false;
			binfo.backgroundYPosition = 100;			
		}
		else if("top".equalsIgnoreCase(yposition)) {
			binfo.backgroundYPositionAbsolute = false;
			binfo.backgroundYPosition = 0;			
		}
		else if("right".equalsIgnoreCase(yposition)) {
			// Can happen
			binfo.backgroundXPositionAbsolute = false;
			binfo.backgroundXPosition = 100;			
		}
		else if("left".equalsIgnoreCase(yposition)) {
			// Can happen
			binfo.backgroundXPositionAbsolute = false;
			binfo.backgroundXPosition = 0;			
		}
		else {
			binfo.backgroundYPositionAbsolute = true;
			binfo.backgroundYPosition = HtmlValues.getPixelSize(yposition, this, 0);
		}						
	}
	
	private void applyBackgroundPosition(BackgroundInfo binfo, String position) {
		binfo.backgroundXPositionAbsolute = false;
		binfo.backgroundYPositionAbsolute = false;
		binfo.backgroundXPosition = 50;
		binfo.backgroundYPosition = 50;
		StringTokenizer tok = new StringTokenizer(position, " \t\r\n");
		if(tok.hasMoreTokens()) {
			String xposition = tok.nextToken();
			this.applyBackgroundHorizontalPositon(binfo, xposition);
			if(tok.hasMoreTokens()) {
				String yposition = tok.nextToken();
				this.applyBackgroundVerticalPosition(binfo, yposition);
			}
		}
	}
	
//	private void applyBackground(BackgroundInfo binfo, String background, CSSStyleDeclaration declaration) {
//		String[] tokens = HtmlValues.splitCssValue(background);
//		boolean hasXPosition = false;
//		for(int i = 0; i < tokens.length; i++) {
//			String token = tokens[i];
//			if(ColorFactory.getInstance().isColor(token)) {
//				binfo.backgroundColor = ColorFactory.getInstance().getColor(token);
//			}	
//			else if(HtmlValues.isUrl(token)) {
//				binfo.backgroundImage = HtmlValues.getURIFromStyleValue(token, declaration, this.document);
//			}
//			else if(isBackgroundRepeat(token)) {
//				this.applyBackgroundRepeat(binfo, token);
//			}
//			else if(isBackgroundPosition(token)) {
//				if(hasXPosition) {
//					this.applyBackgroundVerticalPosition(binfo, token);
//				}
//				else {
//					hasXPosition = true;
//					this.applyBackgroundHorizontalPositon(binfo, token);
//				}
//			}
//		}
//	}

	private void applyBackgroundRepeat(BackgroundInfo binfo, String backgroundRepeatText) {
		String brtl = backgroundRepeatText.toLowerCase();
		if("repeat".equals(brtl)) {
			binfo.backgroundRepeat = BackgroundInfo.BR_REPEAT;
		}
		else if("repeat-x".equals(brtl)) {
			binfo.backgroundRepeat = BackgroundInfo.BR_REPEAT_X;
		}
		else if("repeat-y".equals(brtl)) {
			binfo.backgroundRepeat = BackgroundInfo.BR_REPEAT_Y;
		}
		else if("no-repeat".equals(brtl)) {
			binfo.backgroundRepeat = BackgroundInfo.BR_NO_REPEAT;
		}		
	}

	private Integer cachedVisibility;
	
	public int getVisibility() {
		Integer v = this.cachedVisibility;
		if(v != null) {
			return v.intValue();
		}
		AbstractCSS2Properties props = this.getCssProperties();
		int visibility;
		if(props == null) {
			visibility = VISIBILITY_VISIBLE;
		}
		else {
			String visibText = props.getVisibility();
			if(visibText == null || visibText.length() == 0) {
				visibility = VISIBILITY_VISIBLE;
			}
			else {
				String visibTextTL = visibText.toLowerCase();
				if(visibTextTL.equals("hidden")) {
					visibility = VISIBILITY_HIDDEN;
				}
				else if(visibTextTL.equals("visible")) {
					visibility = VISIBILITY_VISIBLE;
				}
				else if(visibTextTL.equals("collapse")) {
					visibility = VISIBILITY_COLLAPSE;
				}
				else {
					visibility = VISIBILITY_VISIBLE;
				}
			}
		}
		this.cachedVisibility = new Integer(visibility);
		return visibility;
	}
	
	private Integer cachedPosition;
	
	public int getPosition() {
		Integer p = this.cachedPosition;
		if(p != null) {
			return p.intValue();
		}
		AbstractCSS2Properties props = this.getCssProperties();
		int position;
		if(props == null) {
			position = POSITION_STATIC;
		}
		else {
			String positionText = props.getPosition();
			if(positionText == null || positionText.length() == 0) {
				position = POSITION_STATIC;
			}
			else {
				String positionTextTL = positionText.toLowerCase();
				if(positionTextTL.equals("absolute")) {
					position = POSITION_ABSOLUTE;
				}
				else if(positionTextTL.equals("static")) {
					position = POSITION_STATIC;
				}
				else if(positionTextTL.equals("relative")) {
					position = POSITION_RELATIVE;
				}
				else if(positionTextTL.equals("fixed")) {
					position = POSITION_FIXED;
				}
				else {
					position = POSITION_STATIC;
				}
			}
		}
		this.cachedPosition = new Integer(position);
		return position;		
	}

	private Integer cachedFloat;
	
	public int getFloat() {
		Integer p = this.cachedFloat;
		if(p != null) {
			return p.intValue();
		}
		AbstractCSS2Properties props = this.getCssProperties();
		int floatValue;
		if(props == null) {
			floatValue = FLOAT_NONE;
		}
		else {
			String floatText = props.getFloat();
			if(floatText == null || floatText.length() == 0) {
				floatValue = FLOAT_NONE;
			}
			else {
				String floatTextTL = floatText.toLowerCase();
				if(floatTextTL.equals("left")) {
					floatValue = FLOAT_LEFT;
				}
				else if(floatTextTL.equals("right")) {
					floatValue = FLOAT_RIGHT;
				}
				else {
					floatValue = FLOAT_NONE;
				}
			}
		}
		this.cachedFloat = new Integer(floatValue);
		return floatValue;		
	}
	
	public String toString() {
		return "StyleSheetRenderState[font=" + this.getFont() + ",textDecoration=" + this.getTextDecorationMask() + "]";
	}
	
	protected int overflowX = -1;
	protected int overflowY = -1;

    public int getOverflowX() {
        int overflow = this.overflowX;
        if(overflow != -1) {
            return overflow;
        }
        AbstractCSS2Properties props = this.getCssProperties();
        if(props == null) {
            overflow = OVERFLOW_NONE;
        }
        else {
            String overflowText = props.getPropertyValue("overflow-x");
            if(overflowText == null) {
                overflowText = props.getOverflow(); 
            }
            if(overflowText == null) {
                overflow = OVERFLOW_NONE;
            }
            else {
                String overflowTextTL = overflowText.toLowerCase();
                if("scroll".equals(overflowTextTL)) {
                    overflow = OVERFLOW_SCROLL;
                }
                else if("auto".equals(overflowTextTL)) {
                    overflow = OVERFLOW_AUTO;
                }
                else if("hidden".equals(overflowTextTL)) {
                    overflow = OVERFLOW_HIDDEN;
                }
                else if("visible".equals(overflowTextTL)) {
                    overflow = OVERFLOW_VISIBLE;
                }
                else {
                    overflow = OVERFLOW_NONE;
                }
            }
        }
        this.overflowX = overflow;
        return overflow;
    }

    public int getOverflowY() {
        int overflow = this.overflowY;
        if(overflow != -1) {
            return overflow;
        }
        AbstractCSS2Properties props = this.getCssProperties();
        if(props == null) {
            overflow = OVERFLOW_NONE;
        }
        else {
            String overflowText = props.getPropertyValue("overflow-y");
            if(overflowText == null) {
                overflowText = props.getOverflow(); 
            }
            if(overflowText == null) {
                overflow = OVERFLOW_NONE;
            }
            else {
                String overflowTextTL = overflowText.toLowerCase();
                if("scroll".equals(overflowTextTL)) {
                    overflow = OVERFLOW_SCROLL;
                }
                else if("auto".equals(overflowTextTL)) {
                    overflow = OVERFLOW_AUTO;
                }
                else if("hidden".equals(overflowTextTL)) {
                    overflow = OVERFLOW_HIDDEN;
                }
                else if("visible".equals(overflowTextTL)) {
                    overflow = OVERFLOW_VISIBLE;
                }
                else {
                    overflow = OVERFLOW_NONE;
                }
            }
        }
        this.overflowY = overflow;
        return overflow;
    }
    
    protected BorderInfo borderInfo = INVALID_BORDER_INFO;
    
    public BorderInfo getBorderInfo() {
        BorderInfo binfo = this.borderInfo;
        if(binfo != INVALID_BORDER_INFO) {
            return binfo;
        }
        AbstractCSS2Properties props = this.getCssProperties();
        if(props != null) {
            binfo = HtmlValues.getBorderInfo(props, this);
        }
        else {
            binfo = null;
        }
        this.borderInfo = binfo;
        return binfo;
    }
}
