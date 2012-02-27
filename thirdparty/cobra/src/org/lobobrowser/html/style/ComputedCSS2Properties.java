/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The XAMJ Project

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
package org.lobobrowser.html.style;

public class ComputedCSS2Properties extends AbstractCSS2Properties {
	public ComputedCSS2Properties(CSS2PropertiesContext context) {
		super(context);
	}

	protected void setPropertyValueLC(String lowerCaseName, String value) {
		throw new java.lang.IllegalAccessError("Style properties cannot be set in this instance.");
	}

	protected void checkSetProperty() {
		throw new java.lang.IllegalAccessError("Style properties cannot be set in this instance.");
	}
	
	public void internalSetLC(String lowerCaseName, String value) {
	    // Should only be called right after creation of the CSS object.
	    // Properties need to be "unimportant" otherwise they won't get overridden.
	    super.setPropertyValueLCAlt(lowerCaseName, value, false);
	}
}
