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

import java.awt.Color;
import java.awt.Insets;

import org.lobobrowser.html.domimpl.HTMLElementImpl;

public class ImageRenderState extends StyleSheetRenderState {
	public ImageRenderState(RenderState prevRenderState, HTMLElementImpl element) {
		super(prevRenderState, element);
	}

	public HtmlInsets getMarginInsets() {
		HtmlInsets mi = this.marginInsets;
		if(mi != INVALID_INSETS) {
			return mi;
		}
		AbstractCSS2Properties props = this.getCssProperties();
		if(props == null) { 
			mi = null;
		}
		else {
			mi = HtmlValues.getMarginInsets(props, this);
		}
		if(mi == null) {
			int hspace = 0;
			int vspace = 0;
			boolean createNew = false;
			String hspaceText = this.element.getAttribute("hspace");
			if(hspaceText != null && hspaceText.length() != 0) {
				createNew = true;
				try {
					hspace = Integer.parseInt(hspaceText);
				} catch(NumberFormatException nfe) {
					// TODO: Percentages?
				}
			}
			String vspaceText = this.element.getAttribute("vspace");
			if(vspaceText != null && vspaceText.length() != 0) {
				createNew = true;
				try {
					vspace = Integer.parseInt(vspaceText);
				} catch(NumberFormatException nfe) {
					// TODO: Percentages?
				}
			}
			if(createNew) {
			    mi = new HtmlInsets();
			    mi.top = vspace;
			    mi.topType = HtmlInsets.TYPE_PIXELS;
			    mi.bottom = vspace;
			    mi.bottomType = HtmlInsets.TYPE_PIXELS;
			    mi.left = hspace;
			    mi.leftType = HtmlInsets.TYPE_PIXELS;
			    mi.right = hspace;
			    mi.rightType = HtmlInsets.TYPE_PIXELS;
			}
		}
		this.marginInsets = mi;
		return mi;
	}
	
	public BorderInfo getBorderInfo() {
	    BorderInfo binfo = this.borderInfo;
	    if(binfo != INVALID_BORDER_INFO) {
	        return binfo;
	    }
	    binfo = super.getBorderInfo();
	    if(binfo == null || (binfo.topStyle == HtmlValues.BORDER_STYLE_NONE && binfo.bottomStyle == HtmlValues.BORDER_STYLE_NONE && binfo.leftStyle == HtmlValues.BORDER_STYLE_NONE && binfo.rightStyle == HtmlValues.BORDER_STYLE_NONE)) {
	        if(binfo == null) {
	            binfo = new BorderInfo();
	        }
	        HTMLElementImpl element = this.element;
	        if(element != null) {
	            String border = element.getAttribute("border");
	            if(border != null) {
	                border = border.trim();
	                int value;
	                int valueType;
	                if(border.endsWith("%")) {
	                    valueType = HtmlInsets.TYPE_PERCENT;
	                    try {
	                        value = Integer.parseInt(border.substring(0, border.length()-1));
	                    } catch(NumberFormatException nfe) {
	                        value = 0;
	                    }
	                }
	                else {
	                    valueType = HtmlInsets.TYPE_PIXELS;
	                    try {
	                        value = Integer.parseInt(border);
	                    } catch(NumberFormatException nfe) {
	                        value = 0;
	                    }
	                }
	                HtmlInsets borderInsets = new HtmlInsets();
	                borderInsets.top = borderInsets.left = borderInsets.right = borderInsets.bottom = value;
	                borderInsets.topType = borderInsets.leftType = borderInsets.rightType = borderInsets.bottomType = valueType;
	                binfo.insets = borderInsets;
	                if(binfo.topColor == null) {
	                    binfo.topColor = Color.BLACK;
	                }
	                if(binfo.leftColor == null) {
	                    binfo.leftColor = Color.BLACK;
	                }
	                if(binfo.rightColor == null) {
	                    binfo.rightColor = Color.BLACK;
	                }
	                if(binfo.bottomColor == null) {
	                    binfo.bottomColor = Color.BLACK;
	                }
	                if(value != 0) {
	                    binfo.topStyle = binfo.leftStyle = binfo.rightStyle = binfo.bottomStyle = HtmlValues.BORDER_STYLE_SOLID;
	                }
	            }
	        } 
	    }
	    this.borderInfo = binfo;
	    return binfo;
	}   
}
