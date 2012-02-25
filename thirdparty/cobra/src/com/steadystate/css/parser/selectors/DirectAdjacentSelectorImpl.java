/*
 * DirectAdjacentSelectorImpl.java
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
 * $Id: DirectAdjacentSelectorImpl.java,v 1.4 2008/01/03 13:35:39 xamjadmin Exp $
 */

package com.steadystate.css.parser.selectors;

import java.io.Serializable;
import org.w3c.css.sac.*;

public class DirectAdjacentSelectorImpl implements SiblingSelector, Serializable {

    private short _nodeType;
    private Selector _child;
    private SimpleSelector _directAdjacent;

    public DirectAdjacentSelectorImpl(short nodeType, Selector child, SimpleSelector directAdjacent) {
        _nodeType = nodeType;
        _child = child;
        _directAdjacent = directAdjacent;
    }

    public short getNodeType() {
        return _nodeType;
    }
    
    public short getSelectorType() {
        return Selector.SAC_DIRECT_ADJACENT_SELECTOR;
    }

    public Selector getSelector() {
        return _child;
    }

    public SimpleSelector getSiblingSelector() {
        return _directAdjacent;
    }
    
    public String toString() {
        return _child.toString() + " + " + _directAdjacent.toString();
    }
}
