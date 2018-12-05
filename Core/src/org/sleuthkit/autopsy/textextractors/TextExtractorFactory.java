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

import com.google.common.collect.ImmutableList;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Factory for creating text extractors given a source file and a mimetype.
 *
 * See TextExtractor.java for the generic structure of such extractors.
 */
public class TextExtractorFactory {

    private static final Logger logger = Logger.getLogger(TextExtractorFactory.class.getName());

    /**
     * The order of these extractors is important. It is a must that more
     * specialized solutions are placed before the TikaTextExtractor to ensure
     * these solutions are chosen over Tika.
     */
    private static final ImmutableList<Class<?>> extractors
            = ImmutableList.of(HtmlTextExtractor.class,
                    SqliteTextExtractor.class,
                    TikaTextExtractor.class);

    /**
     * Auto detects the correct text extractor given the file and mimetype.
     *
     * TextExtractors can be configured using the ExtractionContext object.
     * Passing in null or a new unmodified instance of ExtractionContext will
     * keep the extractors at default settings. Refer to the extractionconfigs
     * package for available file configurations.
     *
     * @param file    Content source that will be read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return A ContentTextExtractor instance that is properly configured and
     *         can be read from the getReader() method.
     *
     * @throws NoContentSpecificExtractorException In the event that the
     *                                             inputted file and mimetype
     *                                             have no corresponding
     *                                             extractor
     */
    public static TextExtractor getContentSpecificExtractor(Content file,
            ExtractionContext context) throws NoContentSpecificExtractorException {
        if (file instanceof AbstractFile) {
            String mimeType = ((AbstractFile) file).getMIMEType();
            for (Class<?> candidate : extractors) {
                try {
                    ContentTextExtractor newInstance = (ContentTextExtractor) candidate.newInstance();
                    newInstance.setExtractionSettings(context);
                    if (newInstance.isSupported(file, mimeType)) {
                        return newInstance;
                    }
                } catch (SecurityException | InstantiationException | IllegalAccessException
                        | IllegalArgumentException ex) {
                    logger.log(Level.SEVERE, String.format("Could not instantiate ContentTextExtractor "
                            + "instance for file %s, objId=%d and mimeType=%s", file.getName(),
                            file.getId(), mimeType), ex);
                }
            }
        } else if (!(file instanceof BlackboardArtifact)) {
            TikaTextExtractor tikaExtractor = new TikaTextExtractor();
            tikaExtractor.setExtractionSettings(context);
            return tikaExtractor;
        }
        /*
         * TODO JIRA-4468 - There should be an additional check for
         * BlackboardArtifact instances. We should be returning the
         * ArtifactTextExtractor rather than throwing an exception.
         */
        throw new NoContentSpecificExtractorException(
                String.format("Could not find a suitable extractor for "
                        + "file with name [%s] and id=[%d]. Use the default, "
                        + "non content specific extractor as an alternative.",
                        file.getName(), file.getId())
        );
    }

    /**
     * Returns the default extractor that can be run on any content type. This
     * extractor should be used as a backup in the event that no specialized
     * extractor can be found.
     *
     * @param context Contains extraction configurations for certain file types
     *
     * @return A StringsTextExtractor instance
     */
    public static TextExtractor getDefaultExtractor(ExtractionContext context) {
        TextExtractor stringsInstance = new StringsTextExtractor();
        stringsInstance.setExtractionSettings(context);
        return stringsInstance;
    }

    /**
     * System level exception for handling content types that have no specific
     * strategy defined for extracting their text.
     */
    public static class NoContentSpecificExtractorException extends Exception {

        public NoContentSpecificExtractorException(String msg) {
            super(msg);
        }
    }
}
