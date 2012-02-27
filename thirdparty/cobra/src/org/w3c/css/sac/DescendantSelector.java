/*
 * (c) COPYRIGHT 1999 World Wide Web Consortium
 * (Massachusetts Institute of Technology, Institut National de Recherche
 *  en Informatique et en Automatique, Keio University).
 * All Rights Reserved. http://www.w3.org/Consortium/Legal/
 *
 * $Id: DescendantSelector.java,v 1.4 2008/01/03 13:35:38 xamjadmin Exp $
 */
package org.w3c.css.sac;

/**
 * @version $Revision: 1.4 $
 * @author  Philippe Le Hegaret
 * @see Selector#SAC_DESCENDANT_SELECTOR
 * @see Selector#SAC_CHILD_SELECTOR
 */
public interface DescendantSelector extends Selector {
    
    /**
     * Returns the parent selector.
     */    
    public Selector getAncestorSelector();

    /*
     * Returns the simple selector.
     */    
    public SimpleSelector getSimpleSelector();
}
