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

    private static boolean coreComponentsActive = true;
    private static boolean coreComponentsActiveSet = false;

    /**
     * Sets or unsets a flag indicating whether or not the core Autopsy UI
     * components and user interactions with those components via menus, message
     * boxes, NetBeans progress handles, etc., are enabled.
     * <p>
     * This flag exists as a mechanism to allow use of Autopsy as a platform
     * with the core Autopsy user interface disabled, until such time as the
     * user interface is made separable and optional.
     *
     * @param coreComponentsActive True or false.
     */
    public synchronized static void setCoreComponentsActive(boolean coreComponentsActive) {
        if (!coreComponentsActiveSet) {
            RuntimeProperties.coreComponentsActive = coreComponentsActive;
            coreComponentsActiveSet = true;
        }
    }

    /**
     * Gets a flag indicating whether or not the core Autopsy UI components and
     * user interactions with those components via menus, message boxes,
     * NetBeans progress handles, etc., are enabled.
     * <p>
     * This flag exists as a mechanism to allow use of Autopsy as a platform
     * with the core Autopsy user interface disabled, until such time as the
     * user interface is made separable and optional.
     *
     * @return True or false.
     */
    public synchronized static boolean coreComponentsAreActive() {
        return coreComponentsActive;
    }
    
    /**
     * Private constructor to prevent creation of instances of this class.
     */
    private RuntimeProperties() {
        
    }
}
