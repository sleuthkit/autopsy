/*
 * Copyright (c) 2000 World Wide Web Consortium,
 * (Massachusetts Institute of Technology, Institut National de
 * Recherche en Informatique et en Automatique, Keio University). All
 * Rights Reserved. This program is distributed under the W3C's Software
 * Intellectual Property License. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.
 * See W3C License http://www.w3.org/Consortium/Legal/ for more details.
 */

package org.w3c.dom.css;

import org.w3c.dom.views.AbstractView;
import org.w3c.dom.Element;

/**
 *  This interface represents a CSS view. The <code>getComputedStyle</code> 
 * method provides a read only access to the computed values of an element. 
 * <p> The expectation is that an instance of the <code>ViewCSS</code> 
 * interface can be obtained by using binding-specific casting methods on an 
 * instance of the <code>AbstractView</code> interface. 
 * <p> Since a computed style is related to an <code>Element</code> node, if 
 * this element is removed from the document, the associated 
 * <code>CSSStyleDeclaration</code> and <code>CSSValue</code> related to 
 * this declaration are no longer valid. 
 * <p>See also the <a href='http://www.w3.org/TR/2000/REC-DOM-Level-2-Style-20001113'>Document Object Model (DOM) Level 2 Style Specification</a>.
 * @since DOM Level 2
 */
public interface ViewCSS extends AbstractView {
    /**
     *  This method is used to get the computed style as it is defined in . 
     * @param elt The element whose style is to be computed. This parameter 
     *   cannot be null. 
     * @param pseudoElt The pseudo-element or <code>null</code> if none. 
     * @return  The computed style. The <code>CSSStyleDeclaration</code> is 
     *   read-only and contains only absolute values. 
     */
    public CSSStyleDeclaration getComputedStyle(Element elt, 
                                                String pseudoElt);

}
