/*
 * Copyright (c) 1999 World Wide Web Consortium,
 * (Massachusetts Institute of Technology, Institut National de
 * Recherche en Informatique et en Automatique, Keio University). All
 * Rights Reserved. This program is distributed under the W3C's Software
 * Intellectual Property License. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.
 * See W3C License http://www.w3.org/Consortium/Legal/ for more details.
 *
 * $Id: ParserFactory.java,v 1.4 2008/01/03 13:35:40 xamjadmin Exp $
 */
package org.w3c.css.sac.helpers;

import org.w3c.css.sac.Parser;

/**
 * @version $Revision: 1.4 $
 * @author  Philippe Le Hegaret
 */
public class ParserFactory {
    
    /**
     * Create a parser with given selectors factory and conditions factory.
     */    
    public Parser makeParser()
	throws ClassNotFoundException,
	       IllegalAccessException, 
	       InstantiationException,
 	       NullPointerException,
	       ClassCastException {
	String className = System.getProperty("org.w3c.css.sac.parser");
	if (className == null) {
	    throw new NullPointerException("No value for sac.parser property");
	} else {
	    return (Parser)(Class.forName(className).newInstance());
	}
    }
}
