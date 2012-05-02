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

public static String insertPeriodically(
    String text, String insert, int period)
{
    StringBuilder builder = new StringBuilder(
         text.length() + insert.length() * (text.length()/period)+1);

    int index = 0;
    String prefix = "";
    while (index < text.length())
    {
        // Don't put the insert in the very first iteration.
        // This is easier than appending it *after* each substring
        builder.append(prefix);
        prefix = insert;
        builder.append(text.substring(index, 
            Math.min(index + period, text.length())));
        index += period;
    }
    return builder.toString();
}
}