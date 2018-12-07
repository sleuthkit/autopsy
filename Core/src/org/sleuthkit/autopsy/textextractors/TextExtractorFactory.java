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
public class TextExtractorFactory {
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
     * @return A Reader that contains file source text
     *
     * @throws NoTextExtractorFound In the event that the
     *                                             inputted file and mimetype
     *                                             have no corresponding
     *                                             extractor
     */
    public static TextExtractor<Content> getReader(Content file,
            ExtractionContext context) throws NoTextExtractorFound {
        if (file instanceof AbstractFile) {
            String mimeType = ((AbstractFile) file).getMIMEType();   
            List<TextExtractor<Content>> extractors = Arrays.asList(
                    new HtmlTextExtractor(file),
                    new SqliteTextExtractor(file), 
                    new TikaTextExtractor(file));
            for(TextExtractor<Content> extractor : extractors) {
                if(extractor.isEnabled() && extractor.isSupported(file, mimeType)) {
                    return extractor;
                }
            }
        } else if (file instanceof BlackboardArtifact) {
            TextExtractor<Content> artifactExtractor = new ArtifactTextExtractor((BlackboardArtifact)file);
            artifactExtractor.setExtractionSettings(context);
            return artifactExtractor;
        } else if (file instanceof Report) {
            TextExtractor<Content> reportExtractor = new TikaTextExtractor(file);
            reportExtractor.setExtractionSettings(context);
            return reportExtractor;
        }
        
        throw new NoTextExtractorFound(
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
     * @param source Content source to read from
     * @param context Contains extraction configurations for certain file types
     *
     * @return A DefaultExtractor instance
     */
    public static TextExtractor<Content> getDefaultReader(Content source, ExtractionContext context) {
        StringsTextExtractor stringsInstance = new StringsTextExtractor(source);
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
    }
}
