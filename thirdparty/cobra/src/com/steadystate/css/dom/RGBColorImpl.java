/*
 * RGBColorImpl.java
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
 * $Id: RGBColorImpl.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.io.Serializable;
import org.w3c.css.sac.*;
import org.w3c.dom.css.*;

public class RGBColorImpl implements RGBColor, Serializable {

    private CSSPrimitiveValue _red = null;
    private CSSPrimitiveValue _green = null;
    private CSSPrimitiveValue _blue = null;

    protected RGBColorImpl(LexicalUnit lu) {
        LexicalUnit next = lu;
        _red = new CSSValueImpl(next, true);
        next = next.getNextLexicalUnit();
        next = next.getNextLexicalUnit();
        _green = new CSSValueImpl(next, true);
        next = next.getNextLexicalUnit();
        next = next.getNextLexicalUnit();
        _blue = new CSSValueImpl(next, true);
    }

    protected RGBColorImpl() {
    }
    
    public CSSPrimitiveValue getRed() {
        return _red;
    }

    public void setRed(CSSPrimitiveValue red) {
        _red = red;
    }

    public CSSPrimitiveValue getGreen() {
        return _green;
    }

    public void setGreen(CSSPrimitiveValue green) {
        _green = green;
    }

    public CSSPrimitiveValue getBlue() {
        return _blue;
    }

    public void setBlue(CSSPrimitiveValue blue) {
        _blue = blue;
    }

    public String toString() {
        return
            "rgb(" +
            _red.toString() + ", " +
            _green.toString() + ", " +
            _blue.toString() + ")";
    }
}
