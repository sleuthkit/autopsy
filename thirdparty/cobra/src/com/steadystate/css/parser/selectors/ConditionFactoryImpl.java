/*
 * ConditionFactoryImpl.java
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
 * $Id: ConditionFactoryImpl.java,v 1.4 2008/01/03 13:35:38 xamjadmin Exp $
 */

package com.steadystate.css.parser.selectors;

import org.w3c.css.sac.*;

public class ConditionFactoryImpl implements ConditionFactory {

    public CombinatorCondition createAndCondition(
        Condition first, 
        Condition second) throws CSSException {
        return new AndConditionImpl(first, second);
    }

    public CombinatorCondition createOrCondition(
        Condition first, 
        Condition second) throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public NegativeCondition createNegativeCondition(Condition condition)
        throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public PositionalCondition createPositionalCondition(
        int position,
        boolean typeNode, 
        boolean type) throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public AttributeCondition createAttributeCondition(
        String localName,
        String namespaceURI,
        boolean specified,
        String value) throws CSSException {
//        if ((namespaceURI != null) || !specified) {
//            throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
//        } else {
            return new AttributeConditionImpl(localName, value);
//        }
    }

    public AttributeCondition createIdCondition(String value)
        throws CSSException {
        return new IdConditionImpl(value);
    }

    public LangCondition createLangCondition(String lang)
	    throws CSSException {
    	return new LangConditionImpl(lang);
    }

    public AttributeCondition createOneOfAttributeCondition(
        String localName,
        String namespaceURI,
        boolean specified,
        String value) throws CSSException {
//        if ((namespaceURI != null) || !specified) {
//            throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
//        } else {
            return new OneOfAttributeConditionImpl(localName, value);
//        }
    }

    public AttributeCondition createBeginHyphenAttributeCondition(
        String localName,
        String namespaceURI,
        boolean specified,
        String value) throws CSSException {
//        if ((namespaceURI != null) || !specified) {
//            throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
//        } else {
            return new BeginHyphenAttributeConditionImpl(localName, value);
//        }
    }

    public AttributeCondition createClassCondition(
        String namespaceURI,
        String value) throws CSSException {
        return new ClassConditionImpl(value);
    }

    public AttributeCondition createPseudoClassCondition(
        String namespaceURI,
        String value) throws CSSException {
        return new PseudoClassConditionImpl(value);
    }

    public Condition createOnlyChildCondition() throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public Condition createOnlyTypeCondition() throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public ContentCondition createContentCondition(String data)
        throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }    
}
