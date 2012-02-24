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
 * Created on Oct 8, 2005
 */
package org.lobobrowser.html.domimpl;

import java.util.*;
import org.w3c.dom.html2.*;
import org.w3c.dom.*;

class ElementFactory {
	private final Map builders = new HashMap(80);
	
	private ElementFactory() {
		// This sets up builders for each known element tag.		
		Map builders = this.builders;
		builders.put("HTML", new HTMLElementBuilder.Html());
		builders.put("TITLE", new HTMLElementBuilder.Title());
		builders.put("BASE", new HTMLElementBuilder.Base());
		
		HTMLElementBuilder div = new HTMLElementBuilder.Div();
		builders.put("DIV", div);
		builders.put("DL", div);
		
		builders.put("BODY", new HTMLElementBuilder.Body());
		builders.put("CENTER", new HTMLElementBuilder.Center());
		builders.put("CAPTION", new HTMLElementBuilder.Center());
		builders.put("PRE", new HTMLElementBuilder.Pre());
		builders.put("P", new HTMLElementBuilder.P());

		HTMLElementBuilder bq = new HTMLElementBuilder.Blockquote();
		builders.put("BLOCKQUOTE", bq);
		builders.put("DD", bq);
		
		builders.put("SPAN", new HTMLElementBuilder.Span());
		builders.put("SCRIPT", new HTMLElementBuilder.Script());
		builders.put("IMG", new HTMLElementBuilder.Img());
		builders.put("STYLE", new HTMLElementBuilder.Style());
		builders.put("LINK", new HTMLElementBuilder.Link());
		builders.put("A", new HTMLElementBuilder.Anchor());
		builders.put("ANCHOR", new HTMLElementBuilder.Anchor());
		builders.put("TABLE", new HTMLElementBuilder.Table());
		builders.put("TD", new HTMLElementBuilder.Td());
		builders.put("TH", new HTMLElementBuilder.Th());
		builders.put("TR", new HTMLElementBuilder.Tr());
		builders.put("FORM", new HTMLElementBuilder.Form());
		builders.put("INPUT", new HTMLElementBuilder.Input());
		builders.put("BUTTON", new HTMLElementBuilder.Button());
		builders.put("TEXTAREA", new HTMLElementBuilder.Textarea());
		builders.put("SELECT", new HTMLElementBuilder.Select());
		builders.put("OPTION", new HTMLElementBuilder.Option());
		builders.put("FRAMESET", new HTMLElementBuilder.Frameset());
		builders.put("FRAME", new HTMLElementBuilder.Frame());
		builders.put("IFRAME", new HTMLElementBuilder.IFrame());
		builders.put("UL", new HTMLElementBuilder.Ul());
		builders.put("OL", new HTMLElementBuilder.Ol());
		builders.put("LI", new HTMLElementBuilder.Li());
		builders.put("HR", new HTMLElementBuilder.Hr());
		builders.put("BR", new HTMLElementBuilder.Br());
		builders.put("OBJECT", new HTMLElementBuilder.HtmlObject());
		builders.put("APPLET", new HTMLElementBuilder.Applet());
		builders.put("EMBED", new HTMLElementBuilder.NonStandard());
		builders.put("FONT", new HTMLElementBuilder.Font());
		builders.put("BASEFONT", new HTMLElementBuilder.BaseFont());
		
		builders.put("TT", new HTMLElementBuilder.Tt());
		builders.put("CODE", new HTMLElementBuilder.Code());
		builders.put("SMALL", new HTMLElementBuilder.Small());
		builders.put("BIG", new HTMLElementBuilder.Big());
		builders.put("B", new HTMLElementBuilder.Strong());
		builders.put("STRONG", new HTMLElementBuilder.Strong());
		
		builders.put("U", new HTMLElementBuilder.Underline());
		builders.put("STRIKE", new HTMLElementBuilder.Strike());
		builders.put("SUP", new HTMLElementBuilder.Sup());
		builders.put("SUB", new HTMLElementBuilder.Sub());
		
		HTMLElementBuilder em = new HTMLElementBuilder.Em();
		builders.put("I", em);
		builders.put("EM", em);
		builders.put("CITE", em);

		HTMLElementBuilder heading = new HTMLElementBuilder.Heading();
		builders.put("H1", heading);
		builders.put("H2", heading);
		builders.put("H3", heading);
		builders.put("H4", heading);
		builders.put("H5", heading);
		builders.put("H6", heading);
	}
	
	private static ElementFactory instance = new ElementFactory();
	
	public static ElementFactory getInstance() {
		return instance;
	}
	
	public final HTMLElement createElement(HTMLDocumentImpl document, String name) throws DOMException {
		String normalName = name.toUpperCase();
		// No need to synchronize; read-only map at this point.
		HTMLElementBuilder builder = (HTMLElementBuilder) this.builders.get(normalName);
		if(builder == null) {
			//TODO: IE would assume name is html text here?
			HTMLElementImpl element = new HTMLElementImpl(name);
			element.setOwnerDocument(document);
			return element;
		}
		else {
			return builder.create(document, name);
		}
	}
}
