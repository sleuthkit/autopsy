/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textreaders;

import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.textreaders.TextExtractor.ExtractionException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Report;

/**
 * Factory for creating Readers given a Content instance
 *
 * See {@link org.sleuthkit.autopsy.textreaders.textreaderconfigs} for available
 * Reader configuration options.
 *
 * @see org.openide.util.Lookup
 */
public class TextReaders {

    /**
     * Returns a reader containing the Content text. Configuration files can be
     * added to the Lookup.
     *
     * See {@link org.sleuthkit.autopsy.textreaders.textreaderconfigs} for
     * available Reader configuration options.
     *
     * @param content Content source that will be read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return Reader containing file text
     *
     * @throws NoTextReaderFound Encountered when there is no Reader found for
     *                           the given content type or there was an error
     *                           while creating the reader.
     *
     * @see org.openide.util.Lookup
     */
    public static Reader getReader(Content content,
            Lookup context) throws NoTextReaderFound {
        try {
            if (content instanceof AbstractFile) {
                String mimeType = ((AbstractFile) content).getMIMEType();
                List<TextExtractor> extractors = Arrays.asList(
                        new HtmlTextExtractor(content),
                        new SqliteTextExtractor(content),
                        new TikaTextExtractor(content));
                for (TextExtractor extractor : extractors) {
                    extractor.setExtractionSettings(context);
                    if (extractor.isEnabled() && extractor.isSupported(content, mimeType)) {
                        return extractor.getReader();
                    }
                }
            } else if (content instanceof BlackboardArtifact) {
                TextExtractor artifactExtractor = new ArtifactTextExtractor((BlackboardArtifact) content);
                artifactExtractor.setExtractionSettings(context);
                return artifactExtractor.getReader();
            } else if (content instanceof Report) {
                TextExtractor reportExtractor = new TikaTextExtractor(content);
                reportExtractor.setExtractionSettings(context);
                return reportExtractor.getReader();
            }
        } catch (ExtractionException ex) {
            throw new NoTextReaderFound("Error while getting reader", ex);
        }

        throw new NoTextReaderFound(
                String.format("Could not find a suitable reader for "
                        + "content with name [%s] and id=[%d]. Try using "
                        + "the default reader instead.",
                        content.getName(), content.getId())
        );
    }

    /**
     * Returns a reader containing the Content text.
     *
     * @param content Content instance that will be read from
     *
     * @return Reader containing file text
     *
     * @throws NoTextReaderFound Encountered when there is no Reader was found
     *                           for the given content type. Use
     *                           getStringsReader(Content,Lookup) method
     *                           instead.
     */
    public static Reader getReader(Content content)
            throws NoTextReaderFound {
        return TextReaders.getReader(content, null);
    }

    /**
     * Returns a Reader containing the Content strings. This method supports all
     * content types. This method should be used as a backup in the event that
     * no reader was found using getReader(Content) or getReader(Content,
     * Lookup).
     *
     * Configure this reader with the StringsConfig in
     * {@link org.sleuthkit.autopsy.textreaders.textreaderconfigs}
     *
     * @param content Content source to read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return Reader containing file text
     *
     * @see org.openide.util.Lookup
     */
    public static Reader getStringsReader(Content content, Lookup context) {
        StringsTextExtractor stringsInstance = new StringsTextExtractor(content);
        stringsInstance.setExtractionSettings(context);
        return stringsInstance.getReader();
    }

    /**
     * System level exception for handling content types that have no specific
     * strategy defined for extracting their text.
     */
    public static class NoTextReaderFound extends Exception {

        public NoTextReaderFound(String msg) {
            super(msg);
        }

        public NoTextReaderFound(Throwable ex) {
            super(ex);
        }

        private NoTextReaderFound(String msg, Throwable ex) {
            super(msg, ex);
        }
    }
}
