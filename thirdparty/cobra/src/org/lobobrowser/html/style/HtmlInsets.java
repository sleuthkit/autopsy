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

import java.awt.Insets;

public class HtmlInsets {
    public static final int TYPE_UNDEFINED = 0;
    public static final int TYPE_PIXELS = 1;
    public static final int TYPE_AUTO = 2;    
    public static final int TYPE_PERCENT = 3;    
    
    public int top, bottom, left, right;
    
    /* Types assumed to be initialized as UNDEFINED */
    public int topType, bottomType, leftType, rightType;
    
    public HtmlInsets() {
    }

    public int getTop() {
        return top;
    }

    public void setTop(int top) {
        this.top = top;
    }

    public int getBottom() {
        return bottom;
    }

    public void setBottom(int bottom) {
        this.bottom = bottom;
    }

    public int getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public int getTopType() {
        return topType;
    }

    public void setTopType(int topType) {
        this.topType = topType;
    }

    public int getBottomType() {
        return bottomType;
    }

    public void setBottomType(int bottomType) {
        this.bottomType = bottomType;
    }

    public int getLeftType() {
        return leftType;
    }

    public void setLeftType(int leftType) {
        this.leftType = leftType;
    }

    public int getRightType() {
        return rightType;
    }

    public void setRightType(int rightType) {
        this.rightType = rightType;
    }
    
    public java.awt.Insets getAWTInsets(int defaultTop, int defaultLeft, int defaultBottom, int defaultRight, int availWidth, int availHeight, int autoX, int autoY) {
        int top = getInsetPixels(this.top, this.topType, defaultTop, availHeight, autoY);
        int left = getInsetPixels(this.left, this.leftType, defaultLeft, availWidth, autoX);
        int bottom = getInsetPixels(this.bottom, this.bottomType, defaultBottom, availHeight, autoY);
        int right = getInsetPixels(this.right, this.rightType, defaultRight, availWidth, autoX);
        return new Insets(top, left, bottom, right);        
    }

    public java.awt.Insets getSimpleAWTInsets(int availWidth, int availHeight) {
        int top = getInsetPixels(this.top, this.topType, 0, availHeight, 0);
        int left = getInsetPixels(this.left, this.leftType, 0, availWidth, 0);
        int bottom = getInsetPixels(this.bottom, this.bottomType, 0, availHeight, 0);
        int right = getInsetPixels(this.right, this.rightType, 0, availWidth, 0);
        return new Insets(top, left, bottom, right);        
    }

    private static int getInsetPixels(int value, int type, int defaultValue, int availSize, int autoValue) {
        if(type == TYPE_PIXELS) {
            return value;
        }
        else if(type == TYPE_UNDEFINED) {
            return defaultValue;
        }
        else if(type == TYPE_AUTO) {
            return autoValue;
        }
        else if(type == TYPE_PERCENT) {
            return (availSize * value) / 100;
        }
        else {
            throw new IllegalStateException();
        }
    }
    
    public String toString() {
        return "[" + this.top + "," + this.left + "," + this.bottom + "," + this.right + "]";
    }
}
