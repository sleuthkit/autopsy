/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.healthmonitor;

/**
 *
 */
class HealthMonitorException extends Exception {
    private static final long serialVersionUID = 1L;

    HealthMonitorException(String message) {
        super(message);
    }

    HealthMonitorException(String message, Throwable cause) {
        super(message, cause);
    }   
}
