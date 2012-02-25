/*
 * CSSStyleSheetImpl.java
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
 * $Id: CSSStyleSheetImpl.java,v 1.5 2008/08/10 22:54:43 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import org.w3c.dom.*;
import org.w3c.dom.stylesheets.*;
import org.w3c.dom.css.*;
import org.w3c.css.sac.*;
import com.steadystate.css.parser.*;

/**
 * TODO: Setting the media list
 *
 * @author David Schweinsberg
 * @version $Release$
 */
public class CSSStyleSheetImpl implements CSSStyleSheet, Serializable {
	public static final String KEY_DISABLED_CHANGED = "styleSheet.disabled.changed";
    private boolean _disabled = false;
    private Node _ownerNode = null;
    private StyleSheet _parentStyleSheet = null;
    private String _href = null;
    private String _title = null;
    private MediaList _media = null;
    private CSSRule _ownerRule = null;
    private boolean _readOnly = false;
    private CSSRuleListImpl _rules = null;

    public CSSStyleSheetImpl() {
    }

    public String getType() {
        return "text/css";
    }

    public boolean getDisabled() {
        return _disabled;
    }

    /**
     * We will need to respond more fully if a stylesheet is disabled, probably
     * by generating an event for the main application.
     */
    public void setDisabled(boolean disabled) {
    	if(this._disabled != disabled) {
    		_disabled = disabled;
    		Node node = this.getOwnerNode();
    		if(node != null) {
    			node.setUserData(KEY_DISABLED_CHANGED, Boolean.valueOf(disabled), null);
    		}
    	}
    }

    public void setDisabledOnly(boolean disabled) {
    	this._disabled = disabled;
    }
    
    public Node getOwnerNode() {
        return _ownerNode;
    }

    public StyleSheet getParentStyleSheet() {
        return _parentStyleSheet;
    }

    public String getHref() {
        return _href;
    }

    public String getTitle() {
        return _title;
    }

    public MediaList getMedia() {
        return _media;
    }

    public CSSRule getOwnerRule() {
        return _ownerRule;
    }

    public CSSRuleList getCssRules() {
        return _rules;
    }

    public int insertRule(String rule, int index) throws DOMException {
        if (_readOnly) {
            throw new DOMExceptionImpl(
                DOMException.NO_MODIFICATION_ALLOWED_ERR,
                DOMExceptionImpl.READ_ONLY_STYLE_SHEET);
        }

        try {
            InputSource is = new InputSource(new StringReader(rule));
            CSSOMParser parser = new CSSOMParser();
            parser.setParentStyleSheet(this);
            CSSRule r = parser.parseRule(is);

            if (getCssRules().getLength() > 0) {

                // We need to check that this type of rule can legally go into
                // the requested position.
                int msg = -1;
                if (r.getType() == CSSRule.CHARSET_RULE) {

                    // Index must be 0, and there can be only one charset rule
                    if (index != 0) {
                        msg = DOMExceptionImpl.CHARSET_NOT_FIRST;
                    } else if (getCssRules().item(0).getType()
                            == CSSRule.CHARSET_RULE) {
                        msg = DOMExceptionImpl.CHARSET_NOT_UNIQUE;
                    }
                } else if (r.getType() == CSSRule.IMPORT_RULE) {

                    // Import rules must preceed all other rules (except
                    // charset rules)
                    if (index <= getCssRules().getLength()) {
                        for (int i = 0; i < index; i++) {
                            int rt = getCssRules().item(i).getType();
                            if ((rt != CSSRule.CHARSET_RULE)
                                    || (rt != CSSRule.IMPORT_RULE)) {
                                msg = DOMExceptionImpl.IMPORT_NOT_FIRST;
                                break;
                            }
                        }
                    }
                }

                if (msg > -1) {
                    throw new DOMExceptionImpl(
                        DOMException.HIERARCHY_REQUEST_ERR,
                        msg);
                }
            }

            // Insert the rule into the list of rules
            ((CSSRuleListImpl)getCssRules()).insert(r, index);

        } catch (ArrayIndexOutOfBoundsException e) {
            throw new DOMExceptionImpl(
                DOMException.INDEX_SIZE_ERR,
                DOMExceptionImpl.ARRAY_OUT_OF_BOUNDS,
                e.getMessage());
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
        return index;
    }

    public void deleteRule(int index) throws DOMException {
        if (_readOnly) {
            throw new DOMExceptionImpl(
                DOMException.NO_MODIFICATION_ALLOWED_ERR,
                DOMExceptionImpl.READ_ONLY_STYLE_SHEET);
        }

        try {
            ((CSSRuleListImpl)getCssRules()).delete(index);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new DOMExceptionImpl(
                DOMException.INDEX_SIZE_ERR,
                DOMExceptionImpl.ARRAY_OUT_OF_BOUNDS,
                e.getMessage());
        }
    }

    public boolean isReadOnly() {
        return _readOnly;
    }

    public void setReadOnly(boolean b) {
        _readOnly = b;
    }

    public void setOwnerNode(Node ownerNode) {
        _ownerNode = ownerNode;
    }

    public void setParentStyleSheet(StyleSheet parentStyleSheet) {
        _parentStyleSheet = parentStyleSheet;
    }

    public void setHref(String href) {
        _href = href;
    }

    public void setTitle(String title) {
        _title = title;
    }

    public void setMedia(String mediaText) {
        // MediaList _media = null;
    }

    public void setOwnerRule(CSSRule ownerRule) {
        _ownerRule = ownerRule;
    }
    
    public void setRuleList(CSSRuleListImpl rules) {
        _rules = rules;
    }
    
    public String toString() {
        return _rules.toString();
    }
}
