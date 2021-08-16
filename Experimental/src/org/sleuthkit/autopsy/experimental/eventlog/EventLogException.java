/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.eventlog;

/**
 * Exception thrown if there is a problem in the EventLogManager initialization.
 */
public class EventLogException extends Exception {
    
    /**
     * Main constructor.
     * @param message The message.
     */
    EventLogException(String message) {
        super(message);
    }

    /**
     * Constructor.
     * @param message The message.
     * @param exception The inner exception.
     */
    EventLogException(String message, Throwable exception) {
        super(message, exception);
    }
    
}
