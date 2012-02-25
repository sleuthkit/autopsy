/*
 * CSS2Parser.java
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
 * $Id: CSS2Parser.java,v 1.5 2008/01/03 13:35:40 xamjadmin Exp $
 */
package com.steadystate.css;
import java.io.*;
import org.w3c.dom.*;
import org.w3c.dom.css.*;
import org.w3c.css.sac.InputSource;
import com.steadystate.css.parser.CSSOMParser;

/**
 *
 * @author  David Schweinsberg
 * @version $Release$
 * @deprecated As of 0.9.0, replaced by
 * {@link com.steadystate.css.parsers.CSSOMParser}
 */
public class CSS2Parser {

    private CSSOMParser _parser = null;
    private InputSource _is = null;
    
    public CSS2Parser(
            Reader stream,
            Node ownerNode,
            String href,
            String title,
            String media) {
        _parser = new CSSOMParser();
        _is = new InputSource(stream);
    }

    public CSS2Parser(
            InputStream stream,
            Node ownerNode,
            String href,
            String title,
            String media) {
        this(new InputStreamReader(stream), ownerNode, href, title, media);
    }

    public CSS2Parser(Reader stream) {
        this(stream, null, null, null, null);
    }

    public CSS2Parser(InputStream stream) {
        this(stream, null, null, null, null);
    }
/*
    public CSS2Parser(
            InputStream stream,
            StyleSheet parentStyleSheet,
            CSSRule ownerRule,
            String href,
            String title,
            String media) {
        _parentStyleSheet = parentStyleSheet;
        _ownerRule = ownerRule;
        _href = href;
        _title = title;
        _media = media;
    }

    public CSS2Parser(
            Reader stream,
            StyleSheet parentStyleSheet,
            CSSRule ownerRule,
            String href,
            String title,
            String media) {
        _parser = new CSSOMParser();
        _is = new InputSource(stream);
    }
*/    
    public CSSStyleSheet styleSheet() {
        try {
            return _parser.parseStyleSheet(_is);
        } catch (IOException e) {
            return null;
        }
    }

    public CSSRuleList styleSheetRuleList() throws IOException {
        return null;
    }

    public CSSCharsetRule charsetRule() throws IOException {
        return null;
    }

    public CSSUnknownRule unknownRule() throws IOException {
        return null;
    }

    public CSSImportRule importRule() throws IOException {
        return null;
    }

    public CSSMediaRule mediaRule() throws IOException {
        return null;
    }

    public CSSPageRule pageRule() throws IOException {
        return null;
    }

    public CSSFontFaceRule fontFaceRule() throws IOException {
        return null;
    }

    public CSSStyleRule styleRule() throws IOException {
        return null;
    }
    
    public CSSStyleDeclaration styleDeclaration() {
        try {
            return _parser.parseStyleDeclaration(_is);
        } catch (IOException e) {
            return null;
        }
    }

    public CSSValue expr() {
        try {
            return _parser.parsePropertyValue(_is);
        } catch (IOException e) {
            return null;
        }
    }
}