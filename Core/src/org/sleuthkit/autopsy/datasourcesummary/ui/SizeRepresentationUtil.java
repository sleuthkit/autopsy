/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import org.openide.util.NbBundle;

/**
 *
 * @author gregd
 */
public class SizeRepresentationUtil {

    private static final int SIZE_CONVERSION_CONSTANT = 1000;
    private static final DecimalFormat APPROXIMATE_SIZE_FORMAT = new DecimalFormat("#.##");

    private static List<String> UNITS = Arrays.asList(
            Bundle.SizeRepresentationUtil_units_bytes(),
            Bundle.SizeRepresentationUtil_units_kilobytes(),
            Bundle.SizeRepresentationUtil_units_megabytes(),
            Bundle.SizeRepresentationUtil_units_gigabytes(),
            Bundle.SizeRepresentationUtil_units_terabytes(),
            Bundle.SizeRepresentationUtil_units_petabytes()
    );

    /**
     * Get a long size in bytes as a string formated to be read by users.
     *
     * @param size Long value representing a size in bytes
     *
     * @return return a string formated with a user friendly version of the size
     *         as a string, returns empty String when provided empty size
     */
    public static String getSizeString(Long size) {
        return getSizeString(size, APPROXIMATE_SIZE_FORMAT, true);
    }
    
    
    @NbBundle.Messages({
        "SizeRepresentationUtil_units_bytes= bytes",
        "SizeRepresentationUtil_units_kilobytes= kB",
        "SizeRepresentationUtil_units_megabytes= MB",
        "SizeRepresentationUtil_units_gigabytes= GB",
        "SizeRepresentationUtil_units_terabytes= TB",
        "SizeRepresentationUtil_units_petabytes= PB"
    })
    public static String getSizeString(Long size, DecimalFormat format, boolean showFullSize) {
        if (size == null) {
            return "";
        }
        double approximateSize = size;
        int unitsIndex = 0;
        for (; unitsIndex < UNITS.size(); unitsIndex++) {
            if (approximateSize < SIZE_CONVERSION_CONSTANT) {
                break;
            } else {
                approximateSize /= SIZE_CONVERSION_CONSTANT;
            }
        }
        
        String fullSize = String.valueOf(size) + UNITS.get(0);
        String closestUnitSize = format.format(approximateSize) + UNITS.get(unitsIndex);
        
        if (unitsIndex == 0) {
            return fullSize;
        } else if (showFullSize) {
            return String.format("%s (%s)", closestUnitSize, fullSize);
        } else {
            return closestUnitSize;
        }
    }
}
