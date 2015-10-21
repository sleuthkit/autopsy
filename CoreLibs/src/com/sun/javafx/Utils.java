/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package com.sun.javafx;

/**
 * This class takes the place of the one in Java8 versions prior to u60. As of
 * u60 com.sun.javafx.Utils was moved to the com.sun.javafx.util package and
 * code, specifically ControlsFX, that depended on it broke. ControlsFX has
 * removed their dependency on this class, but their fix will not be released
 * until version 8.60.10 of ControlsFX. Until then, this shim class allows
 * version 8.40.9 to run on Java 8u60. This class (and package) should be
 * removed once we upgrade to ControlsFX 8.60.x.
 */
public class Utils extends com.sun.javafx.util.Utils {

    //Does nothing but expose com.sun.javafx.utila.Utils in the old package (com.sun.javafx.Utils)
}
