/*
 * MediaListImpl.java
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
 * $Id: MediaListImpl.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */

package com.steadystate.css.dom;

import java.io.Serializable;
import java.util.*;
import org.w3c.dom.*;
import org.w3c.dom.stylesheets.*;
import org.w3c.css.sac.*;

/**
 *
 * @author  David Schweinsberg
 * @version $Release$
 */
public class MediaListImpl implements MediaList, Serializable {

    private Vector _media = new Vector();

    public MediaListImpl(SACMediaList mediaList) {
        for (int i = 0; i < mediaList.getLength(); i++) {
            _media.addElement(mediaList.item(i));
        }
    }

    public String getMediaText() {
        StringBuffer sb = new StringBuffer("");
        for (int i = 0; i < _media.size(); i++) {
            sb.append(_media.elementAt(i).toString());
            if (i < _media.size() - 1) {
                sb.append( ", " );
            }
        }
        return sb.toString();
    }

    public void setMediaText(String mediaText) throws DOMException {
/*
        try
        {
            StringReader sr = new StringReader( mediaText );
            CSS2Parser parser = new CSS2Parser( sr );
            ASTMediaList ml = parser.mediaList();
            _media = ml._media;
        }
        catch( ParseException e )
        {
            throw new DOMExceptionImpl(
            DOMException.SYNTAX_ERR,
            DOMExceptionImpl.SYNTAX_ERROR,
            e.getMessage() );
        }
*/
    }

    public int getLength() {
        return _media.size();
    }

    public String item(int index) {
        return (index < _media.size()) ? (String) _media.elementAt(index) : null;
    }

    public void deleteMedium(String oldMedium) throws DOMException {
        for (int i = 0; i < _media.size(); i++) {
            String str = (String) _media.elementAt(i);
            if (str.equalsIgnoreCase(oldMedium)) {
                _media.removeElementAt(i);
                return;
            }
        }
        throw new DOMExceptionImpl(
            DOMException.NOT_FOUND_ERR,
            DOMExceptionImpl.NOT_FOUND);
    }

    public void appendMedium(String newMedium) throws DOMException {
        _media.addElement(newMedium);
    }

    public String toString() {
        return getMediaText();
    }
}
