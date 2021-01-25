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
 * Factory for creating TextExtractors given a Content instance
 *
 * See {@link org.sleuthkit.autopsy.textextractors.configs} for
 * available extractor configuration options.
 *
 * @see org.openide.util.Lookup
 */
public class TextExtractorFactory {

    /**
     * Returns a TextExtractor containing the Content text. Configuration files
     * can be added to the Lookup.
     *
     * See {@link org.sleuthkit.autopsy.textextractors.configs} for
     * available extractor configuration options.
     *
     * @param content Content source that will be read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return TextExtractor containing file text
     *
     * @throws NoTextExtractorFound Encountered when there is no Reader found
     *                              for the given content type or there was an
     *                              error while creating the reader.
     *
     * @see org.openide.util.Lookup
     */
    public static TextExtractor getExtractor(Content content, Lookup context) throws NoTextExtractorFound {
        if (content instanceof AbstractFile) {
            for (TextExtractor extractor : getFileExtractors((AbstractFile) content, context)) {
                if (extractor.isSupported()) {
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
                String.format("Could not find a suitable reader for "
                        + "content with name [%s] and id=[%d].",
                        content.getName(), content.getId())
        );
    }

    /**
     * Initializes, orders, and returns all file extractors that can read
     * AbstractFile instances.
     *
     * @param content AbstractFile content
     * @param context Lookup containing extractor configurations
     *
     * @return List of all extractors in priority order. Not all will support the passed in content.   @@@ PERHAPS ONLY SUPPORTED SHOULD BE RETURNED
     */
    private static List<TextExtractor> getFileExtractors(AbstractFile content, Lookup context) {
        List<TextExtractor> fileExtractors = Arrays.asList(
                new TextFileExtractor(content),
                new HtmlTextExtractor(content),
                new SqliteTextExtractor(content),
                new TikaTextExtractor(content));   /// This should go last to ensure the more specific ones are picked first. 

        fileExtractors.forEach((fileExtractor) -> {
            fileExtractor.setExtractionSettings(context);
        });

        return fileExtractors;
    }

    /**
     * Returns a TextExtractor containing the Content text.
     *
     * @param content Content instance that will be read from
     *
     * @return TextExtractor containing file text
     *
     * @throws NoTextExtractorFound Encountered when there is no Reader was
     *                              found for the given content type. Use
     *                              getStringsExtractor(Content,Lookup) method
     *                              instead.
     */
    public static TextExtractor getExtractor(Content content) throws NoTextExtractorFound {
        return TextExtractorFactory.getExtractor(content, null);
    }

    /**
     * Returns a TextExtractor containing the Content strings. This method
     * supports all content types. This method should be used as a backup in the
     * event that no reader was found using getExtractor(Content) or
     * getExtractor(Content, Lookup).
     *
     * Configure this extractor with the StringsConfig in
     * {@link org.sleuthkit.autopsy.textextractors.configs}
     *
     * @param content Content source to read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return TextExtractor containing file text
     *
     * @see org.openide.util.Lookup
     */
    public static TextExtractor getStringsExtractor(Content content, Lookup context) {
        StringsTextExtractor stringsInstance = new StringsTextExtractor(content);
        stringsInstance.setExtractionSettings(context);
        return stringsInstance;
    }

    /**
     * System level exception for handling content types that have no specific
     * strategy defined for extracting their text.
     */
    public static class NoTextExtractorFound extends Exception {

        public NoTextExtractorFound(String msg) {
            super(msg);
        }

        public NoTextExtractorFound(Throwable ex) {
            super(ex);
        }

        private NoTextExtractorFound(String msg, Throwable ex) {
            super(msg, ex);
        }
    }
}
