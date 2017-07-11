/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Used to strip Python IDs on factory class names.
 */
class FactoryClassNameNormalizer {

    private static final CharSequence pythonModuleSettingsPrefixCS = "org.python.proxies.".subSequence(0, "org.python.proxies.".length() - 1); //NON-NLS
    private static final Logger logger = Logger.getLogger(FactoryClassNameNormalizer.class.getName());

    static String normalize(String canonicalClassName) {
        if (isPythonModuleSettingsFile(canonicalClassName)) {
            // Compiled Python modules have variable instance number as a part
            // of their file name. This block of code gets rid of that variable
            // instance number and helps maitains constant module name over
            // multiple runs.
            String moduleClassName = canonicalClassName.replaceAll("\\$\\d*$", ""); //NON-NLS NON-NLS
            return moduleClassName;
        }
        return canonicalClassName;
    }

    /**
     * Determines if the moduleSettingsFilePath is that of a serialized Jython
     * instance. Serialized Jython instances (settings saved on the disk)
     * contain "org.python.proxies." in their fileName based on the current
     * implementation.
     *
     * @param moduleSettingsFilePath path to the module settings file.
     *
     * @return True or false
     */
    private static boolean isPythonModuleSettingsFile(String moduleSettingsFilePath) {
        return moduleSettingsFilePath.contains(pythonModuleSettingsPrefixCS);
    }

}
