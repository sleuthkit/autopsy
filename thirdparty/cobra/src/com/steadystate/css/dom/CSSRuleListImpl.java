/*
 * CSSRuleListImpl.java
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
 * $Id: CSSRuleListImpl.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.io.Serializable;
import java.util.Vector;
import org.w3c.dom.css.*;

public class CSSRuleListImpl implements CSSRuleList, Serializable {
    
    private Vector _rules = null;

    public CSSRuleListImpl() {
    }

    public int getLength() {
        return (_rules != null) ? _rules.size() : 0;
    }

    public CSSRule item(int index) {
        return (_rules != null) ? (CSSRule) _rules.elementAt(index) : null;
    }

    public void add(CSSRule rule) {
        if (_rules == null) {
            _rules = new Vector();
        }
        _rules.addElement(rule);
    }
    
    public void insert(CSSRule rule, int index) {
        if (_rules == null) {
            _rules = new Vector();
        }
        _rules.insertElementAt(rule, index);
    }
    
    public void delete(int index) {
        if (_rules == null) {
            _rules = new Vector();
        }
        _rules.removeElementAt(index);
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < getLength(); i++ ) {
            sb.append(item(i).toString()).append("\r\n");
        }
        return sb.toString();
    }
}
