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

class FloatingViewportBounds implements FloatingBounds {
	private final FloatingBounds prevBounds;
	private final boolean leftFloat;
	private final int y;
	private final int offsetFromBorder;
	private final int height;

	/**
	 * 
	 * @param prevBounds
	 * @param leftFloat
	 * @param y
	 * @param offsetFromBorder Width of floating box, including padding insets.
	 * @param height
	 */
	public FloatingViewportBounds(FloatingBounds prevBounds, boolean leftFloat, int y, int offsetFromBorder, int height) {
		this.prevBounds = prevBounds;
		this.leftFloat = leftFloat;
		this.y = y;
		this.offsetFromBorder = offsetFromBorder;
		this.height = height;
	}
	
	public int getLeft(int y) {
		int left = 0;
		if(this.leftFloat && y >= this.y && y < this.y + height) {
			left = this.offsetFromBorder;
		}
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int newLeft = prev.getLeft(y);
			if(newLeft > left) {
				left = newLeft;
			}
		}
		return left;
	}
	
	/**
	 * The offset from the right edge, not counting padding.
	 */
	public int getRight(int y) {
		int right = 0;
		if(!this.leftFloat && y >= this.y && y < this.y + this.height) {
			right = this.offsetFromBorder;
		}
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int newRight = prev.getRight(y);
			if(newRight > right) {
				right = newRight;
			}
		}
		return right;		
	}
	
	public int getClearY(int y) {
		int cleary = Math.max(y, this.y + this.height);
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int pcy = prev.getClearY(y);
			if(pcy > cleary) {
				cleary = pcy;
			}
		}
		return cleary;
	}

	public int getFirstClearY(int y) {
		int clearY = y;
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int prevClearY = prev.getFirstClearY(y);
			if(prevClearY != y) {
				clearY = prevClearY;
			}
		}
		if(clearY == y && y >= this.y && y < this.y + this.height) {
			clearY = this.y + this.height;
		}
		return clearY;
	}
	
	public int getLeftClearY(int y) {
		int cleary;
		if(this.leftFloat) {
			cleary = Math.max(y, this.y + this.height);
		}
		else {
			cleary = y;
		}
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int pcy = prev.getLeftClearY(y);
			if(pcy > cleary) {
				cleary = pcy;
			}
		}
		return cleary;
	}

	public int getRightClearY(int y) {
		int cleary;
		if(!this.leftFloat) {
			cleary = Math.max(y, this.y + this.height);
		}
		else {
			cleary = y;
		}
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int pcy = prev.getLeftClearY(y);
			if(pcy > cleary) {
				cleary = pcy;
			}
		}
		return cleary;
	}

	public int getMaxY() {
		int maxY = this.y + this.height;
		FloatingBounds prev = this.prevBounds;
		if(prev != null) {
			int prevMaxY = prev.getMaxY();
			if(prevMaxY > maxY) {
				maxY = prevMaxY;
			}
		}
		return maxY;
	}
	
	public boolean equals(Object other) {
        // Important for layout caching.
	    if(other == this) {
	        return true;
	    }
		if(!(other instanceof FloatingViewportBounds)) {
			return false;
		}
		FloatingViewportBounds olm = (FloatingViewportBounds) other;
		return olm.leftFloat == this.leftFloat &&
				olm.y == this.y &&
				olm.height == this.height &&
				olm.offsetFromBorder == this.offsetFromBorder &&
				org.lobobrowser.util.Objects.equals(olm.prevBounds, this.prevBounds);
	}

    public int hashCode() {
        return (this.leftFloat ? 1 : 0) ^ this.y ^ this.height ^ this.offsetFromBorder;
    }
}
