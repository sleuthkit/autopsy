/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.yara;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * native JNI interface to the yarabridge dll.
 */
public class YaraJNIWrapper {

    // Load the yarabridge.dll which should be located in the same directory as
    // the jar file. If we need to use this code for debugging the dll this
    // code will need to be modified to add that support.
    static {
        Path directoryPath = null;
        try {
            directoryPath = Paths.get(YaraJNIWrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().toAbsolutePath();
        } catch (URISyntaxException ex) {
            Logger.getLogger(YaraJNIWrapper.class.getName()).log(Level.SEVERE, null, ex);
        }
        String libraryPath = Paths.get(directoryPath != null ? directoryPath.toString() : "", "yarabridge.dll").toAbsolutePath().toString();
        System.load(libraryPath);
    }

    /**
     * Returns a list of rules that were found in the given byteBuffer.
     *
     * The rule path must be to a yara compile rule file.
     *
     * @param compiledRulesPath
     * @param byteBuffer
     *
     * @return List of rules found rules. Null maybe returned if error occurred.
     *
     * @throws YaraWrapperException
     */
    static public native List<String> FindRuleMatch(String compiledRulesPath, byte[] byteBuffer) throws YaraWrapperException;

    /**
     * private constructor.
     */
    private YaraJNIWrapper() {
    }
    
}
