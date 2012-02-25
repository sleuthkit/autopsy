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

import java.awt.Insets;

import org.lobobrowser.html.domimpl.HTMLElementImpl;
import org.lobobrowser.util.gui.*;

public class BodyRenderState extends StyleSheetRenderState {
    //Note: BODY behaves like an inline element, but the root
    //block uses the element and treats its properties as those
    //of a block element.
    
	public BodyRenderState(RenderState prevRenderState, HTMLElementImpl element) {
		super(prevRenderState, element);
	}

	public void invalidate() {
		super.invalidate();
	}
	
	public BackgroundInfo getBackgroundInfo() {
		BackgroundInfo binfo = this.iBackgroundInfo;
		if(binfo != INVALID_BACKGROUND_INFO) {
			return binfo;
		}
		binfo = super.getBackgroundInfo();
		if(binfo == null || binfo.backgroundColor == null) {
			String bgcolor = this.element.getAttribute("bgcolor");
			if(bgcolor != null && bgcolor.length() != 0) {
				if(binfo == null) {
					binfo = new BackgroundInfo();
				}
				binfo.backgroundColor = ColorFactory.getInstance().getColor(bgcolor);
			}
		}
		this.iBackgroundInfo = binfo;
		return binfo;
	}

    public HtmlInsets getMarginInsets() {
        HtmlInsets insets = this.marginInsets;
        if(insets != INVALID_INSETS) {
            return insets;
        }
        insets = super.getMarginInsets();
        if(insets == null) {
            HTMLElementImpl element = this.element;
            String leftMargin = element.getAttribute("leftmargin");
            String rightMargin = element.getAttribute("rightmargin");
            String bottomMargin = element.getAttribute("rightmargin");
            String topMargin = element.getAttribute("topmargin");
            String marginWidth = element.getAttribute("marginwidth");
            String marginHeight = element.getAttribute("marginheight");
            if(leftMargin == null) {
                leftMargin = marginWidth;
            }
            if(rightMargin == null) {
                rightMargin = marginWidth;
            }
            if(topMargin == null) {
                topMargin = marginHeight;
            }
            if(bottomMargin == null) {
                bottomMargin = marginHeight;
            }
            if(leftMargin != null) {
                if(insets == null) {
                    insets = new HtmlInsets();
                }
                insets.left = HtmlValues.getOldSyntaxPixelSizeSimple(leftMargin, 0);
                insets.leftType = HtmlInsets.TYPE_PIXELS;
            }
            if(rightMargin != null) {
                if(insets == null) {
                    insets = new HtmlInsets();
                }
                insets.right = HtmlValues.getOldSyntaxPixelSizeSimple(rightMargin, 0);
                insets.rightType = HtmlInsets.TYPE_PIXELS;                
            }
            if(topMargin != null) {
                if(insets == null) {
                    insets = new HtmlInsets();
                }
                insets.top = HtmlValues.getOldSyntaxPixelSizeSimple(topMargin, 0);
                insets.topType = HtmlInsets.TYPE_PIXELS;                
            }
            if(bottomMargin != null) {
                if(insets == null) {
                    insets = new HtmlInsets();
                }
                insets.bottom = HtmlValues.getOldSyntaxPixelSizeSimple(bottomMargin, 0);
                insets.bottomType = HtmlInsets.TYPE_PIXELS;                
            }
        }
        this.marginInsets = insets; 
        return insets;
    }
	
	
}
