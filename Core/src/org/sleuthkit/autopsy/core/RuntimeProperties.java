/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2022 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.core;

/**
 * Application properties that are set once at runtime and are not saved between
 * invocations of Autopsy.
 */
public class RuntimeProperties {

    private static boolean runningWithGUI = true;
    private static boolean runningWithGUIFlagHasBeenSet = false;
    private static boolean runningInTarget = false;
    private static boolean runningInTargetFlagHasBeenSet = false;

    /**
     * Sets or unsets a flag indicating whether or not the application is
     * running with a GUI. The flag can only be set once per application
     * innvocation.
     *
     * @param runningWithGUI True or false.
     *
     * @throws RuntimePropertiesException if the flag has already been set.
     */
    public synchronized static void setRunningWithGUI(boolean runningWithGUI) throws RuntimePropertiesException {
        if (!runningWithGUIFlagHasBeenSet) {
            RuntimeProperties.runningWithGUI = runningWithGUI;
            runningWithGUIFlagHasBeenSet = true;
        } else {
            throw new RuntimePropertiesException("The runningWithGUI flag has already been set and cannot be changed");
        }
    }
    
    /**
     * Sets or unsets a flag indicating whether or not the application is running in a target system.
     * The flag can only be set once per application invocation
     * 
     * @param runningInTarget
     * 
     * @throws RuntimePropertiesException if the flag has already been set 
     */
    
    public synchronized static void setRunningInTarget(boolean runningInTarget) throws RuntimePropertiesException{
        if(!runningInTargetFlagHasBeenSet){
            RuntimeProperties.runningInTarget = runningInTarget;
            runningInTargetFlagHasBeenSet = true;
        } else {
            throw new RuntimePropertiesException("The runningLive Flag has already been set and cannot be changed");
        }
    }
    
    /**
     * Gets a flag indicating whether or not the application is running in a target system
     * 
     * @return True or false.
     */
    public synchronized static boolean isRunningInTarget() {
        return runningInTarget;
    }

    /**
     * Gets a flag indicating whether or not the application is running with a
     * GUI. In addition to the Autopsy flag setting, it also checks whether the  
     * AUTOPSY_HEADLESS environment variable is set. The environment variable is set 
     * by some of the projects built on top of Autopsy platform. This is necessary 
     * because sometimes this method is called from Installer classes, i.e. before 
     * we have been able to determine whether we are running headless or not. 
     * See JIRA-8422.
     *
     * @return True or false.
     */
    public synchronized static boolean runningWithGUI() {
        if (System.getenv("AUTOPSY_HEADLESS") != null) {
            // Some projects built on top of Autopsy platform set this environment 
            // variable to make sure there are no UI popups
            return false;
        } else {
            return runningWithGUI;
        }
    }

    /**
     * Private constructor to prevent creation of instances of this class.
     */
    private RuntimeProperties() {
    }

    /**
     * Exception to throw if there is an error setting a runtime property.
     */
    public final static class RuntimePropertiesException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructor for an exception to throw if there is an error setting
         * a runtime property.
         *
         * @param message The exception message.
         */
        public RuntimePropertiesException(String message) {
            super(message);
        }

        /**
         * Constructor for an exception to throw if there is an error setting
         * a runtime property.
         *
         * @param message The exception message.
         * @param cause   The cause of the error.
         */
        public RuntimePropertiesException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
