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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.Renderer;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StartTagType;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Extractor of text from HTML supported AbstractFile content. Extracted text is
 * divided into chunks and indexed with Solr. If HTML extraction succeeds,
 * chunks are indexed with Solr.
 */
class HtmlTextExtractor implements TextExtractor {

    private static final Logger logger = Logger.getLogger(HtmlTextExtractor.class.getName());
    private static final Ingester ingester = Server.getIngester();
    private static final Charset outCharset = Server.DEFAULT_INDEXED_TEXT_CHARSET;
    private static final int MAX_EXTR_TEXT_CHARS = 512 * 1024;
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace    
    private static final int MAX_SIZE = 50000000;

    private final char[] textChunkBuf = new char[MAX_EXTR_TEXT_CHARS];

    private AbstractFile sourceFile;
    private int numChunks = 0;

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
    public boolean setScripts(List<SCRIPT> extractScripts) {
        return false;
    }

    @Override
    public List<SCRIPT> getScripts() {
        return null;
    }

    @Override
    public Map<String, String> getOptions() {
        return null;
    }

    @Override
    public void setOptions(Map<String, String> options) {
    }

    @Override
    public int getNumChunks() {
        return numChunks;
    }

    @Override
    public AbstractFile getSourceFile() {
        return sourceFile;
    }

    @Override
    public boolean index(AbstractFile sourceFile, IngestJobContext context) throws IngesterException {
        this.sourceFile = sourceFile;
        this.numChunks = 0; //unknown until indexing is done

        try (Reader reader = getReader(new ReadContentInputStream(sourceFile));) {

            // In case there is an exception parsing the content
            if (reader == null) {
                logger.log(Level.WARNING, "No reader available from HTML parser"); //NON-NLS
                return false;
            }

            long readSize;
            boolean eof = false;
            //we read max 1024 chars at time, this seems to max what this Reader would return
            while (!eof && (readSize = reader.read(textChunkBuf, 0, SINGLE_READ_CHARS)) != -1) {
                int totalRead = 0;
                if (context.fileIngestIsCancelled()) {
                    ingester.ingest(this); //ingest partialy chunked file?
                    return true;
                }
                totalRead += readSize;

                //consume more bytes to fill entire chunk (leave EXTRA_CHARS to end the word)
                while ((totalRead < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (readSize = reader.read(textChunkBuf, totalRead, SINGLE_READ_CHARS)) != -1) {
                    totalRead += readSize;
                }
                if (readSize == -1) {
                    //this is the last chunk
                    eof = true;
                } else {
                    //try to read until whitespace to not break words
                    while ((totalRead < MAX_EXTR_TEXT_CHARS - 1)
                            && !Character.isWhitespace(textChunkBuf[totalRead - 1])
                            && (readSize = reader.read(textChunkBuf, totalRead, 1)) != -1) {
                        totalRead += readSize;
                    }
                    if (readSize == -1) {
                        //this is the last chunk
                        eof = true;
                    }
                }

                //encode to bytes to index as byte stream
                byte[] encodedBytes = new String(textChunkBuf, 0, totalRead).getBytes(outCharset);
                AbstractFileChunk chunk = new AbstractFileChunk(this, this.numChunks + 1);
                try {
                    chunk.index(ingester, encodedBytes, encodedBytes.length, outCharset);
                    this.numChunks++;
                } catch (Ingester.IngesterException ingEx) {
                    logger.log(Level.WARNING, "Ingester had a problem with extracted HTML from file '" //NON-NLS
                            + sourceFile.getName() + "' (id: " + sourceFile.getId() + ").", ingEx); //NON-NLS
                    throw ingEx; //need to rethrow to signal error and move on
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex); //NON-NLS
            return false;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Unexpected error, can't read content stream from " + sourceFile.getId() + ": " + sourceFile.getName(), ex); //NON-NLS
            return false;
        } finally {
            //after all chunks, ingest the parent file without content itself, and store numChunks
            ingester.ingest(this);
        }
        return true;
    }

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(AbstractFile file, String detectedFormat) {
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
    static Reader getReader(InputStream in) throws IOException {

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
    }
}
