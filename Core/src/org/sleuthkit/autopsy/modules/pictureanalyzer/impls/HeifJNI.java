/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.pictureanalyzer.impls;

/**
 *
 * Interop with libheif native dependencies.
 */
public class HeifJNI {

    private static HeifJNI instance = null;
    
    /**
     * @return The singleton instance of this class.
     * @throws UnsatisfiedLinkError 
     */
    public static HeifJNI getInstance() throws UnsatisfiedLinkError {
        if (instance == null) {
            System.loadLibrary("vcruntime140_1");
            System.loadLibrary("libx265");
            System.loadLibrary("libde265");
            System.loadLibrary("heif");
            System.loadLibrary("jpeg62");
            System.loadLibrary("heifconvert");
            instance = new HeifJNI();
        }
        return instance;
    }
    
    private HeifJNI() {}

    /**
     * Native method found in heif_convert.dll in third party libheif.  Converts a heic/heif file to one or many jpegs.
     * @param data The heic/heif binary data.
     * @param jpgOutputPath The jpeg output file.  A new file name will be calculated if multiple jpegs are generated.
     * @return The dll return code.
     */
    public native int convertToDisk(byte[] data, String jpgOutputPath);
}
