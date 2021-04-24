/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.Config;
import net.htmlparser.jericho.LoggerProvider;
import net.htmlparser.jericho.Renderer;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StartTagType;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extracts text from HTML content.
 */
final class HtmlTextExtractor implements TextExtractor {

    static final private Logger logger = Logger.getLogger(HtmlTextExtractor.class.getName());
    private final int MAX_SIZE;
    private final AbstractFile file;

    static final List<String> WEB_MIME_TYPES = Arrays.asList(
            "application/javascript", //NON-NLS
            "application/xhtml+xml", //NON-NLS
            "application/json", //NON-NLS
            "text/css", //NON-NLS
            "text/html", //NON-NLS NON-NLS
            "text/javascript" //NON-NLS
    );

    static {
        // Disable Jericho HTML Parser log messages.
        Config.LoggerProvider = LoggerProvider.DISABLED;
    }

    /**
     * Creates a default instance of the HtmlTextExtractor. Supported file size
     * is 50MB.
     */
    public HtmlTextExtractor(AbstractFile file) {
        //Set default to be 50 MB.
        MAX_SIZE = 50_000_000;
        this.file = file;
    }

    /**
     * Determines if this content type is supported by this extractor.
     *
     * @param content Content instance to be analyzed
     * @param detectedFormat Mimetype of content instance
     *
     * @return flag indicating support
     */
    @Override
    public boolean isSupported() {
        return file.getMIMEType() != null
                && WEB_MIME_TYPES.contains(file.getMIMEType())
                && file.getSize() <= MAX_SIZE;
    }

    /**
     * Get the metadata as a key -> value map. HTML metadata will include
     * scripts, links, images, comments, and misc attributes.
     * 
     * @return Map containing metadata key -> value pairs.
     */
    @Override
    public Map<String, String> getMetadata() {
        Map<String, String> metadataMap = new HashMap<>();
        try {
            ReadContentInputStream stream = new ReadContentInputStream(file);
            StringBuilder scripts = new StringBuilder("\n");
            StringBuilder links = new StringBuilder("\n");
            StringBuilder images = new StringBuilder("\n");
            StringBuilder comments = new StringBuilder("\n");
            StringBuilder others = new StringBuilder("\n");
            int numScripts = 0;
            int numLinks = 0;
            int numImages = 0;
            int numComments = 0;
            int numOthers = 0;

            Source source = new Source(stream);
            source.fullSequentialParse();

            List<StartTag> tags = source.getAllStartTags();
            for (StartTag tag : tags) {
                if (tag.getName().equals("script")) {                //NON-NLS
                    // If the <script> tag has attributes
                    numScripts++;
                    scripts.append(numScripts).append(") ");
                    if (tag.getTagContent().length() > 0) {
                        scripts.append(tag.getTagContent()).append(" ");
                    }
                    // Get whats between the <script> .. </script> tags
                    scripts.append(tag.getElement().getContent()).append("\n");

                } else if (tag.getName().equals("a")) {
                    //NON-NLS
                    numLinks++;
                    links.append(numLinks).append(") ");
                    links.append(tag.getTagContent()).append("\n");

                } else if (tag.getName().equals("img")) {
                    //NON-NLS
                    numImages++;
                    images.append(numImages).append(") ");
                    images.append(tag.getTagContent()).append("\n");

                } else if (tag.getTagType().equals(StartTagType.COMMENT)) {
                    numComments++;
                    comments.append(numComments).append(") ");
                    comments.append(tag.getTagContent()).append("\n");

                } else {
                    // Make sure it has an attribute
                    Attributes atts = tag.getAttributes();
                    if (atts != null && atts.length() > 0) {
                        numOthers++;
                        others.append(numOthers).append(") ");
                        others.append(tag.getName()).append(":");
                        others.append(tag.getTagContent()).append("\n");

                    }
                }
            }

            if (numScripts > 0) {
                metadataMap.put("Scripts", scripts.toString());
            }
            if (numLinks > 0) {
                metadataMap.put("Links", links.toString());
            }
            if (numImages > 0) {
                metadataMap.put("Images", images.toString());
            }
            if (numComments > 0) {
                metadataMap.put("Comments", comments.toString());
            }
            if (numOthers > 0) {
                metadataMap.put("Others", others.toString());
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error extracting HTML metadata from content.", ex);
        }

        return metadataMap;
    }

    /**
     * Returns a reader that will iterate over the text of an HTML document.
     *
     * @param content Html document source
     *
     * @return A reader instance containing the document source text
     *
     * @throws TextExtractorException
     */
    @Override
    public Reader getReader() throws InitReaderException {
        //TODO JIRA-4467, there is only harm in excluding HTML documents greater
        //than 50MB due to our troubled approach of extraction.
        ReadContentInputStream stream = new ReadContentInputStream(file);

        //Parse the stream with Jericho and put the results in a Reader
        try {
            Source source = new Source(stream);
            source.fullSequentialParse();
            Renderer renderer = source.getRenderer();
            renderer.setNewLine("\n");
            renderer.setIncludeHyperlinkURLs(false);
            renderer.setDecorateFontStyles(false);
            renderer.setIncludeAlternateText(false);
            renderer.setMaxLineLength(0); // don't force wrapping
            return new StringReader(renderer.toString());
        } catch (Throwable ex) {
            // JIRA-3436: HtmlTextExtractor someties throws StackOverflowError, which is 
            // not an "Exception" but "Error". The error is occurring in a call to renderer.toString().
            logger.log(Level.WARNING, "Error extracting HTML from content.", ex);
            throw new InitReaderException("Error extracting HTML from content.", ex);
        }
    }
}
