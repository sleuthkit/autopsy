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
package org.lobobrowser.html.style;

import java.awt.Image;

public class ListStyle {
	public static final int TYPE_UNSET = 256;
	public static final int TYPE_NONE = 0;
	public static final int TYPE_DISC = 1;
	public static final int TYPE_CIRCLE = 2;
	public static final int TYPE_SQUARE = 3;
	public static final int TYPE_DECIMAL = 4;
	public static final int TYPE_LOWER_ALPHA = 5;
	public static final int TYPE_UPPER_ALPHA = 6;
	public static final int TYPE_LOWER_LATIN = 7;
	public static final int TYPE_UPPER_LATIN = 8;
	
	public static final int POSITION_UNSET = 0;
	public static final int POSITION_INSIDE = 0;
	public static final int POSITION_OUTSIDE = 0;
	
	public int type;
	public java.awt.Image image;
	public int position;
	
	public ListStyle(final int type, final Image image, final int position) {
		super();
		this.type = type;
		this.image = image;
		this.position = position;
	}	
	
	public ListStyle() {
	}
}
