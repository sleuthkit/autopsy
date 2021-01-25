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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * native JNI interface to the yarabridge dll.
 */
public class YaraJNIWrapper {

    static {
        try {
            extractAndLoadDll();
        } catch (IOException | YaraWrapperException ex) {
            Logger.getLogger(YaraJNIWrapper.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Returns a list of rules that were found in the given byteBuffer.
     *
     * The rule path must be to a yara compile rule file.
     *
     * @param compiledRulesPath Absolute path to a compiled YARA rule file.
     * @param byteBuffer        File buffer.
     * @param bufferSize        Size of the byte to read in the given buffer
     * @param timeoutSec        Scan timeout value in seconds.
     *
     * @return List of rules found rules. Null maybe returned if error occurred.
     *
     * @throws YaraWrapperException
     */
    static public native List<String> findRuleMatch(String compiledRulesPath, byte[] byteBuffer, int bufferSize, int timeoutSec) throws YaraWrapperException;

    /**
     * Returns a list of matching YARA rules found in the given file.
     *
     * @param compiledRulePath Absolute path to a compiled YARA rule file.
     * @param filePath         Absolute path to the file to search.
     * @param timeoutSec       Scan timeout value in seconds.
     *
     * @return List of rules found rules. Null maybe returned if error occurred.
     *
     *
     * @throws YaraWrapperException
     */
    static public native List<String> findRuleMatchFile(String compiledRulePath, String filePath, int timeoutSec) throws YaraWrapperException;

    /**
     * Copy yarabridge.dll from inside the jar to a temp file that can be loaded
     * with System.load.
     *
     * To make this work, the dll needs to be in the same folder as this source
     * file. The dll needs to be located somewhere in the jar class path.
     *
     * @throws IOException
     * @throws YaraWrapperException
     */
    static private void extractAndLoadDll() throws IOException, YaraWrapperException {
        File tempFile = File.createTempFile("lib", null);
        tempFile.deleteOnExit();
        try (InputStream in = YaraJNIWrapper.class.getResourceAsStream("yarabridge.dll")) {
            if (in == null) {
                throw new YaraWrapperException("native library was not found in jar file.");
            }
            try (OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int lengthRead;
                while ((lengthRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, lengthRead);
                    out.flush();
                }
            }
        }

        System.load(tempFile.getAbsolutePath());
    }

    /**
     * private constructor.
     */
    private YaraJNIWrapper() {
    }

}
