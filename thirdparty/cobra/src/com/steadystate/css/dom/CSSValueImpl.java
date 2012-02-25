/*
 * CSSValueImpl.java
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
 * $Id: CSSValueImpl.java,v 1.9 2009/01/18 14:30:22 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.util.logging.*;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.*;
import org.w3c.css.sac.*;
import org.w3c.dom.*;
import org.w3c.dom.css.*;
import com.steadystate.css.parser.CSSOMParser;
import com.steadystate.css.parser.LexicalUnitImpl;

/**
 * The <code>CSSValueImpl</code> class can represent either a
 * <code>CSSPrimitiveValue</code> or a <code>CSSValueList</code> so that
 * the type can successfully change when using <code>setCssText</code>.
 *
 * TO DO:
 * Float unit conversions,
 * A means of checking valid primitive types for properties
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class CSSValueImpl implements CSSPrimitiveValue, CSSValueList, Serializable {
	private static final Logger logger = Logger.getLogger(CSSValueImpl.class.getName());
    private Object _value = null;

    /**
     * Constructor
     */
    public CSSValueImpl(LexicalUnit value, boolean forcePrimitive) {
        if(value == null) {
            _value = null;
        }
        else if (value.getParameters() != null) {
            if (value.getLexicalUnitType() == LexicalUnit.SAC_RECT_FUNCTION) {
                // Rect
                _value = new RectImpl(value.getParameters());
            } else if (value.getLexicalUnitType() == LexicalUnit.SAC_RGBCOLOR) {
                // RGBColor
                _value = new RGBColorImpl(value.getParameters());
                LexicalUnit nextValue;
                if((nextValue = value.getNextLexicalUnit()) != null) {
                	Vector v = new Vector();
                	v.add(_value);
                	while(nextValue != null) {
                        if ((nextValue.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA)
                           && (nextValue.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_SLASH)) {
                           v.addElement(new CSSValueImpl(nextValue, true));
                        }
                		nextValue = nextValue.getNextLexicalUnit();
                	}
                	_value = v;
                }
            } else if (value.getLexicalUnitType() == LexicalUnit.SAC_COUNTER_FUNCTION) {
                // Counter
                _value = new CounterImpl(false, value.getParameters());
            } else if (value.getLexicalUnitType() == LexicalUnit.SAC_COUNTERS_FUNCTION) {
                // Counter
                _value = new CounterImpl(true, value.getParameters());
            } else {
                _value = value;
            }
        } else if (forcePrimitive || (value.getNextLexicalUnit() == null)) {
            // We need to be a CSSPrimitiveValue
            _value = value;
        } else {
            // We need to be a CSSValueList
            // Values in an "expr" can be seperated by "operator"s, which are
            // either '/' or ',' - ignore these operators
            Vector v = new Vector();
            LexicalUnit lu = value;
            while (lu != null) {
                if ((lu.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA)
                    && (lu.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_SLASH)) {
                    v.addElement(new CSSValueImpl(lu, true));
                }
                lu = lu.getNextLexicalUnit();
            }
            _value = v;
        }
    }

    public CSSValueImpl(LexicalUnit value) {
        this(value, false);
    }

    private String toString(Object value) {
    	if(value instanceof Vector) {
    		StringBuffer sb = new StringBuffer();
            Vector v = (Vector) value;
            int size = v.size();
            boolean firstTime = true;
            for(int i = 0; i < size; i++) {
            	Object element = v.elementAt(i);
            	if (element instanceof CSSValueImpl) {
            		Object itemValue = ((CSSValueImpl) element)._value;
            		if(itemValue instanceof LexicalUnit) {
            			LexicalUnit lu = (LexicalUnit) itemValue;
            			if(lu != null) {
                    		if(firstTime) {
                    			firstTime = false;
                    		}
                    		else {
                    			sb.append(" ");
                    		}
            				sb.append(lu.toString());
            				// See if there's a comma or slash after value.
            				LexicalUnit potentialOp = lu.getNextLexicalUnit();
            				if(potentialOp != null) {
            					int type = potentialOp.getLexicalUnitType();
            					switch(type) {
            					case LexicalUnit.SAC_OPERATOR_COMMA:
            						sb.append(",");
            						break;
            					case LexicalUnit.SAC_OPERATOR_SLASH:
            						sb.append("/");
            						break;
            					case LexicalUnit.SAC_OPERATOR_EQUALS:
            						sb.append("=");
            						break;
            					}
            				}
            			}
            		}
            		else if(itemValue instanceof RGBColor) {
                		if(firstTime) {
                			firstTime = false;
                		}
                		else {
                			sb.append(" ");
                		}
                		sb.append(itemValue.toString());
            		}
            		else if(itemValue instanceof Vector) {            			
                		if(firstTime) {
                			firstTime = false;
                		}
                		else {
                			sb.append(" ");
                		}
            			sb.append(this.toString(itemValue));
            		}
            		else if(itemValue != null) {
            			logger.warning("toString(): Unknown item value type: " + itemValue.getClass().getName());
            		}
            	}
            	else {
            		if(element != null) {
                		if(firstTime) {
                			firstTime = false;
                		}
                		else {
                			sb.append(" ");
                		}
            			sb.append(element.toString());
            		}            			
            	}
            }
            return sb.toString();    		
    	}
    	else {
    		return String.valueOf(value);
    	}
    }
    
    public String getCssText() {
    	return this.toString(_value);
    }

    public void setCssText(String cssText) throws DOMException {
        try {
            InputSource is = new InputSource(new StringReader(cssText));
            CSSOMParser parser = new CSSOMParser();
            CSSValueImpl v2 = (CSSValueImpl) parser.parsePropertyValue(is);
            _value = v2._value;
        } catch (Exception e) {
            throw new DOMExceptionImpl(
                DOMException.SYNTAX_ERR,
                DOMExceptionImpl.SYNTAX_ERROR,
                e.getMessage() );
        }
    }

    public short getCssValueType() {
        return (_value instanceof Vector) ? CSS_VALUE_LIST : CSS_PRIMITIVE_VALUE;
    }

    public short getPrimitiveType() {
        if (_value instanceof LexicalUnit) {
            LexicalUnit lu = (LexicalUnit) _value;
            switch (lu.getLexicalUnitType()) {
            case LexicalUnit.SAC_INHERIT:
                return CSS_IDENT;
            case LexicalUnit.SAC_INTEGER:
            case LexicalUnit.SAC_REAL:
                return CSS_NUMBER;
            case LexicalUnit.SAC_EM:
                return CSS_EMS;
            case LexicalUnit.SAC_EX:
                return CSS_EXS;
            case LexicalUnit.SAC_PIXEL:
                return CSS_PX;
            case LexicalUnit.SAC_INCH:
                return CSS_IN;
            case LexicalUnit.SAC_CENTIMETER:
                return CSS_CM;
            case LexicalUnit.SAC_MILLIMETER:
                return CSS_MM;
            case LexicalUnit.SAC_POINT:
                return CSS_PT;
            case LexicalUnit.SAC_PICA:
                return CSS_PC;
            case LexicalUnit.SAC_PERCENTAGE:
                return CSS_PERCENTAGE;
            case LexicalUnit.SAC_URI:
                return CSS_URI;
//            case LexicalUnit.SAC_COUNTER_FUNCTION:
//            case LexicalUnit.SAC_COUNTERS_FUNCTION:
//                return CSS_COUNTER;
//            case LexicalUnit.SAC_RGBCOLOR:
//                return CSS_RGBCOLOR;
            case LexicalUnit.SAC_DEGREE:
                return CSS_DEG;
            case LexicalUnit.SAC_GRADIAN:
                return CSS_GRAD;
            case LexicalUnit.SAC_RADIAN:
                return CSS_RAD;
            case LexicalUnit.SAC_MILLISECOND:
                return CSS_MS;
            case LexicalUnit.SAC_SECOND:
                return CSS_S;
            case LexicalUnit.SAC_HERTZ:
                return CSS_KHZ;
            case LexicalUnit.SAC_KILOHERTZ:
                return CSS_HZ;
            case LexicalUnit.SAC_IDENT:
                return CSS_IDENT;
            case LexicalUnit.SAC_STRING_VALUE:
                return CSS_STRING;
            case LexicalUnit.SAC_ATTR:
                return CSS_ATTR;
//            case LexicalUnit.SAC_RECT_FUNCTION:
//                return CSS_RECT;
            case LexicalUnit.SAC_UNICODERANGE:
            case LexicalUnit.SAC_SUB_EXPRESSION:
            case LexicalUnit.SAC_FUNCTION:
                return CSS_STRING;
            case LexicalUnit.SAC_DIMENSION:
                return CSS_DIMENSION;
            }
        } else if (_value instanceof RectImpl) {
            return CSS_RECT;
        } else if (_value instanceof RGBColorImpl) {
            return CSS_RGBCOLOR;
        } else if (_value instanceof CounterImpl) {
            return CSS_COUNTER;
        }
        return CSS_UNKNOWN;
    }

    public void setFloatValue(short unitType, float floatValue) throws DOMException {
        _value = LexicalUnitImpl.createNumber(null, floatValue);
    }

    public float getFloatValue(short unitType) throws DOMException {
        if (_value instanceof LexicalUnit) {
            LexicalUnit lu = (LexicalUnit) _value;
            return lu.getFloatValue();
        }
        throw new DOMExceptionImpl(
            DOMException.INVALID_ACCESS_ERR,
            DOMExceptionImpl.FLOAT_ERROR);

        // We need to attempt a conversion
//        return 0;
    }

    public void setStringValue(short stringType, String stringValue) throws DOMException {
        switch (stringType) {
        case CSS_STRING:
            _value = LexicalUnitImpl.createString(null, stringValue);
            break;
        case CSS_URI:
            _value = LexicalUnitImpl.createURI(null, stringValue);
            break;
        case CSS_IDENT:
            _value = LexicalUnitImpl.createIdent(null, stringValue);
            break;
        case CSS_ATTR:
//           _value = LexicalUnitImpl.createAttr(null, stringValue);
//            break;
            throw new DOMExceptionImpl(
                DOMException.NOT_SUPPORTED_ERR,
                DOMExceptionImpl.NOT_IMPLEMENTED);
        default:
            throw new DOMExceptionImpl(
                DOMException.INVALID_ACCESS_ERR,
                DOMExceptionImpl.STRING_ERROR);
        }
    }

    /**
     * TODO: return a value for a list type
     */
    public String getStringValue() throws DOMException {
        if (_value instanceof LexicalUnit) {
            LexicalUnit lu = (LexicalUnit) _value;
            if ((lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT)
                || (lu.getLexicalUnitType() == LexicalUnit.SAC_STRING_VALUE)
                || (lu.getLexicalUnitType() == LexicalUnit.SAC_URI)
                || (lu.getLexicalUnitType() == LexicalUnit.SAC_ATTR)) {
                return lu.getStringValue();
            }
        } else if (_value instanceof Vector) {
            return null;
        }

        throw new DOMExceptionImpl(
            DOMException.INVALID_ACCESS_ERR,
            DOMExceptionImpl.STRING_ERROR);
    }

    public Counter getCounterValue() throws DOMException {
        if ((_value instanceof Counter) == false) {
            throw new DOMExceptionImpl(
                DOMException.INVALID_ACCESS_ERR,
                DOMExceptionImpl.COUNTER_ERROR);
        }
        return (Counter) _value;
    }

    public Rect getRectValue() throws DOMException {
        if ((_value instanceof Rect) == false) {
            throw new DOMExceptionImpl(
                DOMException.INVALID_ACCESS_ERR,
                DOMExceptionImpl.RECT_ERROR);
        }
        return (Rect) _value;
    }

    public RGBColor getRGBColorValue() throws DOMException {
        if ((_value instanceof RGBColor) == false) {
            throw new DOMExceptionImpl(
                DOMException.INVALID_ACCESS_ERR,
                DOMExceptionImpl.RGBCOLOR_ERROR);
        }
        return (RGBColor) _value;
    }

    public int getLength() {
        return (_value instanceof Vector) ? ((Vector)_value).size() : 0;
    }

    public CSSValue item(int index) {
        return (_value instanceof Vector)
            ? ((CSSValue)((Vector)_value).elementAt(index))
            : null;
    }

    public String toString() {
        return getCssText();
    }
}
