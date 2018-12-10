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
package org.sleuthkit.autopsy.textextractors;

import java.util.Arrays;
import java.util.List;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Report;

/**
 * Factory for creating
 * {@link org.sleuthkit.autopsy.textextractors.TextExtractor}'s given a
 * {@link org.sleuthkit.datamodel.Content} instance
 *
 * See {@link org.sleuthkit.autopsy.textextractors.extractionconfigs} for
 * available {@link org.sleuthkit.autopsy.textextractors.TextExtractor}
 * configuration options.
 *
 * @see org.openide.util.Lookup
 */
public class TextExtractorFactory {

    /**
     * Auto detects the correct
     * {@link org.sleuthkit.autopsy.textextractors.TextExtractor} given the
     * {@link org.sleuthkit.datamodel.Content}.
     *
     * See {@link org.sleuthkit.autopsy.textextractors.extractionconfigs} for
     * available {@link org.sleuthkit.autopsy.textextractors.TextExtractor}
     * configuration options.
     *
     * @param content Content source that will be read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return A TextExtractor that supports the given content. File text can be
     *         obtained from {@link TextExtractor#getReader()}.
     *
     * @throws NoTextExtractorFound Encountered when there is no TextExtractor
     *                              was found for the given content type. Use {@link
     *                              TextExtractorFactory#getDefaultExtractor(org.sleuthkit.datamodel.Content,
     *                              org.openide.util.Lookup)}
     *
     * @see org.openide.util.Lookup
     */
    public static TextExtractor getExtractor(Content content,
            Lookup context) throws NoTextExtractorFound {
        if (content instanceof AbstractFile) {
            String mimeType = ((AbstractFile) content).getMIMEType();
            List<TextExtractor> extractors = Arrays.asList(
                    new HtmlTextExtractor(content),
                    new SqliteTextExtractor(content),
                    new TikaTextExtractor(content));
            for (TextExtractor extractor : extractors) {
                extractor.setExtractionSettings(context);
                if (extractor.isEnabled() && extractor.isSupported(content, mimeType)) {
                    return extractor;
                }
            }
        } else if (content instanceof BlackboardArtifact) {
            TextExtractor artifactExtractor = new ArtifactTextExtractor((BlackboardArtifact) content);
            artifactExtractor.setExtractionSettings(context);
            return artifactExtractor;
        } else if (content instanceof Report) {
            TextExtractor reportExtractor = new TikaTextExtractor(content);
            reportExtractor.setExtractionSettings(context);
            return reportExtractor;
        }

        throw new NoTextExtractorFound(
                String.format("Could not find a suitable extractor for "
                        + "content with name [%s] and id=[%d]. Try using the default, "
                        + "non content specific extractor as an alternative.",
                        content.getName(), content.getId())
        );
    }

    /**
     * Auto detects the correct
     * {@link org.sleuthkit.autopsy.textextractors.TextExtractor} given the
     * {@link org.sleuthkit.datamodel.Content}.
     *
     * @param content Content instance that will be read from
     *
     * @return A TextExtractor that supports the given content. File text can be
     *         obtained from {@link TextExtractor#getReader()}.
     *
     * @throws NoTextExtractorFound Encountered when there is no TextExtractor
     *                              was found for the given content type. Use {@link
     *                              TextExtractorFactory#getDefaultExtractor(org.sleuthkit.datamodel.Content,
     *                              org.openide.util.Lookup)}
     */
    public static TextExtractor getExtractor(Content content)
            throws NoTextExtractorFound {
        return getExtractor(content, null);
    }

    /**
     * Returns the default extractor that can be run on any content type. This
     * extractor should be used as a backup in the event that no extractor was
     * found using or {@link TextExtractorFactory#getDefaultExtractor(org.sleuthkit.datamodel.Content, org.openide.util.Lookup)}
     * {@link TextExtractorFactory#getExtractor(org.sleuthkit.datamodel.Content)}.
     *
     * @param content Content source to read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return A DefaultExtractor instance. File text can be obtained from
     *         {@link TextExtractor#getReader()}.
     *
     * @see org.openide.util.Lookup
     */
    public static TextExtractor getDefaultExtractor(Content content, Lookup context) {
        TextExtractor stringsInstance = new StringsTextExtractor(content);
        stringsInstance.setExtractionSettings(context);
        return stringsInstance;
    }

    /**
     * System level exception for handling content types that have no specific
     * strategy defined for extracting their text.
     *
     * @see
     * org.sleuthkit.autopsy.textextractors.TextExtractorFactory#getExtractor(org.sleuthkit.datamodel.Content)
     * @see
     * org.sleuthkit.autopsy.textextractors.TextExtractorFactory#getDefaultExtractor(org.sleuthkit.datamodel.Content,
     * org.openide.util.Lookup)}
     */
    public static class NoTextExtractorFound extends Exception {

        public NoTextExtractorFound(String msg) {
            super(msg);
        }

        public NoTextExtractorFound(Throwable ex) {
            super(ex);
        }
    }
}
