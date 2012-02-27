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

import org.lobobrowser.html.domimpl.HTMLElementImpl;
import java.awt.*;

public class ListRenderState extends AbstractMarginRenderState {
    public ListRenderState(RenderState prevRenderState,
            HTMLElementImpl element) {
        super(prevRenderState, element);
    }

    protected HtmlInsets getDefaultMarginInsets() {
        HtmlInsets insets = new HtmlInsets();
        RenderState prevRS = this.getPreviousRenderState();
        FontMetrics fm = prevRS == null ? this.getFontMetrics() : prevRS.getFontMetrics();
        insets.top = fm.getHeight(); 
        insets.bottom = fm.getHeight();
        insets.topType = HtmlInsets.TYPE_PIXELS;
        insets.bottomType = HtmlInsets.TYPE_PIXELS;
        int dpi = GraphicsEnvironment.isHeadless() ? 72 : Toolkit
                .getDefaultToolkit().getScreenResolution();
        double leftInches = 30 / 72.0;
        insets.left = (int) Math.round(dpi * 30.0 / 72.0);
        insets.leftType = HtmlInsets.TYPE_PIXELS;
        return insets;
    }

    public int getDefaultDisplay() {
        return DISPLAY_LIST_ITEM;
    }
}
