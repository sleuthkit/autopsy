/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.xryparser;

import java.nio.file.Path;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author dsmyda
 */
public class XRYReportFile {
    
    //It is assumed XRY files have a txt extension.
    private static final String EXTENSION = "txt";
    
    public static boolean isXRYReportFile(Path file) {
        String parsedExtension = FilenameUtils.getExtension(file.toString());
        if(!EXTENSION.equals(parsedExtension)) {
            return false;
        }
        
        
        
        return true;
    }
}
