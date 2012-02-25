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
package org.lobobrowser.html.renderer;

import org.lobobrowser.util.Objects;

class CombinedFloatingBounds implements FloatingBounds {
	private final FloatingBounds floatBounds1;
	private final FloatingBounds floatBounds2;
	
	public CombinedFloatingBounds(final FloatingBounds floatBounds1, final FloatingBounds floatBounds2) {
		super();
		this.floatBounds1 = floatBounds1;
		this.floatBounds2 = floatBounds2;
	}

	public int getClearY(int y) {
		return Math.max(this.floatBounds1.getClearY(y), this.floatBounds2.getClearY(y));
	}

	public int getFirstClearY(int y) {
		return Math.max(this.floatBounds1.getFirstClearY(y), this.floatBounds2.getFirstClearY(y));
	}

	public int getLeft(int y) {
		return Math.max(this.floatBounds1.getLeft(y), this.floatBounds2.getLeft(y));
	}

	public int getLeftClearY(int y) {
		return Math.max(this.floatBounds1.getLeftClearY(y), this.floatBounds2.getLeftClearY(y));
	}

	public int getMaxY() {
		return Math.max(this.floatBounds1.getMaxY(), this.floatBounds2.getMaxY());
	}

	public int getRight(int y) {
		return Math.max(this.floatBounds1.getRight(y), this.floatBounds2.getRight(y));
	}

	public int getRightClearY(int y) {
		return Math.max(this.floatBounds1.getRightClearY(y), this.floatBounds2.getRightClearY(y));
	}

    public boolean equals(Object obj) {
        // Important for layout caching.
        if(!(obj instanceof CombinedFloatingBounds)) {
            return false;
        }
        CombinedFloatingBounds other = (CombinedFloatingBounds) obj;
        return 
            Objects.equals(other.floatBounds1, this.floatBounds1) &&
            Objects.equals(other.floatBounds2, this.floatBounds2);        
    }

    public int hashCode() {
        FloatingBounds fbounds1 = this.floatBounds1;
        FloatingBounds fbounds2 = this.floatBounds2;
        return (fbounds1 == null ? 0 : fbounds1.hashCode()) ^ (fbounds2 == null ? 0 : fbounds2.hashCode());
    }
}
