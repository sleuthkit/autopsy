/*
 * CSSStyleDeclarationImpl.java
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
 * $Id: CSSStyleDeclarationImpl.java,v 1.10 2008/02/02 19:55:23 xamjadmin Exp $
 */

package com.steadystate.css.dom;
import java.io.Serializable;
import java.io.StringReader;
import java.util.*;
import org.w3c.css.sac.*;
import org.w3c.dom.*;
import org.w3c.dom.css.*;
import com.steadystate.css.parser.*;

/**
 * @author David Schweinsberg
 * @version $Release$
 */
public class CSSStyleDeclarationImpl implements CSSStyleDeclaration, Serializable {
    private final CSSRule _parentRule;
    private final ArrayList _properties = new ArrayList(4);
    private final Map _propertiesMap = new HashMap(6);
    
    public CSSStyleDeclarationImpl(CSSRule parentRule) {
        _parentRule = parentRule;
    }

    public String getCssText() {
        StringBuffer sb = new StringBuffer();
        sb.append("{");
        //if newlines requested in text
        //sb.append("\n");
        ArrayList props = this._properties;
        int size = props.size();
        for (int i = 0; i < size; ++i) {
            Property p = (Property) props.get(i);
            if (p != null) {
                sb.append(p.toString());
            }
            if (i < size - 1) {
                sb.append("; ");
            }
            //if newlines requested in text
            //sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    public void setCssText(String cssText) throws DOMException {
        try {
            InputSource is = new InputSource(new StringReader(cssText));
            CSSOMParser parser = new CSSOMParser();
            this._properties.clear();
            this._propertiesMap.clear();
            parser.parseStyleDeclaration(this, is);
        } catch (Exception e) {
            throw new DOMExceptionImpl(
                DOMException.SYNTAX_ERR,
                DOMExceptionImpl.SYNTAX_ERROR,
                e.getMessage());
        }
    }

    public String getPropertyValue(String propertyName) {
        Property p = getPropertyDeclaration(propertyName);
        return (p != null) ? p.getValue().getCssText() : "";
    }

    public CSSValue getPropertyCSSValue(String propertyName) {
        Property p = getPropertyDeclaration(propertyName);
        return (p != null) ? p.getValue() : null;
    }

    public String removeProperty(String propertyName) throws DOMException {
    	Map propsMap = this._propertiesMap;
    	Property prop = (Property) propsMap.remove(propertyName.toLowerCase());
    	if(prop != null) {
    		this._properties.remove(prop);
    		CSSValue value = prop.getValue();
    		return value == null ? "" : value.getCssText();
    	}
    	return "";
    }

    public String getPropertyPriority(String propertyName) {
        Property p = getPropertyDeclaration(propertyName);
        if (p != null) {
            return p.isImportant() ? "important" : "";
        } else {
            return "";
        }
    }

    public void setProperty(
            String propertyName,
            String value,
            String priority ) throws DOMException {
        try {
            InputSource is = new InputSource(new StringReader(value));
            CSSOMParser parser = new CSSOMParser();
            CSSValue expr = parser.parsePropertyValue(is);
            Property p = getPropertyDeclaration(propertyName);
            boolean important = (priority != null)
                ? priority.equalsIgnoreCase("important")
                : false;
            if (p == null) {
                p = new Property(propertyName, expr, important);
                addProperty(p);
            } else {
                p.setValue(expr);
                p.setImportant(important);
            }
        } catch (Exception e) {
            throw new DOMExceptionImpl(
            DOMException.SYNTAX_ERR,
            DOMExceptionImpl.SYNTAX_ERROR,
            e.getMessage());
        }
    }
    
    public int getLength() {
        return _properties.size();
    }

    public String item(int index) {
        Property p = (Property) _properties.get(index);
        return (p != null) ? p.getName() : "";
    }

    public CSSRule getParentRule() {
        return _parentRule;
    }

    public void addProperty(Property p) {
    	_propertiesMap.put(p.getName().toLowerCase(), p);
        _properties.add(p);
    }

    private Property getPropertyDeclaration(String name) {
    	// Must visit from last to first. Bbc.co.uk is a
    	// site that has multiple property names in one declaration.
    	return (Property) this._propertiesMap.get(name.toLowerCase());
    }

    public String toString() {
        return this.getCssText();
    }
}
