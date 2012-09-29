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
import java.nio.charset.Charset;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractResult;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Wrapper over StringExtract to provide streaming API Given AbstractFile
 * object, extract international strings from the file and read output as a
 * stream of UTF-8 strings as encoded bytes.
 *
 * Currently not-thread safe (reusing static buffers for efficiency)
 */
public class AbstractFileStringIntStream extends InputStream {

    private AbstractFile content;
    private final byte[] oneCharBuf = new byte[1];
    private final StringExtract stringExtractor;
    private static final int FILE_BUF_SIZE = 1024 * 1024;
    private static final byte[] fileReadBuff = new byte[FILE_BUF_SIZE]; //NOTE: need to run all stream extraction in same thread
    private long fileReadOffset = 0L;
    private byte[] convertBuff; //stores extracted string encoded as bytes, before returned to user
    private int convertBuffOffset = 0; //offset to start returning data to user on next read()
    private int bytesInConvertBuff = 0; //amount of data currently in the buffer
    private boolean fileEOF = false; //if file has more bytes to read
    private boolean extractUTF8;
    private boolean extractUTF16;
    private Charset outCharset;
    private static final Logger logger = Logger.getLogger(AbstractFileStringIntStream.class.getName());
    private StringExtractResult lastExtractResult;

    /**
     * Constructs new stream object that does conversion from file, to extracted
     * strings, then to byte stream, for specified script, auto-detected encoding
     * (UTF8, UTF16LE, UTF16BE), and specified output byte stream encoding
     *
     * @param content input content to process and turn into a stream to convert into strings
     * @param scripts a list of scripts to consider
     * @param extractUTF8 whether to extract utf8 encoding
     * @param extractUTF16 whether to extract utf16 encoding
     * @param outCharset encoding to use in the output byte stream
     */
    public AbstractFileStringIntStream(AbstractFile content, List<SCRIPT> scripts, boolean extractUTF8, 
           boolean extractUTF16, Charset outCharset) {
        this.content = content;
        this.stringExtractor = new StringExtract();
        this.stringExtractor.setEnabledScripts(scripts);
        this.extractUTF8 = extractUTF8;
        this.extractUTF16 = extractUTF16;
        this.outCharset = outCharset;
        this.stringExtractor.setEnableUTF8(extractUTF8);
        this.stringExtractor.setEnableUTF16(extractUTF16);
    }

    @Override
    public int read() throws IOException {
        if (extractUTF8 == false && extractUTF16 == false) {
            return -1;
        }
        final int read = read(oneCharBuf, 0, 1);
        if (read == 1) {
            return oneCharBuf[0];
        } else {
            return -1;
        }

    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        
        if (extractUTF8 == false && extractUTF16 == false) {
            return -1;
        }

        long fileSize = content.getSize();
        if (fileSize == 0) {
            return -1;
        }


        //read and convert until user buffer full
        //we have data if file can be read or when byteBuff has converted strings to return
        int bytesToUser = 0; //returned to user so far
        int offsetUser = off;
        while (bytesToUser < len && offsetUser < len) {
            //check if we have enough converted strings         
            int convertBuffRemain = bytesInConvertBuff - convertBuffOffset;

            if ((convertBuff == null || convertBuffRemain == 0) && !fileEOF && fileReadOffset < fileSize) {
                try {
                    //convert more strings, store in buffer
                    long toRead = 0;
                    //int shiftSize = 0;
                    
                    //if (lastExtractResult != null && lastExtractResult.getTextLength() != 0
                      //      && (shiftSize = FILE_BUF_SIZE - lastExtractResult.getFirstUnprocessedOff()) > 0) {
                        ////a string previously extracted
                        ////shift the fileReadBuff past last bytes extracted
                        ////read only what's needed to fill the buffer
                        ////to avoid loosing chars and breaking or corrupting potential strings - preserve byte stream continuity
                        //byte[] temp = new byte[shiftSize];
                        //System.arraycopy(fileReadBuff, lastExtractResult.getFirstUnprocessedOff(),
                        //        temp, 0, shiftSize);
                        //System.arraycopy(temp, 0, fileReadBuff, 0, shiftSize);
                        //toRead = Math.min(lastExtractResult.getFirstUnprocessedOff(), fileSize - fileReadOffset);
                        //lastExtractResult = null;
                    //} else { 
                        //fill up entire fileReadBuff fresh
                        toRead = Math.min(FILE_BUF_SIZE, fileSize - fileReadOffset);
                    //}
                    int read = content.read(fileReadBuff, fileReadOffset, toRead);
                    if (read == -1 || read == 0) {
                        fileEOF = true;
                    } else {
                        fileReadOffset += read;
                        if (fileReadOffset >= fileSize) {
                            fileEOF = true;
                        }

                        //put converted string in convertBuff
                        convert(read);
                        convertBuffRemain = bytesInConvertBuff - convertBuffOffset;
                    }
                } catch (TskCoreException ex) {
                    //Exceptions.printStackTrace(ex);
                    fileEOF = true;
                }
            }

            //nothing more to read, and no more bytes in convertBuff
            if (convertBuff == null || convertBuffRemain == 0) {
                if (fileEOF) {
                    return bytesToUser > 0 ? bytesToUser : -1;
                } else {
                    //no strings extracted, try another read
                    continue;
                }
            }

            //return part or all of convert buff to user
            final int toCopy = Math.min(convertBuffRemain, len - offsetUser);
            System.arraycopy(convertBuff, convertBuffOffset, b, offsetUser, toCopy);
            
            //DEBUG
            /*
            if (toCopy > 0) {
                FileOutputStream debug = new FileOutputStream("c:\\temp\\" + content.getName(), true);
                debug.write(b, offsetUser, toCopy);
                debug.close();
            }
            */
            
            convertBuffOffset += toCopy;
            offsetUser += toCopy;

            bytesToUser += toCopy;
          
        }

        //if more string data in convertBuff, will be consumed on next read()


        return bytesToUser;
    }

    /**
     * convert bytes in file buffer to string, and encode string in
     * convertBuffer
     *
     * @param numBytes num bytes in the fileReadBuff
     */
    private void convert(int numBytes) {
        lastExtractResult = stringExtractor.extract(fileReadBuff, numBytes, 0);
        convertBuff = lastExtractResult.getText().getBytes(outCharset);

        //reset tracking vars
        if (lastExtractResult.getNumBytes() == 0) {
            bytesInConvertBuff = 0;
        } else {
            bytesInConvertBuff = convertBuff.length;
        }
        convertBuffOffset = 0;
    }
}
