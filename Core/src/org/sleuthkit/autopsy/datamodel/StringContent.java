/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

/**
 * An object that has an associated string. These objects will be looked up by a
 * strings viewer to show the associated string.
 *
 * @author alawrence
 */
public interface StringContent {
    /*
     * Get the string associated with this object
     */

    public String getString();
}
