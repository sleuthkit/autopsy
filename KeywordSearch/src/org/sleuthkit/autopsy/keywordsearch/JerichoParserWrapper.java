/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.htmlparser.jericho.Attribute;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.Segment;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;
import net.htmlparser.jericho.StartTagType;
import net.htmlparser.jericho.TextExtractor;

/**
 * Uses Jericho HTML Parser to create a Reader for output, consisting of
 * the text, comments, tag attributes, and other important information
 * found in the HTML.
 */
public class JerichoParserWrapper {
    private static final Logger logger = Logger.getLogger(JerichoParserWrapper.class.getName());
    private InputStream in;    
    private StringBuilder out;
    private Reader reader;
    
    JerichoParserWrapper(InputStream in) {
        this.in = in;
    }
    
    /**
     * Initialize the reader by parsing the InputStream, adding it to StringBuilder,
     * and creating a StringReader from it.
     */
    public void parse() {
        out = new StringBuilder();
        
        try {
            Source source = new Source(in);
            source.fullSequentialParse();
            
            // Look through each segment
            for(Segment segment : source) {
                // Get all the tags to process them
                List<StartTag> tagz = segment.getAllStartTags();
                for(StartTag tag : tagz) {
                    // If it's a comment, add it
                    if(tag.getTagType().equals(StartTagType.COMMENT)) {
                        out.append("COMMENT\t").append(tag.getTagContent()).append("\n");
                    } else {
                        // If it's a tag with an attribute, add it
                        Attributes atts = tag.getAttributes();
                        if (atts!=null && atts.length()>0) {
                            System.out.print(tag.getName().toUpperCase());
                            for(Attribute att : atts) {
                                out.append("\t").append(att.getName()).append(": ");
                                out.append(att.getValue()).append("\n");
                            }
                        }
                    }
                }
                // In the end, add whatever text there is in this segment
                TextExtractor extractor = new TextExtractor(segment);
                if(extractor.toString().length()>0) {
                    out.append(extractor.toString()).append("\n");
                }
            }
            // All done, now make it a reader
            reader = new StringReader(out.toString());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to parse the HTML file", ex);
        }
    }
    
    /**
     * Returns the reader, initialized in parse(), which will be
     * null if parse() is not called or if parse() throws an error.
     * @return Reader
     */
    public Reader getReader() {
        return reader;
    }
    
}
