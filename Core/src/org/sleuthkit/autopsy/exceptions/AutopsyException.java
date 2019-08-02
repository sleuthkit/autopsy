/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.exceptions;

public abstract class AutopsyException extends Exception {

    private static final long serialVersionUID = 1L;

    private String userMessage = "";

    /**
     * Constructs an exception to be thrown by a autopsy without a user message.
     *
     * @param message Exception message.
     */
    public AutopsyException(String message) {
        super(message);
    }

    /**
     * Constructs an exception to be thrown by a autopsy with a user exception.
     *
     * @param message     Exception message.
     * @param userMessage the user friendly message to include in this exception
     */
    public AutopsyException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    /**
     * Constructs an exception to be thrown by a autopsy without a user message.
     *
     * @param message Exception message.
     * @param cause   Exception cause.
     */
    public AutopsyException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an exception to be thrown by a autopsy with a user exception.
     *
     * @param message     Exception message.
     * @param userMessage the user friendly message to include in this exception
     * @param cause       Exception cause.
     */
    public AutopsyException(String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
    }

    /**
     * Get the user friendly message if one exists.
     *
     * @return the user friendly message if one exists, otherwise an empty String
     */
    public String getUserMessage() {
        return userMessage;
    }

}
