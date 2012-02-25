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
package org.lobobrowser.html.renderer;

class LineMargin {
	private final int clearX;
	private final int clearY;
	private final LineMargin next;
	
	public LineMargin(LineMargin next, int cleary, int totalXOffset) {
		super();
		this.next = next;
		this.clearY = cleary;
		this.clearX = totalXOffset;
	}

	public int getClearY() {
		return clearY;
	}

	public LineMargin getNext() {
		return next;
	}

	public int getOffset() {
		return clearX;
	}	
	
	public boolean equals(Object other) {
		if(!(other instanceof LineMargin)) {
			return false;
		}
		LineMargin olm = (LineMargin) other;
		return olm.clearX == this.clearX && olm.clearY == this.clearY &&
		       org.lobobrowser.util.Objects.equals(olm.next, this.next);
	}
	
	public LineMargin translated(int yoffset, int xoffset) {
		int newClearY = this.clearY - yoffset;
		int newOffsetX = this.clearX - xoffset;
		if(newOffsetX < 0) {
			newOffsetX = 0;
		}
		LineMargin oldNext = this.next;
		LineMargin newNext = oldNext == null ? null : oldNext.translated(yoffset, xoffset);
		return new LineMargin(newNext, newClearY, newOffsetX);
	}
} 
