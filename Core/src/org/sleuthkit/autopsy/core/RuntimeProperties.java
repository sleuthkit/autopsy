/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2015 Basis Technology Corp.
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
     * Gets a flag indicating whether or not the application is running with a
     * GUI.
     *
     * @return True or false.
     */
    public synchronized static boolean runningWithGUI() {
        return runningWithGUI;
    }

    /**
     * Private constructor to prevent creation of instances of this class.
     */
    private RuntimeProperties() {

    }

    private final static class RuntimePropertiesException extends Exception {

        private static final long serialVersionUID = 1L;

        private RuntimePropertiesException(String message) {
            super(message);
        }

        private RuntimePropertiesException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
