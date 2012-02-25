/*
 * ParseTest.java
 *
 * Steady State CSS2 Parser
 *
 * Copyright (C) 1999, 2002 Steady State Software Ltd.  All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * To contact the authors of the library, write to Steady State Software Ltd.,
 * 49 Littleworth, Wing, Buckinghamshire, LU7 0JX, England
 *
 * http://www.steadystate.com/css/
 * mailto:css@steadystate.co.uk
 *
 * $Id: ParseTest.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.parser;

import java.io.*;
import org.w3c.css.sac.*;
import org.w3c.css.sac.helpers.ParserFactory;

/** 
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class ParseTest extends HandlerBase {

    private static final String PARSER = "com.steadystate.css.parser.SACParser";

    private int _propertyCounter = 0;
    private int _indentSize = 0;
  
    public ParseTest() {
        try {
            CSSOMParser.setProperty("org.w3c.css.sac.parser", PARSER);
            ParserFactory factory = new ParserFactory();
            Parser parser = factory.makeParser();
//            Parser parser = new SACParser();
            parser.setDocumentHandler(this);

//            Reader r = new FileReader("d:\\working\\CSS2Parser\\primary.css");
//            Reader r = new FileReader("d:\\project\\css2\\html40.css");
//            Reader r = new FileReader("d:\\project\\css2\\test.css");
//            BufferedReader r = new BufferedReader(new InputStreamReader(
//                    new FileInputStream("c:\\working\\CSS2Parser\\stylesheets\\test-unicode.css"),
//                    "UTF-16"));
//            Reader r = new FileReader("d:\\project\\css2\\single-color.css");
            Reader r = new FileReader("c:\\working\\css2parser\\stylesheets\\page_test.css");
            
            InputSource is = new InputSource(r);
//            InputSource is = new InputSource("file:///d:/project/css2/test-unicode.css");
//            InputSource is = new InputSource("file:///d:/project/css2/test.css");
            parser.parseStyleSheet(is);

        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ParseTest();
    }

    public void startDocument(InputSource source)
        throws CSSException {
        System.out.println("startDocument");
    }
    
    public void endDocument(InputSource source) throws CSSException {
        System.out.println("endDocument");
    }

    public void comment(String text) throws CSSException {
    }

    public void ignorableAtRule(String atRule) throws CSSException {
        System.out.println(atRule);
    }

    public void namespaceDeclaration(String prefix, String uri)
	    throws CSSException {
    }

    public void importStyle(String uri, SACMediaList media, 
			String defaultNamespaceURI)
	    throws CSSException {
        System.out.print("@import url(" + uri + ")");
        if (media.getLength() > 0) {
          System.out.println(" " + media.toString() + ";");
        } else {
          System.out.println(";");
        }
    }

    public void startMedia(SACMediaList media) throws CSSException {
        System.out.println(indent() + "@media " + media.toString() + " {");
        incIndent();
    }

    public void endMedia(SACMediaList media) throws CSSException {
        decIndent();
        System.out.println(indent() + "}");
    }

    public void startPage(String name, String pseudo_page) throws CSSException {
        System.out.print(indent() + "@page");
        if (name != null) {
            System.out.print(" " + name);
        }
        if (pseudo_page != null) {
            System.out.println(" " + pseudo_page);
        }
        System.out.println(" {");
        _propertyCounter = 0;
        incIndent();
    }

    public void endPage(String name, String pseudo_page) throws CSSException {
        System.out.println();
        decIndent();
        System.out.println(indent() + "}");
    }

    public void startFontFace() throws CSSException {
        System.out.println(indent() + "@font-face {");
        _propertyCounter = 0;
        incIndent();
    }

    public void endFontFace() throws CSSException {
        System.out.println();
        decIndent();
        System.out.println(indent() + "}");
    }

    public void startSelector(SelectorList selectors) throws CSSException {
        System.out.println(indent() + selectors.toString() + " {");
        _propertyCounter = 0;
        incIndent();
    }

    public void endSelector(SelectorList selectors) throws CSSException {
        System.out.println();
        decIndent();
        System.out.println(indent() + "}");
    }

    public void property(String name, LexicalUnit value, boolean important)
            throws CSSException {
        if (_propertyCounter++ > 0) {
            System.out.println(";");
        }
        System.out.print(indent() + name + ":");

        // Iterate through the chain of lexical units
        LexicalUnit nextVal = value;
        while (nextVal != null) {
//            System.out.print(" " + nextVal.toString());
            System.out.print(" " + ((LexicalUnitImpl)nextVal).toDebugString());
            nextVal = nextVal.getNextLexicalUnit();
        }

        // Is it important?
        if (important) {
            System.out.print(" !important");
        }
    }
    
    private String indent() {
        StringBuffer sb = new StringBuffer(16);
        for (int i = 0; i < _indentSize; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }
    
    private void incIndent() {
        _indentSize += 4;
    }

    private void decIndent() {
        _indentSize -= 4;
    }
}
