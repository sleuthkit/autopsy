/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.HashEntry;

interface HashSetParser {

    /**
     * Get the next hash to import
     *
     * @return The hash as a string, or null if the end of file was reached
     *         without error
     *
     * @throws TskCoreException
     */
    String getNextHash() throws TskCoreException;

    /**
     * Check if there are more hashes to read
     *
     * @return true if we've read all expected hash values, false otherwise
     */
    boolean doneReading();

    /**
     * Get the expected number of hashes in the file. This number can be an
     * estimate.
     *
     * @return The expected hash count
     */
    long getExpectedHashCount();

    /**
     * Closes the import file
     */
    void close();

    /**
     * Get the next hash to import as a HashEntry object.
     *
     * @return A new hash entry for the next item parsed.
     *
     * @throws TskCoreException
     */
    default HashEntry getNextHashEntry() throws TskCoreException {
        String next = getNextHash();
        if (next == null) {
            return null;
        }

        return new HashEntry(null, next, null, null, null);
    }
}
