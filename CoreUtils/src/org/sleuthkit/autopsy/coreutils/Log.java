/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.coreutils;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class to slightly simplify getting the logger for a class, and other
 * common log tasks.
 */
public class Log {
    static public void noteAction(Class actionClass) {
        get(actionClass).log(Level.INFO, "Action performed: {0}", actionClass.getName());
    }


    static public Logger get(Class clazz) {
        return Logger.getLogger(clazz.getName());
    }
    
    static public Logger get(String loggerName) {
        return Logger.getLogger(loggerName);
    }
}
