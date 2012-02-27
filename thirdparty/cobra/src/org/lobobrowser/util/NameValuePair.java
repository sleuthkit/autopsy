/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
*/
/*
 * Created on Jun 12, 2005
 */
package org.lobobrowser.util;

/**
 * @author J. H. S.
 */
public class NameValuePair implements java.io.Serializable {
    private static final long serialVersionUID = 22574500600001010L;
	public final String name;
	public final String value;
	
	/**
	 * @param name
	 * @param value
	 */
	public NameValuePair(final String name, final String value) {
		super();
		this.name = name;
		this.value = value;
	}
	
	
	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return Returns the value.
	 */
	public String getValue() {
		return value;
	}
}
