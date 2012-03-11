/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

/**
 *
 * @author Alex
 */
public class reportUtils {
    
static String changeExtension(String originalName, String newExtension) {
    int lastDot = originalName.lastIndexOf(".");
    if (lastDot != -1) {
        return originalName.substring(0, lastDot) + newExtension;
    } else {
        return originalName + newExtension;
    }
}
}