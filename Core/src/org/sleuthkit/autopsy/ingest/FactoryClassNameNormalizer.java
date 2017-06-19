/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
            String moduleClassName = canonicalClassName.replaceAll("[$\\d]", ""); //NON-NLS NON-NLS
            return moduleClassName;
        }
        return canonicalClassName;
    }

    /**
     * Determines if the moduleSettingsFilePath is that of a serialized jython
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
