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
package org.lobobrowser.util.gui;

import java.util.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.logging.*;

import org.lobobrowser.util.Objects;
/** Note: Undocumented class? */
import sun.font.FontManager;

/**
 * @author J. H. S.
 */
public class FontFactory {
	private static final Logger logger = Logger.getLogger(FontFactory.class.getName());
	private static final boolean loggableFine = logger.isLoggable(Level.FINE);
	private static final FontFactory instance = new FontFactory();
	private final Set fontFamilies = new HashSet(40);
	private final Map fontMap = new HashMap(50);
	
	/**
	 * 
	 */
	private FontFactory() {
		boolean liflag = loggableFine;
		String[] ffns = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		Set fontFamilies = this.fontFamilies;
		synchronized(this) {
			for(int i = 0; i < ffns.length; i++) {
				String ffn = ffns[i];
				if(liflag) {
					logger.fine("FontFactory(): family=" + ffn);
				}
				fontFamilies.add(ffn.toLowerCase());
			}
		}
	}
	
	public static final FontFactory getInstance() {
		return instance;
	}

	private final Map registeredFonts = new HashMap(0);
	
	/**
	 * Registers a font family. It does not close the stream provided.
	 * Fonts should be registered before the renderer has a chance to
	 * cache document font specifications.
	 * @param fontName The name of a font as it would appear in a font-family specification.
	 * @param fontFormat Should be {@link Font#TRUETYPE_FONT}.
	 */
	public void registerFont(String fontName, int fontFormat, java.io.InputStream fontStream) throws java.awt.FontFormatException, java.io.IOException {
		Font f = Font.createFont(fontFormat, fontStream);
		synchronized(this) {
			this.registeredFonts.put(fontName.toLowerCase(), f);
		}
	}
	
	/**
	 * Unregisters a font previously registered with {@link #registerFont(String, int, java.io.InputStream)}.
	 * @param fontName The font name to be removed.
	 */
	public void unregisterFont(String fontName) {
		synchronized(this) {
			this.registeredFonts.remove(fontName.toLowerCase());
		}
	}
	
	public Font getFont(String fontFamily, String fontStyle, String fontVariant, String fontWeight, float fontSize, Set locales, Integer superscript) {
		FontKey key = new FontKey(fontFamily, fontStyle, fontVariant, fontWeight, fontSize, locales, superscript);
		synchronized(this) {
			Font font = (Font) this.fontMap.get(key);
			if(font == null) {
				font = this.createFont(key);
				this.fontMap.put(key, font);
			}
			return font;
		}
	}
	
	private String defaultFontName = "SansSerif";
	
	public String getDefaultFontName() {
		return defaultFontName;
	}

	/**
	 * Sets the default font name to be used when a name is unrecognized
	 * or when a font is determined not to be capable of diplaying characters
	 * from a given language.
	 * This should be the name of a font that can display unicode text
	 * across all or most languages.
	 * @param defaultFontName The name of a font.
	 */
	public void setDefaultFontName(String defaultFontName) {
		if(defaultFontName == null) {
			throw new IllegalArgumentException("defaultFontName cannot be null");
		}
		this.defaultFontName = defaultFontName;
	}

	private final Font createFont(FontKey key) {
		Font font = createFont_Impl(key);
		return superscriptFont(font, key.superscript);
	}

	public static Font superscriptFont(Font baseFont, Integer newSuperscript) {
		if (newSuperscript == null){
			return baseFont;
		}
		Integer fontSuperScript = (Integer) baseFont.getAttributes().get(TextAttribute.SUPERSCRIPT);
		if (fontSuperScript == null){
			fontSuperScript = new Integer(0);
		}
		if (fontSuperScript.equals(newSuperscript)){
			return baseFont;
		} else {
			Map additionalAttributes = new HashMap();
			additionalAttributes.put(TextAttribute.SUPERSCRIPT, newSuperscript);
			return baseFont.deriveFont(additionalAttributes);
		}
	}
	
