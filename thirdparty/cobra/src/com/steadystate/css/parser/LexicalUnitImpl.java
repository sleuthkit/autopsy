/*
 * LexicalUnitImpl.java
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
 * $Id: LexicalUnitImpl.java,v 1.5 2008/02/24 13:37:09 xamjadmin Exp $
 */

package com.steadystate.css.parser;

import java.io.Serializable;
import org.w3c.css.sac.*;

/** 
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class LexicalUnitImpl implements LexicalUnit, Serializable {

/*
    public static final short SAC_OPERATOR_COMMA	= 0;
    public static final short SAC_OPERATOR_PLUS		= 1;
    public static final short SAC_OPERATOR_MINUS	= 2;
    public static final short SAC_OPERATOR_MULTIPLY	= 3;
    public static final short SAC_OPERATOR_SLASH	= 4;
    public static final short SAC_OPERATOR_MOD		= 5;
    public static final short SAC_OPERATOR_EXP		= 6;
    public static final short SAC_OPERATOR_LT		= 7;
    public static final short SAC_OPERATOR_GT		= 8;
    public static final short SAC_OPERATOR_LE		= 9;
    public static final short SAC_OPERATOR_GE		= 10;
    public static final short SAC_OPERATOR_TILDE	= 11;
    public static final short SAC_INHERIT		= 12;
    public static final short SAC_INTEGER		= 13;
    public static final short SAC_REAL		        = 14;
    public static final short SAC_EM		= 15;
    public static final short SAC_EX		= 16;
    public static final short SAC_PIXEL		= 17;
    public static final short SAC_INCH		= 18;
    public static final short SAC_CENTIMETER	= 19;
    public static final short SAC_MILLIMETER	= 20;
    public static final short SAC_POINT		= 21;
    public static final short SAC_PICA		= 22;
    public static final short SAC_PERCENTAGE		= 23;
    public static final short SAC_URI		        = 24;
    public static final short SAC_COUNTER_FUNCTION	= 25;
    public static final short SAC_COUNTERS_FUNCTION	= 26;
    public static final short SAC_RGBCOLOR		= 27;
    public static final short SAC_DEGREE		= 28;
    public static final short SAC_GRADIAN		= 29;
    public static final short SAC_RADIAN		= 30;
    public static final short SAC_MILLISECOND		= 31;
    public static final short SAC_SECOND		= 32;
    public static final short SAC_HERTZ		        = 33;
    public static final short SAC_KILOHERTZ		= 34;
    public static final short SAC_IDENT		        = 35;
    public static final short SAC_STRING_VALUE		= 36;
    public static final short SAC_ATTR		        = 37;
    public static final short SAC_RECT_FUNCTION		= 38;
    public static final short SAC_UNICODERANGE		= 39;
    public static final short SAC_SUB_EXPRESSION	= 40;
    public static final short SAC_FUNCTION		= 41;
    public static final short SAC_DIMENSION		= 42;
*/

    private short _type;
    private LexicalUnit _next;
    private LexicalUnit _prev;
