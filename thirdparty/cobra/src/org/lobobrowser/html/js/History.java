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
package org.lobobrowser.html.js;

import org.lobobrowser.html.*;
import org.lobobrowser.js.AbstractScriptableDelegate;

public class History  extends AbstractScriptableDelegate {
    private final Window window;
    
    History(Window window) {
        this.window = window;
    }
    
    public String getCurrent() {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        return ctx != null ? ctx.getCurrentURL() : null;
    }
    
    public String getNext() {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        return ctx != null ? ctx.getNextURL() : null;        
    }
        
    public String getPrevious() {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        return ctx != null ? ctx.getPreviousURL() : null;        
    }    
    
    public int getLength() {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        return ctx != null ? ctx.getHistoryLength() : 0;                
    }
    
    public void back() {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        if(ctx != null) {
            ctx.back();
        }        
    }
    
    public void forward() {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        if(ctx != null) {
            ctx.forward();
        }                
    }
    
    public void go(int offset) {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        if(ctx != null) {
            ctx.moveInHistory(offset);
        }                        
    }
    
    public void go(String url) {
        HtmlRendererContext ctx = this.window.getHtmlRendererContext();
        if(ctx != null) {
            ctx.goToHistoryURL(url);
        }                        
    }
}   
