/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.commandlineingest;

import org.sleuthkit.autopsy.progress.LoggingProgressIndicator;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * Wrap a LoggingProgressIndicator to support both logging to the log file
 * and the command line.
 */
public class CommandLineProgressIndicator implements ProgressIndicator {

    private final LoggingProgressIndicator loggingIndicator = new LoggingProgressIndicator();
    
    @Override
    public void start(String message, int totalWorkUnits) {
        loggingIndicator.start(message, totalWorkUnits);
        System.out.println(message);
    }

    @Override
    public void start(String message) {
        loggingIndicator.start(message);
        System.out.println(message);
    }

    @Override
    public void switchToIndeterminate(String message) {
        loggingIndicator.switchToIndeterminate(message);
    }

    @Override
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
       loggingIndicator.switchToDeterminate(message, workUnitsCompleted, totalWorkUnits);
    }

    @Override
    public void progress(String message) {
        loggingIndicator.progress(message);
        System.out.println(message);
    }

    @Override
    public void progress(int workUnitsCompleted) {
        loggingIndicator.progress(workUnitsCompleted);
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
        loggingIndicator.progress(message);
        System.out.println(message);
    }

    @Override
    public void finish() {
       loggingIndicator.finish();
    }
    
}
