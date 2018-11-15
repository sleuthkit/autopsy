/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.textextractors.extractionconfigs;

/**
 *
 * @author dsmyda
 */
public class ImageFileExtractionConfig {
    private boolean OCREnabled;
        
    public void setOCREnabled(boolean enabled) {
        this.OCREnabled = enabled;
    }

    public boolean getOCREnabled() {
        return this.OCREnabled;
    }
}
