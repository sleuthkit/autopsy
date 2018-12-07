/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.List;
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
final class HtmlTextExtractor<T extends AbstractFile> implements TextExtractor<T> {

    static final private Logger logger = Logger.getLogger(HtmlTextExtractor.class.getName());
    private final int MAX_SIZE;

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
    public HtmlTextExtractor() {
        //Set default to be 50 MB.
        MAX_SIZE = 50_000_000;
    }

    /**
     * Determines if this content type is supported by this extractor.
     *
     * @param content        Content instance to be analyzed
     * @param detectedFormat Mimetype of content instance
     *
     * @return flag indicating support
     */
    @Override
    public boolean isSupported(AbstractFile content, String detectedFormat) {
        return detectedFormat != null
                && WEB_MIME_TYPES.contains(detectedFormat)
                && content.getSize() <= MAX_SIZE;
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
    public Reader getReader(AbstractFile content) throws InitReaderException {
        //TODO JIRA-4467, there is only harm in excluding HTML documents greater
        //than 50MB due to our troubled approach of extraction.
        ReadContentInputStream stream = new ReadContentInputStream(content);

        //Parse the stream with Jericho and put the results in a Reader
        try {
            StringBuilder scripts = new StringBuilder();
            StringBuilder links = new StringBuilder();
            StringBuilder images = new StringBuilder();
            StringBuilder comments = new StringBuilder();
            StringBuilder others = new StringBuilder();
            int numScripts = 0;
            int numLinks = 0;
            int numImages = 0;
            int numComments = 0;
            int numOthers = 0;

            Source source = new Source(stream);
            source.fullSequentialParse();
            Renderer renderer = source.getRenderer();
            renderer.setNewLine("\n");
            renderer.setIncludeHyperlinkURLs(false);
            renderer.setDecorateFontStyles(false);
            renderer.setIncludeAlternateText(false);

            String text = renderer.toString();
            // Get all the tags in the source
            List<StartTag> tags = source.getAllStartTags();

            StringBuilder stringBuilder = new StringBuilder();
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
            stringBuilder.append(text).append("\n\n");
            stringBuilder.append("----------NONVISIBLE TEXT----------\n\n"); //NON-NLS
            if (numScripts > 0) {
                stringBuilder.append("---Scripts---\n"); //NON-NLS
                stringBuilder.append(scripts).append("\n");
            }
            if (numLinks > 0) {
                stringBuilder.append("---Links---\n"); //NON-NLS
                stringBuilder.append(links).append("\n");
            }
            if (numImages > 0) {
                stringBuilder.append("---Images---\n"); //NON-NLS
                stringBuilder.append(images).append("\n");
            }
            if (numComments > 0) {
                stringBuilder.append("---Comments---\n"); //NON-NLS
                stringBuilder.append(comments).append("\n");
            }
            if (numOthers > 0) {
                stringBuilder.append("---Others---\n"); //NON-NLS
                stringBuilder.append(others).append("\n");
            }
            // All done, now make it a reader
            return new StringReader(stringBuilder.toString());
        } catch (IOException ex) {
            throw new InitReaderException("Error extracting HTML from content.", ex);
        }
    }

    /**
     * Determines how the extraction process will proceed given the settings 
     * stored in this context instance.
     * 
     * As of now, there are no configurable settings for the HtmlTextExtractor.
     * See the extractionconfigs package for available file configurations.
     * 
     * @param context Instance containing config classes
     */
    @Override
    public void setExtractionSettings(ExtractionContext context) {
    }
}
