/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.corecomponents;

/**
 *
 */
public interface OptionsPanel {

    /**
     * Store the current state of all options in this OptionsPanel.
     */
    public void store();

    /**
     * Load the saved state of all options, and refresh this OptionsPanel
     * accordingly.
     */
    public void load();

}
