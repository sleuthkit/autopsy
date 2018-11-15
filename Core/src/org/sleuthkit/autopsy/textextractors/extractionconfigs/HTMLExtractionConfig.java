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
public class HTMLExtractionConfig {
    private int contentSizeLimit;
        
    public void setContentSizeLimit(int size) {
        this.contentSizeLimit = size;
    }

    public int getContentSizeLimit() {
        return this.contentSizeLimit;
    }
}
