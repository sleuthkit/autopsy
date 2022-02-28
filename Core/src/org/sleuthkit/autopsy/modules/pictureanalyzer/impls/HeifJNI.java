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
 * Interop with libheif dll's.
 */
public class HeifJNI {

//    static {
//        System.load("C:\\Users\\gregd\\Documents\\Source\\heif_convert_test\\HeifConvertTestJNI\\dist\\Release\\libx265.dll");
//        System.load("C:\\Users\\gregd\\Documents\\Source\\heif_convert_test\\HeifConvertTestJNI\\dist\\Release\\libde265.dll");
//        System.load("C:\\Users\\gregd\\Documents\\Source\\heif_convert_test\\HeifConvertTestJNI\\dist\\Release\\heif.dll");
//        System.load("C:\\Users\\gregd\\Documents\\Source\\heif_convert_test\\HeifConvertTestJNI\\dist\\Release\\jpeg62.dll");
//        System.load("C:\\Users\\gregd\\Documents\\Source\\heif_convert_test\\HeifConvertTestJNI\\dist\\Release\\heif-convert.dll");
//
//    }

    public native int convertToDisk(byte[] data, String jpgOutputPath);
}
