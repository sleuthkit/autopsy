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
package org.sleuthkit.autopsy.python;

import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Used to strip Python IDs on factory class names.
 */
public class FactoryClassNameNormalizer {

    private static final CharSequence pythonModuleSettingsPrefixCS = "org.python.proxies.".subSequence(0, "org.python.proxies.".length() - 1); //NON-NLS
    private static final Logger logger = Logger.getLogger(FactoryClassNameNormalizer.class.getName());

    public static String normalize(String canonicalClassName) {
        if (isPythonClassName(canonicalClassName)) {
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
     * Determines if the classNameToVerify is that of a serialized Jython
     * instance. Serialized Jython instances (settings saved on the disk)
     * contain "org.python.proxies." in their fileName based on the current
     * implementation.
     *
     * @param classNameToVerify class name to verify.
     *
     * @return True or false
     */
    private static boolean isPythonClassName(String classNameToVerify) {
        return classNameToVerify.contains(pythonModuleSettingsPrefixCS);
    }

}
