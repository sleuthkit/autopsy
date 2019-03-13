/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;

class CorrelationCaseWrapper {

    private final CorrelationCase corCase;
    private final String message;

    CorrelationCaseWrapper(CorrelationCase corrCase) {
        corCase = corrCase;
        message = corrCase.getDisplayName();
    }

    CorrelationCaseWrapper(String msg) {
        corCase = null;
        message = msg;
    }

    String getMessage() {
        return message;
    }
}
