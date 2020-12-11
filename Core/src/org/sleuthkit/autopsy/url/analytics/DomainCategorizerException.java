/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.url.analytics;

/**
 *An exception thrown by a DomainCategorizer.
 */
public class DomainCategorizerException extends Exception {

    /**
     * Constructor for the exception.
     * @param message The message for the exception.
     */
    public DomainCategorizerException(String message) {
        super(message);
    }

    /**
     * Constructor for the exception.
     * @param message The message for the exception.
     * @param inner The inner exception.
     */
    public DomainCategorizerException(String message, Throwable inner) {
        super(message, inner);
    }
}
