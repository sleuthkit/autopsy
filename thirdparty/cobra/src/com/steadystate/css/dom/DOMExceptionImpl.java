/*
 * DOMExceptionImpl.java
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
 * $Id: DOMExceptionImpl.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.util.*;
import org.w3c.dom.*;

/** 
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class DOMExceptionImpl extends DOMException {

    public static final int SYNTAX_ERROR = 0;
    public static final int ARRAY_OUT_OF_BOUNDS = 1;
    public static final int READ_ONLY_STYLE_SHEET = 2;
    public static final int EXPECTING_UNKNOWN_RULE = 3;
    public static final int EXPECTING_STYLE_RULE = 4;
    public static final int EXPECTING_CHARSET_RULE = 5;
    public static final int EXPECTING_IMPORT_RULE = 6;
    public static final int EXPECTING_MEDIA_RULE = 7;
    public static final int EXPECTING_FONT_FACE_RULE = 8;
    public static final int EXPECTING_PAGE_RULE = 9;
    public static final int FLOAT_ERROR = 10;
    public static final int STRING_ERROR = 11;
    public static final int COUNTER_ERROR = 12;
    public static final int RECT_ERROR = 13;
    public static final int RGBCOLOR_ERROR = 14;
    public static final int CHARSET_NOT_FIRST = 15;
    public static final int CHARSET_NOT_UNIQUE = 16;
    public static final int IMPORT_NOT_FIRST = 17;
    public static final int NOT_FOUND = 18;
    public static final int NOT_IMPLEMENTED = 19;

    private static ResourceBundle _exceptionResource =
        ResourceBundle.getBundle(
            "com.steadystate.css.parser.ExceptionResource",
            Locale.getDefault());

    public DOMExceptionImpl(short code, int messageKey) {
        super(code, _exceptionResource.getString(keyString(messageKey)));
    }

    public DOMExceptionImpl(int code, int messageKey) {
        super((short) code, _exceptionResource.getString(keyString(messageKey)));
    }

    public DOMExceptionImpl(short code, int messageKey, String info) {
        super(code, _exceptionResource.getString(keyString(messageKey)));
    }

    private static String keyString(int key) {
        return "s" + String.valueOf(key);
    }
}
