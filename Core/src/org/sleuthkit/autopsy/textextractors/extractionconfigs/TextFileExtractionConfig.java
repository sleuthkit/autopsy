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
public class TextFileExtractionConfig {
    private int minConfidenceInCharsetDetection;
        
    public void setMinConfidenceInCharsetDetection(int conf) {
        this.minConfidenceInCharsetDetection = conf;
    }

    public int getMinConfidenceInCharsetDetection() {
        return this.minConfidenceInCharsetDetection;
    }
}
