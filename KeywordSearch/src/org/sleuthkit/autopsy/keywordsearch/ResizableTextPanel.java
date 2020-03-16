/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

/**
 * This interface allows for retrieving current text size and setting the new text size
 * for a panel.
 */
interface ResizableTextPanel {

    /**
     * Retrieves the font size (in px).
     * @return  the font size (in px).
     */
    int getTextSize();

    /**
     * Sets the font size (in px).
     * @param newSize   the new font size (in px).
     */
    void setTextSize(int newSize);
    
}
