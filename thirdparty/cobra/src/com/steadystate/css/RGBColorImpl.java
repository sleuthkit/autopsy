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
 */

package com.steadystate.css;

import org.w3c.css.sac.*;
import org.w3c.dom.css.CSSPrimitiveValue;
import com.steadystate.css.dom.CSSValueImpl;
import com.steadystate.css.parser.*;

/**
 *
 * @author  David Schweinsberg
 * @version $Release$
 * @deprecated As of 0.9.0, replaced by {@link com.steadystate.css.dom.RGBColorImpl}
 */
public class RGBColorImpl extends com.steadystate.css.dom.RGBColorImpl {

    protected RGBColorImpl(HexColor h) {
        setRed(new CSSValueImpl(
            LexicalUnitImpl.createNumber(null, h.getRed()),
            true));
        setGreen(new CSSValueImpl(
            LexicalUnitImpl.createNumber(null, h.getGreen()),
            true));
        setBlue(new CSSValueImpl(
            LexicalUnitImpl.createNumber(null, h.getBlue()),
            true));
    }
/*
    protected RGBColorImpl( CSSValue value )
    {
        if( value.getValueType() != CSSValue.CSS_VALUE_LIST )
        return;

        CSSValueList vl = (CSSValueList) value;

        if( vl.getLength() != 3 )
        return;

        _red   = (CSSPrimitiveValue) vl.item( 0 );
        _green = (CSSPrimitiveValue) vl.item( 1 );
        _blue  = (CSSPrimitiveValue) vl.item( 2 );
    }
*/
}
