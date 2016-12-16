/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.InputStream;
import java.io.Reader;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;

/**
 * Extracts text out of a SleuthkitVisitableItem, and exposes it is a Reader.
 * This Reader is given to the Ingester to chunk and index in Solr.
 *
 * @param <TextSource> The subtype of SleuthkitVisitableItem an implementation
 *                     is able to process.
 */
abstract class TextExtractor< TextSource extends SleuthkitVisitableItem> {

    static final private Logger logger = Logger.getLogger(TextExtractor.class.getName());

    /**
     * Is this extractor configured such that no extraction will/should be done?
     *
     * @return True if this extractor will/should not perform any extraction.
     */
    abstract boolean isDisabled();

    /**
     * Log the given message and exception as a warning.
     *
     * @param msg
     * @param ex
     */
    void logWarning(String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex); //NON-NLS  }
    }

    /**
     * Get an input stream over the content of the given source.
     *
     * @param source
     *
     * @return
     */
    abstract InputStream getInputStream(TextSource source);

    /**
     * Get a reader that over the text extracted from the given source.
     *
     * @param stream
     * @param source
     *
     * @return
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    abstract Reader getReader(InputStream stream, TextSource source) throws Ingester.IngesterException;

    /**
     * Get the 'object' id of the given source.
     *
     * @param source
     *
     * @return
     */
    abstract long getID(TextSource source);

    /**
     * Get a human readable name for the given source.
     *
     * @param source
     *
     * @return
     */
    abstract String getName(TextSource source);
}
