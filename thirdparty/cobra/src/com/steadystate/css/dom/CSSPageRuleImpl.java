/*
 * CSSPageRuleImpl.java
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
 * $Id: CSSPageRuleImpl.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import org.w3c.dom.*;
import org.w3c.dom.css.*;
import org.w3c.css.sac.*;
import com.steadystate.css.parser.*;

/**
 * TO DO:
 * Implement setSelectorText()
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class CSSPageRuleImpl implements CSSPageRule, Serializable {

    private CSSStyleSheetImpl _parentStyleSheet = null;
    private CSSRule _parentRule = null;
    private String _ident = null;
    private String _pseudoPage = null;
    private CSSStyleDeclaration _style = null;

    public CSSPageRuleImpl(
            CSSStyleSheetImpl parentStyleSheet,
            CSSRule parentRule,
            String ident,
            String pseudoPage) {
        _parentStyleSheet = parentStyleSheet;
        _parentRule = parentRule;
        _ident = ident;
        _pseudoPage = pseudoPage;
    }

    public short getType() {
        return PAGE_RULE;
    }

    public String getCssText() {
        String sel = getSelectorText();
        return "@page "
            + sel + ((sel.length() > 0) ? " " : "")
            + getStyle().getCssText();
    }

    public void setCssText(String cssText) throws DOMException {
        if (_parentStyleSheet != null && _parentStyleSheet.isReadOnly()) {
            throw new DOMExceptionImpl(
                DOMException.NO_MODIFICATION_ALLOWED_ERR,
                DOMExceptionImpl.READ_ONLY_STYLE_SHEET);
        }

        try {
            InputSource is = new InputSource(new StringReader(cssText));
            CSSOMParser parser = new CSSOMParser();
            CSSRule r = parser.parseRule(is);

            // The rule must be a page rule
            if (r.getType() == CSSRule.PAGE_RULE) {
                _ident = ((CSSPageRuleImpl)r)._ident;
                _pseudoPage = ((CSSPageRuleImpl)r)._pseudoPage;
                _style = ((CSSPageRuleImpl)r)._style;
            } else {
                throw new DOMExceptionImpl(
                    DOMException.INVALID_MODIFICATION_ERR,
                    DOMExceptionImpl.EXPECTING_PAGE_RULE);
            }
        } catch (CSSException e) {
            throw new DOMExceptionImpl(
                DOMException.SYNTAX_ERR,
                DOMExceptionImpl.SYNTAX_ERROR,
                e.getMessage());
        } catch (IOException e) {
            throw new DOMExceptionImpl(
                DOMException.SYNTAX_ERR,
                DOMExceptionImpl.SYNTAX_ERROR,
                e.getMessage());
        }
    }

    public CSSStyleSheet getParentStyleSheet() {
        return _parentStyleSheet;
    }

    public CSSRule getParentRule() {
        return _parentRule;
    }

    public String getSelectorText() {
        return ((_ident != null) ? _ident : "")
            + ((_pseudoPage != null) ? ":" + _pseudoPage : "");
    }

    public void setSelectorText(String selectorText) throws DOMException {
    }

    public CSSStyleDeclaration getStyle() {
        return _style;
    }

    protected void setIdent(String ident) {
        _ident = ident;
    }

    protected void setPseudoPage(String pseudoPage) {
        _pseudoPage = pseudoPage;
    }

    public void setStyle(CSSStyleDeclarationImpl style) {
        _style = style;
    }
}
