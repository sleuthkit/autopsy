/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.io.IOException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Interface for XRY file parsing.
 */
interface XRYFileParser {
    
    /**
     * Parses XRY entities and creates artifacts from the interpreted content.
     *
     * See XRYFileReader for more information on XRY entities. It is expected
     * that implementations will create artifacts on the supplied Content
     * object.
     *
     * @param reader Produces XRY entities from a given XRY file.
     * @param parent Content object that will act as the source of the
     * artifacts.
     * @throws IOException If an I/O error occurs during reading.
     * @throws TskCoreException If an error occurs during artifact creation.
     */
    void parse(XRYFileReader reader, Content parent) throws IOException, TskCoreException;
    
}
    
