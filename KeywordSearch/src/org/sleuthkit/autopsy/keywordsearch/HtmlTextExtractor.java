/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.Renderer;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StartTagType;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extractor of text from HTML supported AbstractFile content. Extracted text is
 * divided into chunks and indexed with Solr. If HTML extraction succeeds,
 * chunks are indexed with Solr.
 */
class HtmlTextExtractor extends TextExtractor<Void> {

    private static final Logger logger = Logger.getLogger(HtmlTextExtractor.class.getName());

    static final int MAX_EXTR_TEXT_CHARS = 31 * 1024;
    private static final int MAX_SIZE = 50000000;

    static final List<String> WEB_MIME_TYPES = Arrays.asList(
            "application/javascript", //NON-NLS
            "application/xhtml+xml", //NON-NLS
            "application/json", //NON-NLS
            "text/css", //NON-NLS
            "text/html", //NON-NLS NON-NLS
            "text/javascript" //NON-NLS
    );

    HtmlTextExtractor() {
    }

    @Override
    void logWarning(final String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex); //NON-NLS
    }

    @Override
    boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    boolean isSupported(AbstractFile file, String detectedFormat) {
        return detectedFormat != null
                && WEB_MIME_TYPES.contains(detectedFormat)
                && file.getSize() <= MAX_SIZE;
    }

    /** Parse the stream with Jericho and put the results in a Reader
     *
     * @param in an input stream for the content to be parsed by Jericho
     *
     * @return a Reader for the parsed content.
     *
     * @throws IOException if There is an IOException parsing the input stream.
     */
    @Override
    Reader getReader(InputStream in, AbstractFile sourceFile, Void v) throws Ingester.IngesterException {
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

            Source source = new Source(in);
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
            throw new Ingester.IngesterException("Error extracting HTML from content.", ex);
        }
    }

    @Override
    Void newAppendixProvider() {
        return null;
    }

    InputStream getInputStream(AbstractFile sourceFile1) {
        return new ReadContentInputStream(sourceFile1);
    }

    @Override
    boolean noExtractionOptionsAreEnabled() {
        return false;
    }
}
