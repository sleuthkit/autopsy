/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.logicalimager.configuration;

import java.text.NumberFormat;
import java.text.ParseException;
import javax.swing.text.NumberFormatter;

/**
 * Number formatter which will reset to being a null value when an invalid value
 * is entered
 */
final class DefaultToEmptyNumberFormatter extends NumberFormatter {

    private static final long serialVersionUID = 1L;

    /**
     * Create a DefaultToEmptyNumberFormatter
     *
     * @param format the format for the numbers
     */
    DefaultToEmptyNumberFormatter(NumberFormat format) {
        super(format);
    }

    @Override
    public Object stringToValue(String string)
            throws ParseException {
        Object returnValue = null;
        try {
            returnValue = super.stringToValue(string);
        } catch (ParseException ignored) {
            //reset value to being empty since invalid value was entered
        }
        return returnValue;
    }
}