	private final Font createFont_Impl(FontKey key) {
		String fontNames = key.fontFamily;
		String matchingFace = null;
		Set fontFamilies = this.fontFamilies;
		Map registeredFonts = this.registeredFonts; 
		Font baseFont = null;
		if(fontNames != null) {
			StringTokenizer tok = new StringTokenizer(fontNames, ",");
			while(tok.hasMoreTokens()) {
				String face = tok.nextToken().trim();
				String faceTL = face.toLowerCase();
				if(registeredFonts.containsKey(faceTL)) {
					baseFont = (Font) registeredFonts.get(faceTL);
					break;
				}
				else if(fontFamilies.contains(faceTL)) {
					matchingFace = faceTL;
					break;
				}
			}
		}
		int fontStyle = Font.PLAIN;
		if("italic".equalsIgnoreCase(key.fontStyle)) {
			fontStyle |= Font.ITALIC;
		}
		if("bold".equalsIgnoreCase(key.fontWeight) || "bolder".equalsIgnoreCase(key.fontWeight)) {
			fontStyle |= Font.BOLD;
		}		
		if(baseFont != null) {
			return baseFont.deriveFont(fontStyle, key.fontSize);
		}
		else if(matchingFace != null) {
			Font font = createFont(matchingFace, fontStyle, (int) Math.round(key.fontSize));
			Set locales = key.locales;
			if(locales == null) {
				Locale locale = Locale.getDefault();
				if(font.canDisplayUpTo(locale.getDisplayLanguage(locale)) == -1) {
					return font;
				}				
			}
			else {
				Iterator i = locales.iterator();
				boolean allMatch = true;
				while(i.hasNext()) {
					Locale locale = (Locale) i.next();
					if(font.canDisplayUpTo(locale.getDisplayLanguage(locale)) != -1) {
						allMatch = false;
						break;
					}				
				}
				if(allMatch) {
					return font;
				}
			}
			// Otherwise, fall through.
		}		
		// Last resort:
		return createFont(this.defaultFontName, fontStyle, (int) Math.round(key.fontSize));
	}
	
	private Font createFont(String name, int style, int size) {
		// Proprietary Sun API. Maybe shouldn't use it. Works well for Chinese.
	    return FontManager.getCompositeFontUIResource(new Font(name, style, size));
	}
	
	private static class FontKey {
		public final String fontFamily;
		public final String fontStyle; 
		public final String fontVariant; 
		public final String fontWeight; 
		public final float fontSize;
		public final Set locales;
		public final Integer superscript;
		
		
		/**
		 * @param fontFamily
		 * @param fontStyle
		 * @param fontVariant
		 * @param fontWeight
		 * @param fontSize
		 */
		public FontKey(final String fontFamily, final String fontStyle,
				final String fontVariant, final String fontWeight,
				final float fontSize, final Set locales, final Integer superscript) {
			this.fontFamily = fontFamily == null ? null : fontFamily.intern();
			this.fontStyle = fontStyle == null ? null : fontStyle.intern();
			this.fontVariant = fontVariant == null ? null : fontVariant.intern();
			this.fontWeight = fontWeight == null ? null : fontWeight.intern();
			this.fontSize = fontSize;
			this.locales = locales;
			this.superscript = superscript;
		}
		
		public boolean equals(Object other) {
			if(other == this) {
				// Quick check.
				return true;
			}
			FontKey ors;
			try {
				ors = (FontKey) other;
			} catch(ClassCastException cce) {
				// Not expected
				return false;
			}
			// Note that we use String.intern() for all string fields,
			// so we can do instance comparisons.
			return this.fontSize == ors.fontSize &&
				   this.fontFamily == ors.fontFamily &&
				   this.fontStyle == ors.fontStyle &&
				   this.fontWeight == ors.fontWeight &&
				   this.fontVariant == ors.fontVariant &&
				   this.superscript == ors.superscript &&
				   Objects.equals(this.locales,ors.locales);
		}

		private int cachedHash = -1;
		
		public int hashCode() {
			int ch = this.cachedHash;
			if(ch != -1) {
				// Object is immutable - caching is ok.
				return ch;
			}
			String ff = this.fontFamily;
			if(ff == null) {
				ff = "";
			}
			String fw = this.fontWeight;
			if(fw == null) {
				fw = "";
			}
			String fs = this.fontStyle;
			if(fs == null) {
				fs = "";
			}
			Integer ss = this.superscript;
			ch = ff.hashCode() ^ 
				   fw.hashCode() ^ 
				   fs.hashCode() ^
				   (int) this.fontSize ^
				   (ss == null ? 0 : ss.intValue());
			this.cachedHash = ch;
			return ch;
		}
		
		public String toString() {
			return "FontKey[family=" + this.fontFamily + ",size=" + this.fontSize + ",style=" + this.fontStyle + ",weight=" + this.fontWeight + ",variant=" + this.fontVariant + ",superscript="+this.superscript+"]";
		}
	}
}