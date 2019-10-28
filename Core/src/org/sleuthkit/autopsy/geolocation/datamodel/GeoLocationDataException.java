/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.geolocation.datamodel;

/**
 *
 * 
 */
public class GeoLocationDataException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor when error message is not available
     */
    public GeoLocationDataException() {
        super("No error message available.");
    }

    /**
     * Create exception containing the error message
     *
     * @param msg the message
     */
    public GeoLocationDataException(String msg) {
        super(msg);
    }

    /**
     * Create exception containing the error message and cause exception
     *
     * @param msg the message
     * @param ex  cause exception
     */
    public GeoLocationDataException(String msg, Exception ex) {
        super(msg, ex);
    }
}
