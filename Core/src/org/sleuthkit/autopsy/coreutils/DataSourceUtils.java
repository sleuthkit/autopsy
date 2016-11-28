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
package org.sleuthkit.autopsy.coreutils;

import org.sleuthkit.datamodel.SleuthkitJNI;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility methods for working with data sources.
 */
public class DataSourceUtils {

    /**
     * Calls TSK to determine whether a
     * potential data source has a file system.
     *
     * @param dataSourcePath      The path to the data source.
     *
     * @return True or false.
     *
     * @throws IOException if an error occurs while trying to determine if the
     *                     data source has a file system.
     */
    public static boolean imageHasFileSystem(Path dataSourcePath) throws IOException {
        return SleuthkitJNI.isImageSupported(dataSourcePath.toString());
    }
}
