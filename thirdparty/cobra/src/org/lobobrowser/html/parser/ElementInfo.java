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
 * Created on Oct 23, 2005
 */
package org.lobobrowser.html.parser;

import java.util.Set;

class ElementInfo {
	public final int endElementType;
	public final boolean childElementOk;
	public final Set stopTags;
	public final boolean noScriptElement;
	public final boolean decodeEntities;
	
	public static final int END_ELEMENT_FORBIDDEN = 0;
	public static final int END_ELEMENT_OPTIONAL = 1;
	public static final int END_ELEMENT_REQUIRED = 2;
	
	/**
	 * @param ok
	 * @param type
	 */
	public ElementInfo(boolean ok, int type) {
		this.childElementOk = ok;
		this.endElementType = type;
		this.stopTags = null;
		this.noScriptElement = false;
		this.decodeEntities = true;
	}

	/**
	 * @param ok
	 * @param type
	 */
	public ElementInfo(boolean ok, int type, Set stopTags) {
		this.childElementOk = ok;
		this.endElementType = type;
		this.stopTags = stopTags;
		this.noScriptElement = false;
		this.decodeEntities = true;
	}

	public ElementInfo(boolean ok, int type, Set stopTags, boolean noScriptElement) {
		this.childElementOk = ok;
		this.endElementType = type;
		this.stopTags = stopTags;
		this.noScriptElement = noScriptElement;
		this.decodeEntities = true;
	}
	
	public ElementInfo(boolean ok, int type, boolean decodeEntities) {
		this.childElementOk = ok;
		this.endElementType = type;
		this.stopTags = null;
		this.noScriptElement = false;
		this.decodeEntities = decodeEntities;
	}
}
