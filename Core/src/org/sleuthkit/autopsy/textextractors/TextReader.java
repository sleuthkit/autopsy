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

import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Report;

/**
 * Factory for creating text extractors given a source file
 *
 * See ContentTextExtractor interface for the generic structure of such
 * extractors.
 */
public class TextReader {
    
    private final static List<TextExtractor<AbstractFile>> fileExtractors = Arrays.asList(
                new HtmlTextExtractor<>(),
                new SqliteTextExtractor<>(),
                new TikaTextExtractor<>()
        );
    /**
     * Auto detects the correct text extractor given the file.
     *
     * ContentTextExtractor can be configured using the ExtractionContext
     * object. Passing in null or a new unmodified instance of ExtractionContext
     * will keep the extractors at default settings. Refer to the
     * extractionconfigs package for available file configurations.
     *
     * @param file    Content source that will be read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return A ContentTextExtractor instance that is properly configured and
     *         can be read from the getReader() method.
     *
     * @throws NoReaderFoundException In the event that the
     *                                             inputted file and mimetype
     *                                             have no corresponding
     *                                             extractor
     */
    public static Reader getContentSpecificReader(Content file,
            ExtractionContext context) throws NoReaderFoundException {
        try {
            if (file instanceof AbstractFile) {
                String mimeType = ((AbstractFile) file).getMIMEType();
                for (TextExtractor<AbstractFile> candidate : fileExtractors) {
                    candidate.setExtractionSettings(context);
                    if (candidate.isSupported((AbstractFile)file, mimeType)) {
                        return candidate.getReader((AbstractFile)file);
                    }
                }
            } else if (file instanceof BlackboardArtifact) {
                TextExtractor<BlackboardArtifact> artifactExtractor = new ArtifactTextExtractor<>();
                artifactExtractor.setExtractionSettings(context);
                return artifactExtractor.getReader((BlackboardArtifact)file);
            } else if (file instanceof Report) {
                TextExtractor<Report> reportExtractor = new TikaTextExtractor<>();
                reportExtractor.setExtractionSettings(context);
                reportExtractor.getReader((Report)file);
            }
        } catch (TextExtractor.InitReaderException ex) {
            throw new NoReaderFoundException(ex);
        }
        
        throw new NoReaderFoundException(
                String.format("Could not find a suitable extractor for "
                        + "file with name [%s] and id=[%d]. Try using the default, "
                        + "non content specific extractor as an alternative.",
                        file.getName(), file.getId())
        );
    }

    /**
     * Returns the default extractor that can be run on any content type. This
     * extractor should be used as a backup in the event that no specialized
     * extractor can be found.
     *
     * @param source
     * @param context Contains extraction configurations for certain file types
     *
     * @return A DefaultExtractor instance
     */
    public static Reader getDefaultReader(Content source, ExtractionContext context) {
        StringsTextExtractor stringsInstance = new StringsTextExtractor();
        stringsInstance.setExtractionSettings(context);
        return stringsInstance.getReader(source);
    }

    /**
     * System level exception for handling content types that have no specific
     * strategy defined for extracting their text.
     */
    public static class NoReaderFoundException extends Exception {

        public NoReaderFoundException(String msg) {
            super(msg);
        }
        
        public NoReaderFoundException(Throwable ex) {
            super(ex);
        }
    }
}
