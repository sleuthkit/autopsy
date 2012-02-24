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
 * Created on Nov 27, 2005
 */
package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.style.CSSUtilities;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.css.CSSStyleSheet;
import org.w3c.dom.html2.HTMLStyleElement;
import com.steadystate.css.dom.CSSStyleSheetImpl;
import com.steadystate.css.parser.CSSOMParser;

public class HTMLStyleElementImpl extends HTMLElementImpl implements
		HTMLStyleElement {
	private CSSStyleSheet styleSheet; 
	
	public HTMLStyleElementImpl() {
		super("STYLE", true);
	}

	public HTMLStyleElementImpl(String name) {
		super(name, true);
	}
	
	private boolean disabled;
	public boolean getDisabled() {
		return this.disabled;
	}
	
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
		CSSStyleSheet sheet = this.styleSheet;
		if(sheet != null) {
			sheet.setDisabled(disabled);
		}
	}
	
	public String getMedia() {
		return this.getAttribute("media");
	}
	
	public void setMedia(String media) {
		this.setAttribute("media", media);
	}
	
	public String getType() {
		return this.getAttribute("type");
	}
	
	public void setType(String type) {
		this.setAttribute("type", type);
	}
	
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		if(org.lobobrowser.html.parser.HtmlParser.MODIFYING_KEY.equals(key) && data != Boolean.TRUE) {
			this.processStyle();
		}
		else if(com.steadystate.css.dom.CSSStyleSheetImpl.KEY_DISABLED_CHANGED.equals(key)) {
			this.informDocumentInvalid();
		}
		return super.setUserData(key, data, handler);
	}

	protected void processStyle() {
		this.styleSheet = null;
		if(CSSUtilities.matchesMedia(this.getMedia(), this.getUserAgentContext())) {
			String text = this.getRawInnerText(true);
			if(text != null && !"".equals(text)) {
				String processedText = CSSUtilities.preProcessCss(text);
				HTMLDocumentImpl doc = (HTMLDocumentImpl) this.getOwnerDocument();
				CSSOMParser parser = new CSSOMParser();
				String baseURI = doc.getBaseURI();
				InputSource is = CSSUtilities.getCssInputSourceForStyleSheet(processedText, baseURI);
				try {
					CSSStyleSheetImpl sheet = (CSSStyleSheetImpl) parser.parseStyleSheet(is);
					sheet.setOwnerNode(this);
					sheet.setHref(baseURI);
					doc.addStyleSheet(sheet);
					this.styleSheet = sheet;
					if(sheet instanceof CSSStyleSheetImpl) {
						CSSStyleSheetImpl sheetImpl = (CSSStyleSheetImpl) sheet; 
						sheetImpl.setDisabledOnly(this.disabled);
					}
					else {
						sheet.setDisabled(this.disabled);
					}
				} catch(Throwable err) {
					this.warn("Unable to parse style sheet", err);
				}
			}
		}
	}
	
	protected void appendInnerTextImpl(StringBuffer buffer) {
		// nop
	}
}