//    private int _intVal;
    private float _floatVal;
    private String _dimension;
    private String _function;
    private LexicalUnit _params;
    private String _stringVal;

    protected LexicalUnitImpl(LexicalUnit previous, short type) {
        _type = type;
        _prev = previous;
        if (_prev != null) {
            ((LexicalUnitImpl)_prev)._next = this;
        }
    }

    /**
     * Integer
     */
    protected LexicalUnitImpl(LexicalUnit previous, int value) {
        this(previous, SAC_INTEGER);
//        _intVal = value;
        _floatVal = value;
    }

    /**
     * Dimension
     */
    protected LexicalUnitImpl(LexicalUnit previous, short type, float value) {
        this(previous, type);
        _floatVal = value;
    }

    /**
     * Unknown dimension
     */
    protected LexicalUnitImpl(
            LexicalUnit previous,
            short type,
            String dimension,
            float value) {
        this(previous, type);
        _dimension = dimension;
        _floatVal = value;
    }

    /**
     * String
     */
    protected LexicalUnitImpl(LexicalUnit previous, short type, String value) {
        this(previous, type);
        _stringVal = value;
    }

    /**
     * Function
     */
    protected LexicalUnitImpl(
            LexicalUnit previous,
            short type,
            String name,
            LexicalUnit params) {
        this(previous, type);
        _function = name;
        _params = params;
    }

    public short getLexicalUnitType() {
        return _type;
    }
    
    public LexicalUnit getNextLexicalUnit() {
        return _next;
    }
    
    public LexicalUnit getPreviousLexicalUnit() {
        return _prev;
    }
    
    public int getIntegerValue() {
//        return _intVal;
        return (int) _floatVal;
    }
    
    public float getFloatValue() {
        return _floatVal;
    }
    
    public String getDimensionUnitText() {
        switch (_type) {
        case SAC_EM:
            return "em";
        case SAC_EX:
            return "ex";
        case SAC_PIXEL:
            return "px";
        case SAC_INCH:
            return "in";
        case SAC_CENTIMETER:
            return "cm";
        case SAC_MILLIMETER:
            return "mm";
        case SAC_POINT:
            return "pt";
        case SAC_PICA:
            return "pc";
        case SAC_PERCENTAGE:
            return "%";
        case SAC_DEGREE:
            return "deg";
        case SAC_GRADIAN:
            return "grad";
        case SAC_RADIAN:
            return "rad";
        case SAC_MILLISECOND:
            return "ms";
        case SAC_SECOND:
            return "s";
        case SAC_HERTZ:
            return "Hz";
        case SAC_KILOHERTZ:
            return "kHz";
        case SAC_DIMENSION:
            return _dimension;
        }
        return "";
    }
    
    public String getFunctionName() {
        return _function;
    }
    
    public LexicalUnit getParameters() {
        return _params;
    }

    public String getStringValue() {
        return _stringVal;
    }

    public LexicalUnit getSubValues() {
        return _params;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        switch (_type) {
        case SAC_OPERATOR_COMMA:
            sb.append(",");
            break;
        case SAC_OPERATOR_PLUS:
            sb.append("+");
            break;
        case SAC_OPERATOR_MINUS:
            sb.append("-");
            break;
        case SAC_OPERATOR_MULTIPLY:
            sb.append("*");
            break;
        case SAC_OPERATOR_SLASH:
            sb.append("/");
            break;
        case SAC_OPERATOR_MOD:
            sb.append("%");
            break;
        case SAC_OPERATOR_EXP:
            sb.append("^");
            break;
        case SAC_OPERATOR_LT:
            sb.append("<");
            break;
        case SAC_OPERATOR_GT:
            sb.append(">");
            break;
        case SAC_OPERATOR_LE:
            sb.append("<=");
            break;
        case SAC_OPERATOR_GE:
            sb.append(">=");
            break;
        case SAC_OPERATOR_TILDE:
            sb.append("~");
            break;
        case SAC_OPERATOR_EQUALS:
            sb.append("=");
            break;
        case SAC_INHERIT:
            sb.append("inherit");
            break;
        case SAC_INTEGER:
            sb.append(String.valueOf(getIntegerValue()));
            break;
        case SAC_REAL:
            sb.append(trimFloat(getFloatValue()));
            break;
        case SAC_EM:
        case SAC_EX:
        case SAC_PIXEL:
        case SAC_INCH:
        case SAC_CENTIMETER:
        case SAC_MILLIMETER:
        case SAC_POINT:
        case SAC_PICA:
        case SAC_PERCENTAGE:
        case SAC_DEGREE:
        case SAC_GRADIAN:
        case SAC_RADIAN:
        case SAC_MILLISECOND:
        case SAC_SECOND:
        case SAC_HERTZ:
        case SAC_KILOHERTZ:
        case SAC_DIMENSION:
            sb.append(trimFloat(getFloatValue()))
              .append(getDimensionUnitText());
            break;
        case SAC_URI:
            sb.append("url(").append(getStringValue()).append(")");
            break;
        case SAC_COUNTER_FUNCTION:
            sb.append("counter(");
            appendParams(sb, _params);
            sb.append(")");
            break;
        case SAC_COUNTERS_FUNCTION:
            sb.append("counters(");
            appendParams(sb, _params);
            sb.append(")");
            break;
        case SAC_RGBCOLOR:
            sb.append("rgb(");
            appendParams(sb, _params);
            sb.append(")");
            break;
        case SAC_IDENT:
            sb.append(getStringValue());
            break;
        case SAC_STRING_VALUE:
            sb.append("\"").append(getStringValue()).append("\"");
            break;
        case SAC_ATTR:
            sb.append("attr(");
            appendParams(sb, _params);
            sb.append(")");
            break;
        case SAC_RECT_FUNCTION:
            sb.append("rect(");
            appendParams(sb, _params);
            sb.append(")");
            break;
        case SAC_UNICODERANGE:
            sb.append(getStringValue());
            break;
        case SAC_SUB_EXPRESSION:
            sb.append(getStringValue());
            break;
        case SAC_FUNCTION:
            sb.append(getFunctionName());
            appendParams(sb, _params);
            sb.append(")");
            break;
        case SAC_ALPHA_FUNCTION:
        	sb.append(getFunctionName() + "(");
        	appendParams(sb, _params);
            sb.append(")");
            break;
        }
        return sb.toString();
    }

    public String toDebugString() {
        StringBuffer sb = new StringBuffer();
        switch (_type) {
        case SAC_OPERATOR_COMMA:
            sb.append("SAC_OPERATOR_COMMA");
            break;
        case SAC_OPERATOR_PLUS:
            sb.append("SAC_OPERATOR_PLUS");
            break;
        case SAC_OPERATOR_MINUS:
            sb.append("SAC_OPERATOR_MINUS");
            break;
        case SAC_OPERATOR_MULTIPLY:
            sb.append("SAC_OPERATOR_MULTIPLY");
            break;
        case SAC_OPERATOR_SLASH:
            sb.append("SAC_OPERATOR_SLASH");
            break;
        case SAC_OPERATOR_MOD:
            sb.append("SAC_OPERATOR_MOD");
            break;
        case SAC_OPERATOR_EXP:
            sb.append("SAC_OPERATOR_EXP");
            break;
        case SAC_OPERATOR_LT:
            sb.append("SAC_OPERATOR_LT");
            break;
        case SAC_OPERATOR_GT:
            sb.append("SAC_OPERATOR_GT");
            break;
        case SAC_OPERATOR_LE:
            sb.append("SAC_OPERATOR_LE");
            break;
        case SAC_OPERATOR_GE:
            sb.append("SAC_OPERATOR_GE");
            break;
        case SAC_OPERATOR_TILDE:
            sb.append("SAC_OPERATOR_TILDE");
            break;
        case SAC_INHERIT:
            sb.append("SAC_INHERIT");
            break;
        case SAC_INTEGER:
            sb.append("SAC_INTEGER(")
                .append(String.valueOf(getIntegerValue()))
                .append(")");
            break;
        case SAC_REAL:
            sb.append("SAC_REAL(")
                .append(trimFloat(getFloatValue()))
                .append(")");
            break;
        case SAC_EM:
            sb.append("SAC_EM(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_EX:
            sb.append("SAC_EX(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_PIXEL:
            sb.append("SAC_PIXEL(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_INCH:
            sb.append("SAC_INCH(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_CENTIMETER:
            sb.append("SAC_CENTIMETER(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_MILLIMETER:
            sb.append("SAC_MILLIMETER(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_POINT:
            sb.append("SAC_POINT(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_PICA:
            sb.append("SAC_PICA(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_PERCENTAGE:
            sb.append("SAC_PERCENTAGE(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_DEGREE:
            sb.append("SAC_DEGREE(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_GRADIAN:
            sb.append("SAC_GRADIAN(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_RADIAN:
            sb.append("SAC_RADIAN(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_MILLISECOND:
            sb.append("SAC_MILLISECOND(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_SECOND:
            sb.append("SAC_SECOND(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_HERTZ:
            sb.append("SAC_HERTZ(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_KILOHERTZ:
            sb.append("SAC_KILOHERTZ(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_DIMENSION:
            sb.append("SAC_DIMENSION(")
                .append(trimFloat(getFloatValue()))
                .append(getDimensionUnitText())
                .append(")");
            break;
        case SAC_URI:
            sb.append("SAC_URI(url(")
                .append(getStringValue())
                .append("))");
            break;
        case SAC_COUNTER_FUNCTION:
            sb.append("SAC_COUNTER_FUNCTION(counter(");
            appendParams(sb, _params);
            sb.append("))");
            break;
        case SAC_COUNTERS_FUNCTION:
            sb.append("SAC_COUNTERS_FUNCTION(counters(");
            appendParams(sb, _params);
            sb.append("))");
            break;
        case SAC_RGBCOLOR:
            sb.append("SAC_RGBCOLOR(rgb(");
            appendParams(sb, _params);
            sb.append("))");
            break;
        case SAC_IDENT:
            sb.append("SAC_IDENT(")
                .append(getStringValue())
                .append(")");
            break;
        case SAC_STRING_VALUE:
            sb.append("SAC_STRING_VALUE(\"")
                .append(getStringValue())
                .append("\")");
            break;
        case SAC_ATTR:
            sb.append("SAC_ATTR(attr(");
            appendParams(sb, _params);
            sb.append("))");
            break;
        case SAC_RECT_FUNCTION:
            sb.append("SAC_RECT_FUNCTION(rect(");
            appendParams(sb, _params);
            sb.append("))");
            break;
        case SAC_UNICODERANGE:
            sb.append("SAC_UNICODERANGE(")
                .append(getStringValue())
                .append(")");
            break;
        case SAC_SUB_EXPRESSION:
            sb.append("SAC_SUB_EXPRESSION(")
                .append(getStringValue())
                .append(")");
            break;
        case SAC_FUNCTION:
            sb.append("SAC_FUNCTION(")
                .append(getFunctionName())
                .append("(");
            appendParams(sb, _params);
            sb.append("))");
            break;
        }
        return sb.toString();
    }

    private void appendParams(StringBuffer sb, LexicalUnit first) {
        LexicalUnit l = first;
        while (l != null) {
            sb.append(l.toString());
            l = l.getNextLexicalUnit();
        }
    }
    
    private String trimFloat(float f) {
        String s = String.valueOf(getFloatValue());
        return (f - (int) f != 0) ? s : s.substring(0, s.length() - 2);
    }

//    private static float value(char op, String s) {
//        return ((op == '-') ? -1 : 1) * Float.valueOf(s).floatValue();
//    }
//    
    public static LexicalUnit createNumber(LexicalUnit prev, float f) {
        if (f > (int) f) {
            return new LexicalUnitImpl(prev, LexicalUnit.SAC_REAL, f);
        } else {
            return new LexicalUnitImpl(prev, (int) f);
        }
    }
    
    public static LexicalUnit createPercentage(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_PERCENTAGE, f);
    }
    
    public static LexicalUnit createPixel(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_PIXEL, f);
    }
    
    public static LexicalUnit createCentimeter(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_CENTIMETER, f);
    }
    
    public static LexicalUnit createMillimeter(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_MILLIMETER, f);
    }
    
    public static LexicalUnit createInch(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_INCH, f);
    }
    
    public static LexicalUnit createPoint(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_POINT, f);
    }
    
    public static LexicalUnit createPica(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_PICA, f);
    }
    
    public static LexicalUnit createEm(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_EM, f);
    }
    
    public static LexicalUnit createEx(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_EX, f);
    }
    
    public static LexicalUnit createDegree(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_DEGREE, f);
    }
    
    public static LexicalUnit createRadian(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_RADIAN, f);
    }
    
    public static LexicalUnit createGradian(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_GRADIAN, f);
    }
    
    public static LexicalUnit createMillisecond(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_MILLISECOND, f);
    }
    
    public static LexicalUnit createSecond(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_SECOND, f);
    }
    
    public static LexicalUnit createHertz(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_HERTZ, f);
    }
    
    public static LexicalUnit createDimension(LexicalUnit prev, float f, String dim) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_DIMENSION, dim, f);
    }
    
    public static LexicalUnit createKiloHertz(LexicalUnit prev, float f) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_KILOHERTZ, f);
    }
    
    public static LexicalUnit createCounter(LexicalUnit prev, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_COUNTER_FUNCTION, "counter", params);
    }

    public static LexicalUnit createAlpha(LexicalUnit prev, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_ALPHA_FUNCTION, "alpha", params);
    }

    public static LexicalUnit createCounters(LexicalUnit prev, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_COUNTERS_FUNCTION, "counters", params);
    }
    
    public static LexicalUnit createAttr(LexicalUnit prev, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_ATTR, "attr", params);
    }
    
    public static LexicalUnit createRect(LexicalUnit prev, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_RECT_FUNCTION, "rect", params);
    }
    
    public static LexicalUnit createRgbColor(LexicalUnit prev, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_RGBCOLOR, "rgb", params);
    }
    
    public static LexicalUnit createFunction(LexicalUnit prev, String name, LexicalUnit params) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_FUNCTION, name, params);
    }

    public static LexicalUnit createString(LexicalUnit prev, String value) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_STRING_VALUE, value);
    }
    
    public static LexicalUnit createIdent(LexicalUnit prev, String value) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_IDENT, value);
    }
    
    public static LexicalUnit createURI(LexicalUnit prev, String value) {
        return new LexicalUnitImpl(prev, LexicalUnit.SAC_URI, value);
    }
    
    public static LexicalUnit createComma(LexicalUnit prev) {
        return new LexicalUnitImpl(prev, SAC_OPERATOR_COMMA);
    }
}
