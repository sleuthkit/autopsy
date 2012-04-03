/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.common.util.ContentStream;
import org.sleuthkit.autopsy.datamodel.DataConversion;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

/**
 * Converter from FsContent into String with specific encoding
 * Then, an adapter back to Solr' ContentStream (which is a specific InputStream), 
 * using the same encoding
 */
public class FsContentStringStream implements ContentStream { 
    //supported encoding, encoding string names match java canonical names
    public static enum Encoding {ASCII,};
    
    private static final int MIN_ASCII_CHARS = 4; //minimum consecutive number of ASCII chars to qualify as string
    
    //input
    private FsContent content;
    private Encoding encoding;
    
    //converted
    private String convertedString;
    private InputStream convertedStream;
    private long convertedLength;
    
    private static Logger logger = Logger.getLogger(FsContentStringStream.class.getName());

    public FsContentStringStream(FsContent content, Encoding encoding) {
        this.content = content;
        this.encoding = encoding;
        convertedLength = 0;
    }
    
    public FsContent getFsContent() {
        return content;
    }
    
    
    /**
     * Does all the work and delegation of extracting string and converting 
     * to appropriate stream with the right encoding
     * @throws TskException  if conversion failed for any reason
     */
    public void convert() throws TskException {
        //read entire content and extract strings
        long contentLen = content.getSize();
        
        //TODO this needs to be memory-optimized,
        //convert to string in chunks, without reading entire content
        byte [] data = new byte[(int)contentLen];
        final int bytesRead = content.read(data, 0, contentLen);
        
        convertedString = DataConversion.getString(data, bytesRead, MIN_ASCII_CHARS);
        
        //convert the extracted string back to byte stream with the same encoding
        try {
            byte [] bytes = convertedString.getBytes(encoding.toString());
            convertedLength = bytes.length;
            convertedStream = new ByteArrayInputStream(bytes);
        }
        catch (UnsupportedEncodingException e) {
            logger.log(Level.SEVERE, "Unsupported encoding " + encoding);
            throw new TskException("Unsupported encoding " + encoding);
        }
        
    }
    
    @Override
    public String getContentType() {
        return "text/plain; charset = " + encoding.toString();
    }

    @Override
    public String getName() {
        return content.getName();
    }

    @Override
    public Reader getReader() throws IOException {
        if (convertedStream == null)
            throw new UnsupportedOperationException("Not supported yet.");
        return new InputStreamReader(convertedStream);
        
    }

    @Override
    public Long getSize() {
        return convertedLength;
    }

    @Override
    public String getSourceInfo() {
        return "File:" + content.getId();
    }

    @Override
    public InputStream getStream() throws IOException {
        return convertedStream;
    }
    
}
