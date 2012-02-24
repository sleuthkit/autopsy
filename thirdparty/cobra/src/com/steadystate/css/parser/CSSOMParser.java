/*
 * CSSOMParser.java
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
 * $Id: CSSOMParser.java,v 1.11 2008/03/08 17:53:59 xamjadmin Exp $
 */
 
package com.steadystate.css.parser;

import java.io.*;
import java.util.*;
import org.w3c.dom.css.*;
import org.w3c.css.sac.*;
import com.steadystate.css.dom.*;

/** 
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class CSSOMParser {    
    private static final String PARSER = "com.steadystate.css.parser.SACParser";

    private Parser _parser = null;
    private CSSStyleSheetImpl _parentStyleSheet = null;
    //private CSSRule _parentRule = null;

    /** Creates new CSSOMParser */
    public CSSOMParser() {
        try {
        	//Modifying to always use local parser. 
        	//The commented code does not perform as
        	//well and has class loader issues. (Jose 6/3/2007)
        	
            //setProperty("org.w3c.css.sac.parser", PARSER);
            //ParserFactory factory = new ParserFactory();
            //_parser = factory.makeParser();
        	
        	_parser = new com.steadystate.css.parser.SACParser();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public CSSStyleSheet parseStyleSheet(InputSource source) throws IOException {
        CSSOMHandler handler = new CSSOMHandler();
        _parser.setDocumentHandler(handler);
        _parser.parseStyleSheet(source);
        return (CSSStyleSheet) handler.getRoot();
    }
    
    public CSSStyleDeclaration parseStyleDeclaration(InputSource source)
            throws IOException {
        CSSStyleDeclarationImpl sd = new CSSStyleDeclarationImpl(null);
        parseStyleDeclaration(sd, source);
        return sd;
    }
    
    public void parseStyleDeclaration(CSSStyleDeclaration sd, InputSource source)
            throws IOException {
        Stack nodeStack = new Stack();
        nodeStack.push(sd);
        CSSOMHandler handler = new CSSOMHandler(nodeStack);
        _parser.setDocumentHandler(handler);
        _parser.parseStyleDeclaration(source);
    }
    
    public CSSValue parsePropertyValue(InputSource source) throws IOException {
        CSSOMHandler handler = new CSSOMHandler();
        _parser.setDocumentHandler(handler);
        return new CSSValueImpl(_parser.parsePropertyValue(source));
    }
    
    public CSSRule parseRule(InputSource source) throws IOException {
        CSSOMHandler handler = new CSSOMHandler();
        _parser.setDocumentHandler(handler);
        _parser.parseRule(source);
        return (CSSRule) handler.getRoot();
    }
    
    public SelectorList parseSelectors(InputSource source) throws IOException {
        HandlerBase handler = new HandlerBase();
        _parser.setDocumentHandler(handler);
        return _parser.parseSelectors(source);
    }

    public void setParentStyleSheet(CSSStyleSheetImpl parentStyleSheet) {
        _parentStyleSheet = parentStyleSheet;
    }

//    public void setParentRule(CSSRule parentRule) {
//        _parentRule = parentRule;
//    }
    
    class CSSOMHandler implements DocumentHandler {
        
        private Stack _nodeStack;
        private Object _root = null;

        public CSSOMHandler(Stack nodeStack) {
            _nodeStack = nodeStack;
        }
        
        public CSSOMHandler() {
            _nodeStack = new Stack();
        }
        
        public Object getRoot() {
            return _root;
        }
        
        public void startDocument(InputSource source) throws CSSException {
            if (_nodeStack.empty()) {
                CSSStyleSheetImpl ss = new CSSStyleSheetImpl();
                _parentStyleSheet = ss;

                // Create the rule list
                CSSRuleListImpl rules = new CSSRuleListImpl();
                ss.setRuleList(rules);
                _nodeStack.push(ss);
                _nodeStack.push(rules);
            } else {
                // Error
            }
        }

        public void endDocument(InputSource source) throws CSSException {

            // Pop the rule list and style sheet nodes
            _nodeStack.pop();
            _root = _nodeStack.pop();
        }

        public void comment(String text) throws CSSException {
        }

        public void ignorableAtRule(String atRule) throws CSSException {

            // Create the unknown rule and add it to the rule list
            CSSUnknownRuleImpl ir = new CSSUnknownRuleImpl(
                _parentStyleSheet,
                null,
                atRule);
            if (!_nodeStack.empty()) {
                ((CSSRuleListImpl)_nodeStack.peek()).add(ir);
            } else {
//                _nodeStack.push(ir);
                _root = ir;
            }
        }

        public void namespaceDeclaration(String prefix, String uri)
                throws CSSException {
        }

        public void importStyle(
                String uri,
                SACMediaList media, 
                String defaultNamespaceURI) throws CSSException {

            // Create the import rule and add it to the rule list
            CSSImportRuleImpl ir = new CSSImportRuleImpl(
                _parentStyleSheet,
                null,
                uri,
                new MediaListImpl(media));
            if (!_nodeStack.empty()) {
                ((CSSRuleListImpl)_nodeStack.peek()).add(ir);
            } else {
//                _nodeStack.push(ir);
                _root = ir;
            }
        }

        public void startMedia(SACMediaList media) throws CSSException {

            // Create the media rule and add it to the rule list
            CSSMediaRuleImpl mr = new CSSMediaRuleImpl(
                _parentStyleSheet,
                null,
                new MediaListImpl(media));
            if (!_nodeStack.empty()) {
                ((CSSRuleListImpl)_nodeStack.peek()).add(mr);
            }

            // Create the rule list
            CSSRuleListImpl rules = new CSSRuleListImpl();
            mr.setRuleList(rules);
            _nodeStack.push(mr);
            _nodeStack.push(rules);
        }

        public void endMedia(SACMediaList media) throws CSSException {

            // Pop the rule list and media rule nodes
            _nodeStack.pop();
            _root = _nodeStack.pop();
        }

        public void startPage(String name, String pseudo_page) throws CSSException {

            // Create the page rule and add it to the rule list
            CSSPageRuleImpl pr = new CSSPageRuleImpl(_parentStyleSheet, null, name, pseudo_page);
            if (!_nodeStack.empty()) {
                ((CSSRuleListImpl)_nodeStack.peek()).add(pr);
            }

            // Create the style declaration
            CSSStyleDeclarationImpl decl = new CSSStyleDeclarationImpl(pr);
            pr.setStyle(decl);
            _nodeStack.push(pr);
            _nodeStack.push(decl);
        }

        public void endPage(String name, String pseudo_page) throws CSSException {

            // Pop both the style declaration and the page rule nodes
            _nodeStack.pop();
            _root = _nodeStack.pop();
        }

        public void startFontFace() throws CSSException {

            // Create the font face rule and add it to the rule list
            CSSFontFaceRuleImpl ffr = new CSSFontFaceRuleImpl(_parentStyleSheet, null);
            if (!_nodeStack.empty()) {
                ((CSSRuleListImpl)_nodeStack.peek()).add(ffr);
            }

            // Create the style declaration
            CSSStyleDeclarationImpl decl = new CSSStyleDeclarationImpl(ffr);
            ffr.setStyle(decl);
            _nodeStack.push(ffr);
            _nodeStack.push(decl);
        }

        public void endFontFace() throws CSSException {

            // Pop both the style declaration and the font face rule nodes
            _nodeStack.pop();
            _root = _nodeStack.pop();
        }

        public void startSelector(SelectorList selectors) throws CSSException {

            // Create the style rule and add it to the rule list
            CSSStyleRuleImpl sr = new CSSStyleRuleImpl(_parentStyleSheet, null, selectors);
            if (!_nodeStack.empty()) {
                ((CSSRuleListImpl)_nodeStack.peek()).add(sr);
            }
            
            // Create the style declaration
            CSSStyleDeclarationImpl decl = new CSSStyleDeclarationImpl(sr);
            sr.setStyle(decl);
            _nodeStack.push(sr);
            _nodeStack.push(decl);
        }

        public void endSelector(SelectorList selectors) throws CSSException {

            // Pop both the style declaration and the style rule nodes
            _nodeStack.pop();
            _root = _nodeStack.pop();
        }

        public void property(String name, LexicalUnit value, boolean important)
                throws CSSException {
            CSSStyleDeclarationImpl decl =
                (CSSStyleDeclarationImpl) _nodeStack.peek();
            decl.addProperty(
                new Property(name, new CSSValueImpl(value), important));
        }
    }

    public static void setProperty(String key, String val) {
        Properties props = System.getProperties();
        props.put(key, val);
        System.setProperties(props);
    }
}
