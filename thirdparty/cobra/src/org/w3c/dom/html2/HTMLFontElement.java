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
 * Copyright (c) 2003 World Wide Web Consortium,
 * (Massachusetts Institute of Technology, Institut National de
 * Recherche en Informatique et en Automatique, Keio University). All
 * Rights Reserved. This program is distributed under the W3C's Software
 * Intellectual Property License. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.
 * See W3C License http://www.w3.org/Consortium/Legal/ for more details.
 */

package org.w3c.dom.html2;

/**
 * Local change to font. See the FONT element definition in HTML 4.01. This 
 * element is deprecated in HTML 4.01.
 * <p>See also the <a href='http://www.w3.org/TR/2003/REC-DOM-Level-2-HTML-20030109'>Document Object Model (DOM) Level 2 HTML Specification</a>.
 */
public interface HTMLFontElement extends HTMLElement {
    /**
     * Font color. See the color attribute definition in HTML 4.01. This 
     * attribute is deprecated in HTML 4.01.
     */
    public String getColor();
    /**
     * Font color. See the color attribute definition in HTML 4.01. This 
     * attribute is deprecated in HTML 4.01.
     */
    public void setColor(String color);

    /**
     * Font face identifier. See the face attribute definition in HTML 4.01. 
     * This attribute is deprecated in HTML 4.01.
     */
    public String getFace();
    /**
     * Font face identifier. See the face attribute definition in HTML 4.01. 
     * This attribute is deprecated in HTML 4.01.
     */
    public void setFace(String face);

    /**
     * Font size. See the size attribute definition in HTML 4.01. This 
     * attribute is deprecated in HTML 4.01.
     */
    public String getSize();
    /**
     * Font size. See the size attribute definition in HTML 4.01. This 
     * attribute is deprecated in HTML 4.01.
     */
    public void setSize(String size);

}
