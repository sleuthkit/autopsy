/*
 * DescendantSelectorImpl.java
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
 * $Id: DescendantSelectorImpl.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.parser.selectors;

import java.io.Serializable;
import org.w3c.css.sac.*;

public class DescendantSelectorImpl implements DescendantSelector, Serializable {

    private Selector _parent;
    private SimpleSelector _simpleSelector;

    public DescendantSelectorImpl(Selector parent, SimpleSelector simpleSelector) {
        _parent = parent;
        _simpleSelector = simpleSelector;
    }

    public short getSelectorType() {
        return Selector.SAC_DESCENDANT_SELECTOR;
    }

    public Selector getAncestorSelector() {
        return _parent;
    }

    public SimpleSelector getSimpleSelector() {
        return _simpleSelector;
    }
    
    public String toString() {
        return getAncestorSelector().toString() + " " + getSimpleSelector().toString();
    }
}
