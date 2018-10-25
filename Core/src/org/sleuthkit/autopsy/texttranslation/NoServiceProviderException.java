/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.texttranslation;

/**
 * Exception to indicate that no Service Provider could be found during the
 * Lookup action.
 */
public class NoServiceProviderException extends Exception {

    public NoServiceProviderException(String msg) {
        super(msg);
    }
}