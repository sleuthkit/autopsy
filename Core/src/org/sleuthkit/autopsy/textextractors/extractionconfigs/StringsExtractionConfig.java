/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.textextractors.extractionconfigs;

import java.util.List;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;

/**
 *
 * @author dsmyda
 */
public class StringsExtractionConfig {
    private boolean extractUTF8;
    private boolean extractUTF16;
    private List<SCRIPT> extractScripts;

    public void setExtractUTF8(boolean enabled) {
        this.extractUTF8 = enabled;
    }

    public void setExtractUTF16(boolean enabled) {
        this.extractUTF16 = enabled;
    }

    public boolean getExtractUTF8() {
        return extractUTF8;
    }

    public boolean getExtractUTF16() { 
        return extractUTF16;
    }
    
    public void setExtractScripts(List<SCRIPT> scripts) {
        this.extractScripts = scripts;
    }
    
    public List<SCRIPT> getExtractScripts() {
        return this.extractScripts;
    }
}
