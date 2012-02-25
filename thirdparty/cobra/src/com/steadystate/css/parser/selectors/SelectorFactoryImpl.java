/*
 * SelectorFactoryImpl.java
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
 * $Id: SelectorFactoryImpl.java,v 1.4 2008/01/03 13:35:38 xamjadmin Exp $
 */

package com.steadystate.css.parser.selectors;

import org.w3c.css.sac.*;

public class SelectorFactoryImpl implements SelectorFactory {

    public ConditionalSelector createConditionalSelector(
        SimpleSelector selector,
        Condition condition) throws CSSException {
        return new ConditionalSelectorImpl(selector, condition);
    }

    public SimpleSelector createAnyNodeSelector() throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public SimpleSelector createRootNodeSelector() throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public NegativeSelector createNegativeSelector(SimpleSelector selector) 
	    throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public ElementSelector createElementSelector(String namespaceURI, String localName)
        throws CSSException {
        if (namespaceURI != null) {
            throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
        } else {
            return new ElementSelectorImpl(localName);
        }
    }

    public CharacterDataSelector createTextNodeSelector(String data)
        throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public CharacterDataSelector createCDataSectionSelector(String data)
        throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public ProcessingInstructionSelector createProcessingInstructionSelector(
        String target,
        String data) throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public CharacterDataSelector createCommentSelector(String data)
        throws CSSException {
        throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
    }

    public ElementSelector createPseudoElementSelector(
        String namespaceURI, 
        String pseudoName) throws CSSException {
        if (namespaceURI != null) {
            throw new CSSException(CSSException.SAC_NOT_SUPPORTED_ERR);
        } else {
            return new PseudoElementSelectorImpl(pseudoName);
        }
    }

    public DescendantSelector createDescendantSelector(
        Selector parent,
        SimpleSelector descendant) throws CSSException {
        return new DescendantSelectorImpl(parent, descendant);
    }

    public DescendantSelector createChildSelector(
        Selector parent,
        SimpleSelector child) throws CSSException {
        return new ChildSelectorImpl(parent, child);
    }

    public SiblingSelector createDirectAdjacentSelector(
        short nodeType,
        Selector child,
        SimpleSelector directAdjacent) throws CSSException {
        return new DirectAdjacentSelectorImpl(nodeType, child, directAdjacent);
    }
}
