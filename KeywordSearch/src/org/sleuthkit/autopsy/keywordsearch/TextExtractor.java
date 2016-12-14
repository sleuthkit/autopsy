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

abstract class TextExtractor<AppendixProvider, TextSource extends SleuthkitVisitableItem> {

    static final private Logger logger = Logger.getLogger(TextExtractor.class.getName());
    abstract boolean noExtractionOptionsAreEnabled();

    void logWarning(String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex); //NON-NLS  }
    }

    void appendDataToFinalChunk(StringBuilder sb, AppendixProvider dataProvider) {
        //no-op
    }

    abstract AppendixProvider newAppendixProvider();

    abstract InputStream getInputStream(TextSource source);

    abstract Reader getReader(InputStream stream, TextSource source, AppendixProvider appendix) throws Ingester.IngesterException;

    abstract long getID(TextSource source);

    abstract String getName(TextSource source);
}
